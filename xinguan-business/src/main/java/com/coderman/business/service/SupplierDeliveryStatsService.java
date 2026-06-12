package com.coderman.business.service;

import com.coderman.common.vo.business.SupplierDeliveryStatsVO;

import java.util.List;

/**
 * 供应商交付统计服务接口
 */
public interface SupplierDeliveryStatsService {

    /**
     * 入库审批时更新供应商交付统计
     * @param supplierId 供应商ID
     * @param pNum 物资编号
     * @param plannedDeliveryDays 计划交付天数
     * @param actualDeliveryDays 实际交付天数
     */
    void updateStatsOnInStock(Long supplierId, String pNum, int plannedDeliveryDays, int actualDeliveryDays);

    /**
     * 查某供应商的所有交付统计
     * @param supplierId 供应商ID
     * @return 统计列表
     */
    List<SupplierDeliveryStatsVO> getStatsBySupplier(Long supplierId);

    /**
     * 查某物资的各供应商交付对比
     * @param pNum 物资编号
     * @return 统计列表
     */
    List<SupplierDeliveryStatsVO> getStatsByProduct(String pNum);

    /**
     * 查准时率低于阈值的供应商列表
     * @param threshold 准时率阈值（0-1之间的小数）
     * @return 延迟供应商统计列表
     */
    List<SupplierDeliveryStatsVO> getDelayedSuppliers(double threshold);
}
