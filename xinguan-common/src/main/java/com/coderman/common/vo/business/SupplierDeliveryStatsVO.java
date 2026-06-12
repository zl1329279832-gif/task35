package com.coderman.common.vo.business;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

/**
 * 供应商交付统计VO
 */
@Data
public class SupplierDeliveryStatsVO {

    private Long id;

    private Long supplierId;

    private String supplierName;

    private String pNum;

    private String productName;

    private Integer totalOrders;

    private Integer onTimeOrders;

    private Integer lateOrders;

    private BigDecimal onTimeRate;

    private BigDecimal avgDeliveryDays;

    private BigDecimal avgDelayDays;

    private Date lastDeliveryDate;

    private Date createTime;

    private Date modifiedTime;
}
