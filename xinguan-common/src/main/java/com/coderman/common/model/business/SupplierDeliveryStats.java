package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 供应商交付统计实体
 */
@Data
@Table(name = "biz_supplier_delivery_stats")
public class SupplierDeliveryStats {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "p_num")
    private String pNum;

    @Column(name = "total_orders")
    private Integer totalOrders;

    @Column(name = "on_time_orders")
    private Integer onTimeOrders;

    @Column(name = "late_orders")
    private Integer lateOrders;

    @Column(name = "avg_delivery_days")
    private BigDecimal avgDeliveryDays;

    @Column(name = "avg_delay_days")
    private BigDecimal avgDelayDays;

    @Column(name = "last_delivery_date")
    private Date lastDeliveryDate;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "modified_time")
    private Date modifiedTime;
}
