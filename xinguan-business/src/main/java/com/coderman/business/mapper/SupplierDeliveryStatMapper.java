package com.coderman.business.mapper;

import com.coderman.common.model.business.SupplierDeliveryStat;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

/**
 * 供应商交付统计Mapper
 */
public interface SupplierDeliveryStatMapper extends Mapper<SupplierDeliveryStat> {

    /**
     * 按供应商和物资编号查询统计
     */
    SupplierDeliveryStat findBySupplierAndPNum(
            @Param("supplierId") Long supplierId,
            @Param("pNum") String pNum);

    /**
     * 按供应商查询整体统计(pNum IS NULL)
     */
    SupplierDeliveryStat findBySupplierOverall(@Param("supplierId") Long supplierId);
}
