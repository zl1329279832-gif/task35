package com.coderman.business.service.imp;

import com.coderman.business.mapper.*;
import com.coderman.business.service.ProductBatchService;
import com.coderman.common.enums.buisiness.BatchTraceEventType;
import com.coderman.common.enums.buisiness.QualityStatus;
import com.coderman.common.exception.ErrorCodeEnum;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.BatchTraceEvent;
import com.coderman.common.model.business.Consumer;
import com.coderman.common.model.business.Product;
import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.model.business.Supplier;
import com.coderman.common.vo.business.BatchAllocationItemVO;
import com.coderman.common.vo.business.BatchAllocationResultVO;
import com.coderman.common.vo.business.BatchTraceEventVO;
import com.coderman.common.vo.business.ProductBatchVO;
import com.coderman.common.vo.system.PageVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * 物资批次服务实现
 * 修正后的批次库存状态机：
 * - 分配时排除已锁定批次(locked_quantity > 0 且可用量不足时跳过)
 * - 锁定/解锁/扣减全程记录追溯事件
 * - 解锁时校验 locked_quantity >= unlockAmount 防止负数
 * - 质检状态变更触发追溯事件
 * - 回滚扣减时将数量加回并记录 ROLLBACK 事件
 */
@Transactional
@Service
public class ProductBatchServiceImpl implements ProductBatchService {

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private ConsumerMapper consumerMapper;

    @Autowired
    private BatchTraceEventMapper batchTraceEventMapper;

    // ==================== 批次创建 ====================

    @Override
    public ProductBatch createBatch(ProductBatch batch) {
        return createBatch(batch, null);
    }

    @Override
    public ProductBatch createBatch(ProductBatch batch, String relatedNum) {
        if (batch.getCreateTime() == null) {
            batch.setCreateTime(new Date());
        }
        if (batch.getModifiedTime() == null) {
            batch.setModifiedTime(new Date());
        }
        if (batch.getLockedQuantity() == null) {
            batch.setLockedQuantity(0L);
        }
        productBatchMapper.insertSelective(batch);

        // 记录入库追溯事件
        recordTraceEvent(batch.getBatchNumber(), batch.getPNum(),
                BatchTraceEventType.IN_STOCK, relatedNum != null ? relatedNum : batch.getInNum(),
                batch.getQuantity(),
                0L, 0L,
                batch.getQuantity(), 0L,
                null, "入库创建");

        return batch;
    }

    @Override
    public void createBatches(List<ProductBatch> batches) {
        for (ProductBatch batch : batches) {
            createBatch(batch);
        }
    }

    // ==================== 批次查询 ====================

    @Override
    public PageVO<ProductBatchVO> findBatches(Integer pageNum, Integer pageSize, ProductBatchVO vo) {
        PageHelper.startPage(pageNum, pageSize);
        Example example = new Example(ProductBatch.class);
        Example.Criteria criteria = example.createCriteria();
        example.setOrderByClause("create_time desc");

        if (vo.getPNum() != null && !"".equals(vo.getPNum())) {
            criteria.andEqualTo("pNum", vo.getPNum());
        }
        if (vo.getQualityStatus() != null) {
            criteria.andEqualTo("qualityStatus", vo.getQualityStatus());
        }
        if (vo.getReserveLevel() != null) {
            criteria.andEqualTo("reserveLevel", vo.getReserveLevel());
        }
        if (vo.getStatus() != null) {
            criteria.andEqualTo("status", vo.getStatus());
        }

        List<ProductBatch> batches = productBatchMapper.selectByExample(example);
        List<ProductBatchVO> voList = convertToVOList(batches);
        PageInfo<ProductBatch> pageInfo = new PageInfo<>(batches);
        return new PageVO<>(pageInfo.getTotal(), voList);
    }

    @Override
    public ProductBatchVO getTraceability(String batchNumber) {
        ProductBatch batch = productBatchMapper.findTraceByBatchNumber(batchNumber);
        if (batch == null) {
            return null;
        }
        List<ProductBatchVO> voList = convertToVOList(Collections.singletonList(batch));
        if (voList.isEmpty()) {
            return null;
        }

        ProductBatchVO vo = voList.get(0);

        // 填充追溯事件链
        List<BatchTraceEvent> events = batchTraceEventMapper.findByBatchNumber(batchNumber);
        if (!CollectionUtils.isEmpty(events)) {
            List<BatchTraceEventVO> eventVOs = new ArrayList<>();
            for (BatchTraceEvent event : events) {
                BatchTraceEventVO eventVO = new BatchTraceEventVO();
                BeanUtils.copyProperties(event, eventVO);
                eventVOs.add(eventVO);
            }
            vo.setTraceEvents(eventVOs);
        }

        return vo;
    }

    // ==================== 质检状态更新 ====================

    @Override
    public void updateQualityStatus(Long id, Integer qualityStatus) {
        ProductBatch batch = productBatchMapper.selectByPrimaryKey(id);
        if (batch == null) {
            throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
        }

        Integer oldQualityStatus = batch.getQualityStatus();
        batch.setQualityStatus(qualityStatus);
        batch.setModifiedTime(new Date());
        productBatchMapper.updateByPrimaryKeySelective(batch);

        // 记录质检追溯事件
        String eventType = (qualityStatus == QualityStatus.QUALIFIED)
                ? BatchTraceEventType.QUALITY_PASS
                : (qualityStatus == QualityStatus.EXPIRED)
                ? BatchTraceEventType.EXPIRED
                : BatchTraceEventType.QUALITY_FAIL;

        recordTraceEvent(batch.getBatchNumber(), batch.getPNum(),
                eventType, null, null,
                batch.getQuantity(), batch.getLockedQuantity(),
                batch.getQuantity(), batch.getLockedQuantity(),
                null, "质检状态: " + oldQualityStatus + " -> " + qualityStatus);
    }

    // ==================== 近效期查询 ====================

    @Override
    public List<ProductBatchVO> findNearExpiryBatches(int days) {
        List<ProductBatch> batches = productBatchMapper.findNearExpiryBatches(days);
        return convertToVOList(batches);
    }

    // ==================== 批次分配 ====================

    @Override
    public BatchAllocationResultVO allocateBatches(String pNum, Long quantity, Long consumerId, String strategy) {
        BatchAllocationResultVO result = new BatchAllocationResultVO();
        result.setPNum(pNum);
        result.setRequestedQuantity(quantity);

        List<ProductBatch> availableBatches;

        // 判断是否为隔离点
        boolean isIsolationPoint = false;
        if (consumerId != null) {
            Consumer consumer = consumerMapper.selectByPrimaryKey(consumerId);
            if (consumer != null && consumer.getIsIsolationPoint() != null
                    && consumer.getIsIsolationPoint() == 1) {
                isIsolationPoint = true;
            }
        }

        if (isIsolationPoint) {
            // 隔离点：高储备优先
            availableBatches = productBatchMapper.findAvailableBatchesForIsolationPoint(pNum);
        } else if ("FIFO".equals(strategy)) {
            // 先进先出
            availableBatches = productBatchMapper.findAvailableBatches(pNum, "production_date ASC");
        } else {
            // 默认：近效期优先
            availableBatches = productBatchMapper.findAvailableBatches(pNum, "expiry_date ASC");
        }

        List<BatchAllocationItemVO> items = new ArrayList<>();
        long remaining = quantity;

        for (ProductBatch batch : availableBatches) {
            if (remaining <= 0) {
                break;
            }
            // 计算实际可用量 = 总量 - 已锁定量
            long available = batch.getQuantity() - batch.getLockedQuantity();
            if (available <= 0) {
                continue;
            }
            long take = Math.min(remaining, available);

            BatchAllocationItemVO item = new BatchAllocationItemVO();
            item.setBatchNumber(batch.getBatchNumber());
            item.setAllocatedQuantity(take);
            item.setProductionDate(batch.getProductionDate());
            item.setExpiryDate(batch.getExpiryDate());
            item.setReserveLevel(batch.getReserveLevel());

            // 查询供应商名称
            if (batch.getSupplierId() != null) {
                Supplier supplier = supplierMapper.selectByPrimaryKey(batch.getSupplierId());
                if (supplier != null) {
                    item.setSupplierName(supplier.getName());
                }
            }

            items.add(item);
            remaining -= take;
        }

        result.setItems(items);
        result.setAllocatedQuantity(quantity - remaining);
        result.setFullyAllocated(remaining <= 0);
        return result;
    }

    // ==================== 锁定 ====================

    @Override
    public void lockBatches(List<BatchLockItem> items) {
        lockBatches(items, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockBatches(List<BatchLockItem> items, String relatedNum) {
        for (BatchLockItem item : items) {
            // 先查询当前状态用于追溯记录
            ProductBatch batch = productBatchMapper.selectByPrimaryKey(item.getBatchId());
            if (batch == null) {
                throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
            }

            Long beforeQty = batch.getQuantity();
            Long beforeLockedQty = batch.getLockedQuantity();

            int rows = productBatchMapper.lockBatchQuantity(item.getBatchId(), item.getQuantity());
            if (rows == 0) {
                throw new ServiceException(ErrorCodeEnum.BATCH_LOCK_FAILED);
            }

            // 记录锁定追溯事件
            recordTraceEvent(batch.getBatchNumber(), batch.getPNum(),
                    BatchTraceEventType.LOCKED, relatedNum,
                    item.getQuantity(),
                    beforeQty, beforeLockedQty,
                    beforeQty, beforeLockedQty + item.getQuantity(),
                    null, "批次锁定");
        }
    }

    // ==================== 解锁 ====================

    @Override
    public void unlockBatches(List<BatchLockItem> items) {
        unlockBatches(items, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockBatches(List<BatchLockItem> items, String relatedNum) {
        for (BatchLockItem item : items) {
            // 先查询当前状态用于追溯记录和安全校验
            ProductBatch batch = productBatchMapper.selectByPrimaryKey(item.getBatchId());
            if (batch == null) {
                throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
            }

            Long beforeQty = batch.getQuantity();
            Long beforeLockedQty = batch.getLockedQuantity();

            // 安全校验：防止解锁量超过已锁定量
            if (beforeLockedQty < item.getQuantity()) {
                throw new ServiceException(ErrorCodeEnum.STOCK_LOCK_RELEASE_FAILED);
            }

            int rows = productBatchMapper.unlockBatchQuantity(item.getBatchId(), item.getQuantity());
            if (rows == 0) {
                throw new ServiceException(ErrorCodeEnum.STOCK_LOCK_RELEASE_FAILED);
            }

            // 记录解锁追溯事件
            recordTraceEvent(batch.getBatchNumber(), batch.getPNum(),
                    BatchTraceEventType.UNLOCKED, relatedNum,
                    item.getQuantity(),
                    beforeQty, beforeLockedQty,
                    beforeQty, beforeLockedQty - item.getQuantity(),
                    null, "锁定释放");
        }
    }

    // ==================== 确认扣减 ====================

    @Override
    public void confirmBatchDeductions(List<BatchLockItem> items) {
        confirmBatchDeductions(items, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmBatchDeductions(List<BatchLockItem> items, String relatedNum) {
        for (BatchLockItem item : items) {
            // 先查询当前状态用于追溯记录
            ProductBatch batch = productBatchMapper.selectByPrimaryKey(item.getBatchId());
            if (batch == null) {
                throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
            }

            Long beforeQty = batch.getQuantity();
            Long beforeLockedQty = batch.getLockedQuantity();

            int rows = productBatchMapper.confirmBatchDeduction(item.getBatchId(), item.getQuantity());
            if (rows == 0) {
                throw new ServiceException(ErrorCodeEnum.BATCH_STOCK_INSUFFICIENT);
            }

            // 记录扣减追溯事件
            recordTraceEvent(batch.getBatchNumber(), batch.getPNum(),
                    BatchTraceEventType.DEDUCTED, relatedNum,
                    item.getQuantity(),
                    beforeQty, beforeLockedQty,
                    beforeQty - item.getQuantity(), beforeLockedQty - item.getQuantity(),
                    null, "确认扣减");
        }
    }

    // ==================== 回滚扣减 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rollbackBatchDeductions(List<BatchLockItem> items, String relatedNum) {
        for (BatchLockItem item : items) {
            ProductBatch batch = productBatchMapper.selectByPrimaryKey(item.getBatchId());
            if (batch == null) {
                throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
            }

            Long beforeQty = batch.getQuantity();
            Long beforeLockedQty = batch.getLockedQuantity();

            // 原子回滚：将已扣减的数量加回
            int rows = productBatchMapper.rollbackBatchDeduction(item.getBatchId(), item.getQuantity());
            if (rows == 0) {
                throw new ServiceException(ErrorCodeEnum.TRANSFER_ROLLBACK_FAILED);
            }

            // 记录回滚追溯事件
            recordTraceEvent(batch.getBatchNumber(), batch.getPNum(),
                    BatchTraceEventType.ROLLBACK, relatedNum,
                    item.getQuantity(),
                    beforeQty, beforeLockedQty,
                    beforeQty + item.getQuantity(), beforeLockedQty,
                    null, "回滚恢复库存");
        }
    }

    // ==================== 追溯事件记录 ====================

    @Override
    public void recordTraceEvent(String batchNumber, String pNum, String eventType,
                                 String relatedNum, Long quantity,
                                 Long beforeQty, Long beforeLockedQty,
                                 Long afterQty, Long afterLockedQty,
                                 String operator, String remark) {
        BatchTraceEvent event = new BatchTraceEvent();
        event.setBatchNumber(batchNumber);
        event.setPNum(pNum);
        event.setEventType(eventType);
        event.setRelatedNum(relatedNum);
        event.setQuantity(quantity);
        event.setBeforeQuantity(beforeQty);
        event.setBeforeLockedQuantity(beforeLockedQty);
        event.setAfterQuantity(afterQty);
        event.setAfterLockedQuantity(afterLockedQty);
        event.setOperator(operator);
        event.setRemark(remark);
        event.setEventTime(new Date());
        batchTraceEventMapper.insertSelective(event);
    }

    // ==================== 内部工具方法 ====================

    /**
     * 实体列表转VO列表（关联物资名称和供应商名称）
     */
    private List<ProductBatchVO> convertToVOList(List<ProductBatch> batches) {
        List<ProductBatchVO> voList = new ArrayList<>();
        if (CollectionUtils.isEmpty(batches)) {
            return voList;
        }
        for (ProductBatch batch : batches) {
            ProductBatchVO vo = new ProductBatchVO();
            BeanUtils.copyProperties(batch, vo);
            vo.setAvailableQuantity(batch.getQuantity() - batch.getLockedQuantity());

            // 关联物资名称
            Example productExample = new Example(Product.class);
            productExample.createCriteria().andEqualTo("pNum", batch.getPNum());
            List<Product> products = productMapper.selectByExample(productExample);
            if (!CollectionUtils.isEmpty(products)) {
                vo.setProductName(products.get(0).getName());
            }

            // 关联供应商名称
            if (batch.getSupplierId() != null) {
                Supplier supplier = supplierMapper.selectByPrimaryKey(batch.getSupplierId());
                if (supplier != null) {
                    vo.setSupplierName(supplier.getName());
                }
            }

            voList.add(vo);
        }
        return voList;
    }
}
