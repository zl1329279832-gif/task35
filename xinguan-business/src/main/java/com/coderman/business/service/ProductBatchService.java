package com.coderman.business.service;

import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.vo.business.BatchAllocationResultVO;
import com.coderman.common.vo.business.ProductBatchVO;
import com.coderman.common.vo.system.PageVO;

import java.util.List;

/**
 * 物资批次服务接口
 */
public interface ProductBatchService {

    /**
     * 创建单个批次记录
     */
    ProductBatch createBatch(ProductBatch batch);

    /**
     * 批量创建批次记录
     */
    void createBatches(List<ProductBatch> batches);

    /**
     * 分页查询批次列表
     */
    PageVO<ProductBatchVO> findBatches(Integer pageNum, Integer pageSize, ProductBatchVO vo);

    /**
     * 批次追溯查询
     */
    ProductBatchVO getTraceability(String batchNumber);

    /**
     * 更新质检状态
     */
    void updateQualityStatus(Long id, Integer qualityStatus);

    /**
     * 查询近效期批次
     */
    List<ProductBatchVO> findNearExpiryBatches(int days);

    /**
     * 智能批次分配
     * @param pNum 物资编号
     * @param quantity 需求数量
     * @param consumerId 领取方ID（可为null）
     * @param strategy 分配策略: FIFO / NEAR_EXPIRY
     * @return 分配结果
     */
    BatchAllocationResultVO allocateBatches(String pNum, Long quantity, Long consumerId, String strategy);

    /**
     * 锁定批次库存
     */
    void lockBatches(List<BatchLockItem> items);

    /**
     * 解锁批次库存
     */
    void unlockBatches(List<BatchLockItem> items);

    /**
     * 确认批次扣减
     */
    void confirmBatchDeductions(List<BatchLockItem> items);

    /**
     * 批次锁定项
     */
    class BatchLockItem {
        private Long batchId;
        private Long quantity;

        public BatchLockItem(Long batchId, Long quantity) {
            this.batchId = batchId;
            this.quantity = quantity;
        }

        public Long getBatchId() { return batchId; }
        public Long getQuantity() { return quantity; }
    }
}
