package com.coderman.business.service;

import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.vo.business.ProductBatchVO;
import com.coderman.common.vo.business.TraceVO;
import com.coderman.common.vo.system.PageVO;

import java.util.List;

/**
 * 物资批次服务
 */
public interface ProductBatchService {

    /**
     * 批次入库(与入库单关联)
     */
    void addBatch(ProductBatch batch);

    /**
     * 更新批次质检状态
     */
    void updateInspectionStatus(Long batchId, Integer status);

    /**
     * 更新批次储备等级
     */
    void updateReserveLevel(Long batchId, Integer level);

    /**
     * 按先进先出+近效期优先分配批次
     * @param pNum 物资编号
     * @param quantity 需要的数量
     * @param isolationPriority 是否隔离点需求优先
     * @return 分配的批次及对应数量
     */
    List<ProductBatch> allocateBatches(String pNum, int quantity, boolean isolationPriority);

    /**
     * 锁定批次库存(调拨用)
     */
    void lockBatchStock(String batchNum, long quantity);

    /**
     * 释放锁定的批次库存(调拨回滚用)
     */
    void unlockBatchStock(String batchNum, long quantity);

    /**
     * 扣减批次库存(出库确认)
     */
    void deductBatchStock(String batchNum, long quantity);

    /**
     * 查询近效期批次
     */
    PageVO<ProductBatchVO> findNearExpiryBatches(Integer pageNum, Integer pageSize, int days);

    /**
     * 批次列表
     */
    PageVO<ProductBatchVO> findBatchList(Integer pageNum, Integer pageSize, String pNum, String batchNum);

    /**
     * 批次追溯
     */
    TraceVO traceBatch(String batchNum);
}
