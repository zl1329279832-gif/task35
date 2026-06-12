package com.coderman.business.service.imp;

import com.coderman.business.mapper.*;
import com.coderman.business.event.StockChangedEvent;
import com.coderman.business.service.ProductBatchService;
import com.coderman.business.service.TransferService;
import com.coderman.common.enums.buisiness.BatchTraceEventType;
import com.coderman.common.enums.buisiness.TransferStatus;
import com.coderman.common.exception.ErrorCodeEnum;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.model.business.ProductStock;
import com.coderman.common.model.business.TransferRequest;
import com.coderman.common.model.business.TransferRequestInfo;
import com.coderman.common.response.ActiveUser;
import com.coderman.common.vo.business.BatchAllocationItemVO;
import com.coderman.common.vo.business.BatchAllocationResultVO;
import com.coderman.common.vo.business.TransferRequestInfoVO;
import com.coderman.common.vo.business.TransferRequestVO;
import com.coderman.common.vo.system.PageVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.apache.shiro.SecurityUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * 调拨服务实现
 * 修正后的完整状态机:
 * CREATE → [2:Pending] → approve → [3:Approved] → confirmSend → [4:Sent] → confirmReceive → [0:Complete]
 *                       ↓ reject                                        ↓ rollback(4)     ↓ rollback(3)
 *                    [1:Rejected]                                    [6:Rolled Back]   [6:Rolled Back]
 *
 * 保证:
 * - 审批拒绝：仅更新状态，无锁定残留（审批通过前不锁定）
 * - 出库失败：事务回滚，批次锁定和库存扣减一并回滚
 * - 接收异常：事务回滚，目标端新批次和库存一并回滚
 * - 重复确认：通过状态前置检查实现幂等
 * - 并发调拨：悲观锁+乐观锁双重保护 + 原子批次锁定
 */
@Transactional
@Service
public class TransferServiceImpl implements TransferService {

    @Autowired
    private TransferRequestMapper transferRequestMapper;

    @Autowired
    private TransferRequestInfoMapper transferRequestInfoMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private ProductStockMapper productStockMapper;

    @Autowired
    private ProductBatchService productBatchService;

    @Autowired
    private BatchTraceEventMapper batchTraceEventMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // ==================== 创建调拨申请 ====================

    @Override
    public TransferRequestVO createTransferRequest(TransferRequestVO vo) {
        TransferRequest request = new TransferRequest();
        BeanUtils.copyProperties(vo, request);

        String transferNum = UUID.randomUUID().toString().substring(0, 32).replace("-", "");
        request.setTransferNum(transferNum);
        request.setStatus(TransferStatus.PENDING); // 待审批
        if (request.getEmergencyLevel() == null) {
            request.setEmergencyLevel(1);
        }
        request.setCreateTime(new Date());
        request.setModifiedTime(new Date());

        try {
            ActiveUser activeUser = (ActiveUser) SecurityUtils.getSubject().getPrincipal();
            if (activeUser != null) {
                request.setOperator(activeUser.getUser().getUsername());
            }
        } catch (Exception e) {
            // 非Web上下文（如测试环境），忽略
        }

        transferRequestMapper.insertSelective(request);

        TransferRequestVO result = new TransferRequestVO();
        BeanUtils.copyProperties(request, result);
        return result;
    }

    // ==================== 审批通过 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (request.getStatus() != TransferStatus.PENDING) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        // 批次分配（读取快照，不持锁）
        BatchAllocationResultVO allocation = productBatchService.allocateBatches(
                request.getPNum(), request.getTransferQuantity(), null, "NEAR_EXPIRY");

        if (!allocation.isFullyAllocated()) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_INSUFFICIENT_STOCK);
        }

        // 查找批次ID，准备锁定
        List<ProductBatchService.BatchLockItem> lockItems = new ArrayList<>();
        for (BatchAllocationItemVO allocItem : allocation.getItems()) {
            Example batchExample = new Example(ProductBatch.class);
            batchExample.createCriteria().andEqualTo("batchNumber", allocItem.getBatchNumber());
            List<ProductBatch> batches = productBatchMapper.selectByExample(batchExample);
            if (!CollectionUtils.isEmpty(batches)) {
                ProductBatch batch = batches.get(0);
                lockItems.add(new ProductBatchService.BatchLockItem(batch.getId(), allocItem.getAllocatedQuantity()));

                // 创建调拨明细
                TransferRequestInfo info = new TransferRequestInfo();
                info.setTransferNum(request.getTransferNum());
                info.setBatchNumber(allocItem.getBatchNumber());
                info.setAllocatedQuantity(allocItem.getAllocatedQuantity());
                info.setConfirmedQuantity(0L);
                transferRequestInfoMapper.insertSelective(info);
            }
        }

        // 原子锁定批次库存（如果任一锁定失败，事务整体回滚，不留残留）
        try {
            productBatchService.lockBatches(lockItems, request.getTransferNum());
        } catch (ServiceException e) {
            // 锁定失败时事务回滚，已创建的 TransferRequestInfo 也会被回滚
            throw new ServiceException(ErrorCodeEnum.STOCK_LOCK_CONFLICT);
        }

        // 锁定成功后才更新状态
        request.setStatus(TransferStatus.APPROVED);
        request.setModifiedTime(new Date());
        transferRequestMapper.updateByPrimaryKeySelective(request);
    }

    // ==================== 审批拒绝 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (request.getStatus() != TransferStatus.PENDING) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }
        // 拒绝时无任何批次锁定，直接更新状态即可
        request.setStatus(TransferStatus.REJECTED);
        request.setModifiedTime(new Date());
        transferRequestMapper.updateByPrimaryKeySelective(request);
    }

    // ==================== 出库确认 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmSend(Long id) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        // 幂等性：如果已经是已发送/已完成/已回滚状态，不重复处理
        if (request.getStatus() == TransferStatus.SENT
                || request.getStatus() == TransferStatus.COMPLETED) {
            return; // 幂等：已确认过，直接返回
        }
        if (request.getStatus() != TransferStatus.APPROVED) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        // 查询调拨明细
        List<TransferRequestInfo> infoList = transferRequestInfoMapper.findByTransferNum(request.getTransferNum());
        if (CollectionUtils.isEmpty(infoList)) {
            throw new ServiceException("调拨明细为空");
        }

        // 悲观锁查询库存 + 乐观锁更新
        ProductStock productStock = productStockMapper.findByPNumForUpdate(request.getPNum());
        if (productStock == null) {
            throw new ServiceException("库存记录不存在");
        }

        long newStock = productStock.getStock() - request.getTransferQuantity();
        if (newStock < 0) {
            throw new ServiceException(ErrorCodeEnum.PRODUCT_STOCK_ERROR);
        }

        int rows = productStockMapper.updateStockWithVersion(
                request.getPNum(), newStock, productStock.getVersion());
        if (rows == 0) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STOCK_CONFLICT);
        }

        // 确认批次扣减（解锁+减量同时进行）
        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        for (TransferRequestInfo info : infoList) {
            Example batchExample = new Example(ProductBatch.class);
            batchExample.createCriteria().andEqualTo("batchNumber", info.getBatchNumber());
            List<ProductBatch> batches = productBatchMapper.selectByExample(batchExample);
            if (!CollectionUtils.isEmpty(batches)) {
                items.add(new ProductBatchService.BatchLockItem(
                        batches.get(0).getId(), info.getAllocatedQuantity()));
            }
            // 更新确认数量
            info.setConfirmedQuantity(info.getAllocatedQuantity());
            transferRequestInfoMapper.updateByPrimaryKeySelective(info);
        }
        productBatchService.confirmBatchDeductions(items, request.getTransferNum());

        // 所有操作成功后才更新状态
        request.setStatus(TransferStatus.SENT);
        request.setModifiedTime(new Date());
        transferRequestMapper.updateByPrimaryKeySelective(request);

        // 发布库存变动事件
        eventPublisher.publishEvent(new StockChangedEvent(request.getPNum()));
    }

    // ==================== 接收确认 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long id) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        // 幂等性：如果已经是完成状态，不重复处理
        if (request.getStatus() == TransferStatus.COMPLETED) {
            return; // 幂等：已接收过，直接返回
        }
        if (request.getStatus() != TransferStatus.SENT) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        // 恢复目标库存（悲观锁+乐观锁）
        ProductStock productStock = productStockMapper.findByPNumForUpdate(request.getPNum());
        if (productStock != null) {
            long newStock = productStock.getStock() + request.getTransferQuantity();
            int rows = productStockMapper.updateStockWithVersion(
                    request.getPNum(), newStock, productStock.getVersion());
            if (rows == 0) {
                throw new ServiceException(ErrorCodeEnum.TRANSFER_STOCK_CONFLICT);
            }
        } else {
            ProductStock newStockRecord = new ProductStock();
            newStockRecord.setPNum(request.getPNum());
            newStockRecord.setStock(request.getTransferQuantity());
            newStockRecord.setVersion(0);
            productStockMapper.insertSelective(newStockRecord);
        }

        // 创建目标端新批次记录（带追溯事件）
        List<TransferRequestInfo> infoList = transferRequestInfoMapper.findByTransferNum(request.getTransferNum());
        if (!CollectionUtils.isEmpty(infoList)) {
            for (TransferRequestInfo info : infoList) {
                // 查找源批次信息
                ProductBatch sourceBatch = productBatchMapper.findTraceByBatchNumber(info.getBatchNumber());
                if (sourceBatch != null) {
                    String newBatchNumber = "TRANSFER-"
                            + request.getTransferNum().substring(0, Math.min(8, request.getTransferNum().length()))
                            + "-" + info.getBatchNumber();

                    ProductBatch newBatch = new ProductBatch();
                    newBatch.setBatchNumber(newBatchNumber);
                    newBatch.setPNum(sourceBatch.getPNum());
                    newBatch.setInNum("TRANSFER-" + request.getTransferNum());
                    newBatch.setSupplierId(sourceBatch.getSupplierId());
                    newBatch.setProductionDate(sourceBatch.getProductionDate());
                    newBatch.setExpiryDate(sourceBatch.getExpiryDate());
                    newBatch.setQualityStatus(sourceBatch.getQualityStatus());
                    newBatch.setReserveLevel(sourceBatch.getReserveLevel());
                    newBatch.setQuantity(info.getAllocatedQuantity());
                    newBatch.setLockedQuantity(0L);
                    newBatch.setStatus(0);
                    newBatch.setCreateTime(new Date());
                    newBatch.setModifiedTime(new Date());
                    productBatchMapper.insertSelective(newBatch);

                    // 记录目标端新批次的追溯事件
                    productBatchService.recordTraceEvent(
                            newBatchNumber, sourceBatch.getPNum(),
                            BatchTraceEventType.TRANSFER_IN,
                            request.getTransferNum(),
                            info.getAllocatedQuantity(),
                            0L, 0L,
                            info.getAllocatedQuantity(), 0L,
                            null, "调拨接收创建新批次,源批次: " + info.getBatchNumber());
                }
            }
        }

        // 更新状态为完成
        request.setStatus(TransferStatus.COMPLETED);
        request.setModifiedTime(new Date());
        transferRequestMapper.updateByPrimaryKeySelective(request);

        // 发布库存变动事件
        eventPublisher.publishEvent(new StockChangedEvent(request.getPNum()));
    }

    // ==================== 异常回滚 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long id, String reason) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        // 幂等：已回滚的不重复处理
        if (request.getStatus() == TransferStatus.ROLLED_BACK) {
            return;
        }
        // 只有已审批(3)和已发送(4)状态可以回滚
        if (request.getStatus() != TransferStatus.APPROVED
                && request.getStatus() != TransferStatus.SENT) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        List<TransferRequestInfo> infoList = transferRequestInfoMapper.findByTransferNum(request.getTransferNum());

        if (request.getStatus() == TransferStatus.SENT) {
            // 已发送状态回滚：恢复总库存 + 恢复批次数量（原子SQL）
            ProductStock productStock = productStockMapper.findByPNumForUpdate(request.getPNum());
            if (productStock != null) {
                long newStock = productStock.getStock() + request.getTransferQuantity();
                int rows = productStockMapper.updateStockWithVersion(
                        request.getPNum(), newStock, productStock.getVersion());
                if (rows == 0) {
                    throw new ServiceException(ErrorCodeEnum.TRANSFER_STOCK_CONFLICT);
                }
            }

            // 使用原子SQL恢复批次数量（替代手动updateByPrimaryKeySelective）
            if (!CollectionUtils.isEmpty(infoList)) {
                List<ProductBatchService.BatchLockItem> rollbackItems = new ArrayList<>();
                for (TransferRequestInfo info : infoList) {
                    Example batchExample = new Example(ProductBatch.class);
                    batchExample.createCriteria().andEqualTo("batchNumber", info.getBatchNumber());
                    List<ProductBatch> batches = productBatchMapper.selectByExample(batchExample);
                    if (!CollectionUtils.isEmpty(batches)) {
                        rollbackItems.add(new ProductBatchService.BatchLockItem(
                                batches.get(0).getId(), info.getAllocatedQuantity()));
                    }
                }
                productBatchService.rollbackBatchDeductions(rollbackItems, request.getTransferNum());
            }

        } else if (request.getStatus() == TransferStatus.APPROVED) {
            // 已审批状态回滚：解锁批次
            if (!CollectionUtils.isEmpty(infoList)) {
                List<ProductBatchService.BatchLockItem> unlockItems = new ArrayList<>();
                for (TransferRequestInfo info : infoList) {
                    Example batchExample = new Example(ProductBatch.class);
                    batchExample.createCriteria().andEqualTo("batchNumber", info.getBatchNumber());
                    List<ProductBatch> batches = productBatchMapper.selectByExample(batchExample);
                    if (!CollectionUtils.isEmpty(batches)) {
                        unlockItems.add(new ProductBatchService.BatchLockItem(
                                batches.get(0).getId(), info.getAllocatedQuantity()));
                    }
                }
                productBatchService.unlockBatches(unlockItems, request.getTransferNum());
            }
        }

        // 更新状态为回滚
        request.setStatus(TransferStatus.ROLLED_BACK);
        request.setReason(request.getReason() != null
                ? request.getReason() + " | 回滚原因: " + reason
                : "回滚原因: " + reason);
        request.setModifiedTime(new Date());
        transferRequestMapper.updateByPrimaryKeySelective(request);

        // 发布库存变动事件
        eventPublisher.publishEvent(new StockChangedEvent(request.getPNum()));
    }

    // ==================== 查询 ====================

    @Override
    public PageVO<TransferRequestVO> findTransferRequests(Integer pageNum, Integer pageSize, TransferRequestVO vo) {
        PageHelper.startPage(pageNum, pageSize);
        Example example = new Example(TransferRequest.class);
        Example.Criteria criteria = example.createCriteria();
        example.setOrderByClause("create_time desc");

        if (vo.getPNum() != null && !"".equals(vo.getPNum())) {
            criteria.andEqualTo("pNum", vo.getPNum());
        }
        if (vo.getStatus() != null) {
            criteria.andEqualTo("status", vo.getStatus());
        }
        if (vo.getEmergencyLevel() != null) {
            criteria.andEqualTo("emergencyLevel", vo.getEmergencyLevel());
        }

        List<TransferRequest> requests = transferRequestMapper.selectByExample(example);
        List<TransferRequestVO> voList = new ArrayList<>();
        for (TransferRequest request : requests) {
            TransferRequestVO transferVO = new TransferRequestVO();
            BeanUtils.copyProperties(request, transferVO);
            voList.add(transferVO);
        }

        PageInfo<TransferRequest> pageInfo = new PageInfo<>(requests);
        return new PageVO<>(pageInfo.getTotal(), voList);
    }

    @Override
    public TransferRequestVO getDetail(Long id) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            return null;
        }

        TransferRequestVO vo = new TransferRequestVO();
        BeanUtils.copyProperties(request, vo);

        // 加载调拨明细
        List<TransferRequestInfo> infoList = transferRequestInfoMapper.findByTransferNum(request.getTransferNum());
        List<TransferRequestInfoVO> detailVOs = new ArrayList<>();
        if (!CollectionUtils.isEmpty(infoList)) {
            for (TransferRequestInfo info : infoList) {
                TransferRequestInfoVO infoVO = new TransferRequestInfoVO();
                BeanUtils.copyProperties(info, infoVO);

                // 关联批次信息
                ProductBatch batch = productBatchMapper.findTraceByBatchNumber(info.getBatchNumber());
                if (batch != null) {
                    infoVO.setProductionDate(batch.getProductionDate());
                    infoVO.setExpiryDate(batch.getExpiryDate());
                    infoVO.setQualityStatus(batch.getQualityStatus());
                }

                detailVOs.add(infoVO);
            }
        }
        vo.setDetails(detailVOs);
        return vo;
    }
}
