package com.coderman.business.service;

import com.coderman.common.vo.business.SupplierDeliveryStatVO;

import java.util.List;

/**
 * 供应商交付统计服务
 */
public interface SupplierDeliveryStatService {

    /**
     * 记录一次交付事件
     */
    void recordDelivery(Long supplierId, String pNum, int actualLeadDays, Integer promisedLeadDays);

    /**
     * 查询供应商的交付统计列表
     */
    List<SupplierDeliveryStatVO> findBySupplier(Long supplierId);

    /**
     * 查询特定供应商+物资的交付统计
     */
    SupplierDeliveryStatVO getStatForProduct(Long supplierId, String pNum);

    /**
     * 获取预估交货天数(实际平均值或默认值)
     */
    int getEstimatedLeadDays(Long supplierId, String pNum);
}
