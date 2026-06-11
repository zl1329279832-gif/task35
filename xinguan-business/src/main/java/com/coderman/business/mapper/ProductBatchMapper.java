package com.coderman.business.mapper;

import com.coderman.common.model.business.ProductBatch;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * 物资批次Mapper
 */
public interface ProductBatchMapper extends Mapper<ProductBatch> {

    /**
     * 查询可用批次（按指定排序）
     */
    List<ProductBatch> findAvailableBatches(
            @Param("pNum") String pNum,
            @Param("orderByClause") String orderByClause);

    /**
     * 查询隔离点可用批次（高储备优先）
     */
    List<ProductBatch> findAvailableBatchesForIsolationPoint(
            @Param("pNum") String pNum);

    /**
     * 原子锁定批次数量
     */
    int lockBatchQuantity(
            @Param("id") Long id,
            @Param("lockAmount") Long lockAmount);

    /**
     * 解锁批次数量
     */
    int unlockBatchQuantity(
            @Param("id") Long id,
            @Param("unlockAmount") Long unlockAmount);

    /**
     * 确认批次扣减（同时减少quantity和locked_quantity）
     */
    int confirmBatchDeduction(
            @Param("id") Long id,
            @Param("deductAmount") Long deductAmount);

    /**
     * 回滚批次扣减（将已扣减的数量加回）
     */
    int rollbackBatchDeduction(
            @Param("id") Long id,
            @Param("rollbackAmount") Long rollbackAmount);

    /**
     * 按批次号追溯查询
     */
    ProductBatch findTraceByBatchNumber(@Param("batchNumber") String batchNumber);

    /**
     * 查询近效期批次
     */
    List<ProductBatch> findNearExpiryBatches(@Param("days") int days);
}
