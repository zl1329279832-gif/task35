package com.coderman.business.service.imp;

import com.coderman.business.mapper.*;
import com.coderman.business.service.ProductBatchService;
import com.coderman.business.service.TransferService;
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

    @Override
    public TransferRequestVO createTransferRequest(TransferRequestVO vo) {
        TransferRequest request = new TransferRequest();
        BeanUtils.copyProperties(vo, request);

        String transferNum = UUID.randomUUID().toString().substring(0, 32).replace("-", "");
        request.setTransferNum(transferNum);
        request.setStatus(2); // 待审批
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void approve(Long id) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (request.getStatus() != 2) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        // 批次分配
        BatchAllocationResultVO allocation = productBatchService.allocateBatches(
                request.getPNum(), request.getTransferQuantity(), null, "NEAR_EXPIRY");

        if (!allocation.isFullyAllocated()) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_INSUFFICIENT_STOCK);
        }

        // 查找批次ID并锁定
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

        // 锁定批次库存
        productBatchService.lockBatches(lockItems);

        // 更新状态为已审批
        request.setStatus(3);
        request.setModifiedTime(new Date());
        transferRequestMapper.updateByPrimaryKeySelective(request);
    }

    @Override
    public void reject(Long id) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (request.getStatus() != 2) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }
        request.setStatus(1); // 拒绝
        request.setModifiedTime(new Date());
        transferRequestMapper.updateByPrimaryKeySelective(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmSend(Long id) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (request.getStatus() != 3) {
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

        // 确认批次扣减
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
        productBatchService.confirmBatchDeductions(items);

        // 更新状态为已发送
        request.setStatus(4);
        request.setModifiedTime(new Date());
        transferRequestMapper.updateByPrimaryKeySelective(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmReceive(Long id) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (request.getStatus() != 4) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        // 恢复目标库存
        ProductStock productStock = productStockMapper.findByPNumForUpdate(request.getPNum());
        if (productStock != null) {
            long newStock = productStock.getStock() + request.getTransferQuantity();
            int rows = productStockMapper.updateStockWithVersion(
                    request.getPNum(), newStock, productStock.getVersion());
            if (rows == 0) {
                throw new ServiceException(ErrorCodeEnum.TRANSFER_STOCK_CONFLICT);
            }
        } else {
            ProductStock newStock = new ProductStock();
            newStock.setPNum(request.getPNum());
            newStock.setStock(request.getTransferQuantity());
            newStock.setVersion(0);
            productStockMapper.insertSelective(newStock);
        }

        // 创建目标端新批次记录
        List<TransferRequestInfo> infoList = transferRequestInfoMapper.findByTransferNum(request.getTransferNum());
        if (!CollectionUtils.isEmpty(infoList)) {
            for (TransferRequestInfo info : infoList) {
                // 查找源批次信息
                ProductBatch sourceBatch = productBatchMapper.findTraceByBatchNumber(info.getBatchNumber());
                if (sourceBatch != null) {
                    ProductBatch newBatch = new ProductBatch();
                    newBatch.setBatchNumber("TRANSFER-"
                            + request.getTransferNum().substring(0, Math.min(8, request.getTransferNum().length()))
                            + "-" + info.getBatchNumber());
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
                }
            }
        }

        // 更新状态为完成
        request.setStatus(0);
        request.setModifiedTime(new Date());
        transferRequestMapper.updateByPrimaryKeySelective(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollback(Long id, String reason) {
        TransferRequest request = transferRequestMapper.selectByPrimaryKey(id);
        if (request == null) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_NOT_FOUND);
        }
        if (request.getStatus() != 3 && request.getStatus() != 4) {
            throw new ServiceException(ErrorCodeEnum.TRANSFER_STATUS_ERROR);
        }

        List<TransferRequestInfo> infoList = transferRequestInfoMapper.findByTransferNum(request.getTransferNum());

        if (request.getStatus() == 4) {
            // 已发送状态回滚：恢复库存 + 恢复批次数量
            ProductStock productStock = productStockMapper.findByPNumForUpdate(request.getPNum());
            if (productStock != null) {
                long newStock = productStock.getStock() + request.getTransferQuantity();
                int rows = productStockMapper.updateStockWithVersion(
                        request.getPNum(), newStock, productStock.getVersion());
                if (rows == 0) {
                    throw new ServiceException(ErrorCodeEnum.TRANSFER_STOCK_CONFLICT);
                }
            }

            // 恢复批次数量（将已扣减的数量加回）
            if (!CollectionUtils.isEmpty(infoList)) {
                for (TransferRequestInfo info : infoList) {
                    Example batchExample = new Example(ProductBatch.class);
                    batchExample.createCriteria().andEqualTo("batchNumber", info.getBatchNumber());
                    List<ProductBatch> batches = productBatchMapper.selectByExample(batchExample);
                    if (!CollectionUtils.isEmpty(batches)) {
                        ProductBatch batch = batches.get(0);
                        batch.setQuantity(batch.getQuantity() + info.getAllocatedQuantity());
                        batch.setModifiedTime(new Date());
                        productBatchMapper.updateByPrimaryKeySelective(batch);
                    }
                }
            }
        } else if (request.getStatus() == 3) {
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
                productBatchService.unlockBatches(unlockItems);
            }
        }

        // 更新状态为回滚
        request.setStatus(6);
        request.setReason(request.getReason() != null
                ? request.getReason() + " | 回滚原因: " + reason
                : "回滚原因: " + reason);
        request.setModifiedTime(new Date());
        transferRequestMapper.updateByPrimaryKeySelective(request);
    }

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
