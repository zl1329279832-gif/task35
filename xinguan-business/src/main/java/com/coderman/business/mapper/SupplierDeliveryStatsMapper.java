package com.coderman.business.mapper;

import com.coderman.common.model.business.SupplierDeliveryStats;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * 供应商交付统计Mapper
 */
public interface SupplierDeliveryStatsMapper extends Mapper<SupplierDeliveryStats> {

    /**
     * 按供应商和物资查找统计
     */
    SupplierDeliveryStats findBySupplierAndProduct(
            @Param("supplierId") Long supplierId,
            @Param("pNum") String pNum);

    /**
     * UPSERT统计（INSERT ON DUPLICATE KEY UPDATE）
     */
    int upsertStats(SupplierDeliveryStats stats);

    /**
     * 查准时率低于阈值的供应商
     */
    List<SupplierDeliveryStats> findDelayedSuppliers(@Param("threshold") double threshold);
}
