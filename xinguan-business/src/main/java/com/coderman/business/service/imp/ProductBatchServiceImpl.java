package com.coderman.business.service.imp;

import com.coderman.business.mapper.ConsumerMapper;
import com.coderman.business.mapper.ProductBatchMapper;
import com.coderman.business.mapper.ProductBatchTraceMapper;
import com.coderman.business.mapper.ProductMapper;
import com.coderman.business.mapper.SupplierMapper;
import com.coderman.business.service.ProductBatchService;
import com.coderman.common.exception.ErrorCodeEnum;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.Consumer;
import com.coderman.common.model.business.Product;
import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.model.business.ProductBatchTrace;
import com.coderman.common.model.business.Supplier;
import com.coderman.common.vo.business.BatchAllocationItemVO;
import com.coderman.common.vo.business.BatchAllocationResultVO;
import com.coderman.common.vo.business.ProductBatchTraceVO;
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
import java.util.Date;
import java.util.List;

/**
 * 物资批次服务实现
 */
@Transactional
@Service
public class ProductBatchServiceImpl implements ProductBatchService {

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private ProductBatchTraceMapper productBatchTraceMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private ConsumerMapper consumerMapper;

    @Override
    public ProductBatch createBatch(ProductBatch batch) {
        if (batch.getCreateTime() == null) {
            batch.setCreateTime(new Date());
        }
        if (batch.getModifiedTime() == null) {
            batch.setModifiedTime(new Date());
        }
        productBatchMapper.insertSelective(batch);

        // 记录入库追溯事件
        recordTraceEvent(batch.getBatchNumber(), batch.getPNum(), "IN",
                batch.getQuantity(), batch.getInNum(), "批次入库");

        return batch;
    }

    @Override
    public void createBatches(List<ProductBatch> batches) {
        for (ProductBatch batch : batches) {
            createBatch(batch);
        }
    }

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
        List<ProductBatchVO> voList = convertToVOList(java.util.Collections.singletonList(batch));
        return voList.isEmpty() ? null : voList.get(0);
    }

    @Override
    public List<ProductBatchTraceVO> getFullTraceability(String batchNumber) {
        List<ProductBatchTrace> traces = productBatchTraceMapper.findByBatchNumber(batchNumber);
        List<ProductBatchTraceVO> voList = new ArrayList<>();
        if (!CollectionUtils.isEmpty(traces)) {
            for (ProductBatchTrace trace : traces) {
                ProductBatchTraceVO vo = new ProductBatchTraceVO();
                BeanUtils.copyProperties(trace, vo);
                voList.add(vo);
            }
        }
        return voList;
    }

    @Override
    public void updateQualityStatus(Long id, Integer qualityStatus) {
        ProductBatch batch = productBatchMapper.selectByPrimaryKey(id);
        if (batch == null) {
            throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
        }
        batch.setQualityStatus(qualityStatus);
        batch.setModifiedTime(new Date());
        productBatchMapper.updateByPrimaryKeySelective(batch);

        // 记录质检追溯事件
        String statusText;
        switch (qualityStatus) {
            case 1: statusText = "质检通过"; break;
            case 2: statusText = "质检不合格"; break;
            case 3: statusText = "已过期"; break;
            default: statusText = "待质检"; break;
        }
        recordTraceEvent(batch.getBatchNumber(), batch.getPNum(), "QC",
                batch.getQuantity(), null, statusText);
    }

    @Override
    public List<ProductBatchVO> findNearExpiryBatches(int days) {
        List<ProductBatch> batches = productBatchMapper.findNearExpiryBatches(days);
        return convertToVOList(batches);
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void lockBatches(List<BatchLockItem> items) {
        for (BatchLockItem item : items) {
            int rows = productBatchMapper.lockBatchQuantity(item.getBatchId(), item.getQuantity());
            if (rows == 0) {
                throw new ServiceException(ErrorCodeEnum.BATCH_LOCK_FAILED);
            }

            // 记录锁定追溯事件
            ProductBatch batch = productBatchMapper.selectByPrimaryKey(item.getBatchId());
            if (batch != null) {
                recordTraceEvent(batch.getBatchNumber(), batch.getPNum(), "LOCK",
                        item.getQuantity(), null, "批次库存锁定");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlockBatches(List<BatchLockItem> items) {
        for (BatchLockItem item : items) {
            int rows = productBatchMapper.unlockBatchQuantity(item.getBatchId(), item.getQuantity());
            if (rows == 0) {
                throw new ServiceException(ErrorCodeEnum.BATCH_LOCK_FAILED);
            }

            // 记录解锁追溯事件
            ProductBatch batch = productBatchMapper.selectByPrimaryKey(item.getBatchId());
            if (batch != null) {
                recordTraceEvent(batch.getBatchNumber(), batch.getPNum(), "UNLOCK",
                        item.getQuantity(), null, "批次库存解锁");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void confirmBatchDeductions(List<BatchLockItem> items) {
        for (BatchLockItem item : items) {
            int rows = productBatchMapper.confirmBatchDeduction(item.getBatchId(), item.getQuantity());
            if (rows == 0) {
                throw new ServiceException(ErrorCodeEnum.BATCH_STOCK_INSUFFICIENT);
            }

            // 记录出库追溯事件
            ProductBatch batch = productBatchMapper.selectByPrimaryKey(item.getBatchId());
            if (batch != null) {
                recordTraceEvent(batch.getBatchNumber(), batch.getPNum(), "OUT",
                        item.getQuantity(), null, "批次库存扣减确认");
            }
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restoreBatchQuantities(List<BatchLockItem> items) {
        for (BatchLockItem item : items) {
            int rows = productBatchMapper.restoreBatchQuantity(item.getBatchId(), item.getQuantity());
            if (rows == 0) {
                throw new ServiceException(ErrorCodeEnum.BATCH_NOT_FOUND);
            }

            // 记录回滚追溯事件
            ProductBatch batch = productBatchMapper.selectByPrimaryKey(item.getBatchId());
            if (batch != null) {
                recordTraceEvent(batch.getBatchNumber(), batch.getPNum(), "ROLLBACK",
                        item.getQuantity(), null, "批次数量恢复");
            }
        }
    }

    @Override
    public void recordTraceEvent(String batchNumber, String pNum, String eventType,
                                 Long quantity, String refNum, String remark) {
        ProductBatchTrace trace = new ProductBatchTrace();
        trace.setBatchNumber(batchNumber);
        trace.setPNum(pNum);
        trace.setEventType(eventType);
        trace.setQuantity(quantity);
        trace.setRefNum(refNum);
        trace.setRemark(remark);
        trace.setCreateTime(new Date());

        try {
            com.coderman.common.response.ActiveUser activeUser =
                    (com.coderman.common.response.ActiveUser) org.apache.shiro.SecurityUtils.getSubject().getPrincipal();
            if (activeUser != null) {
                trace.setOperator(activeUser.getUser().getUsername());
            }
        } catch (Exception e) {
            // 非Web上下文（如测试环境），忽略
        }

        productBatchTraceMapper.insertSelective(trace);
    }

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
