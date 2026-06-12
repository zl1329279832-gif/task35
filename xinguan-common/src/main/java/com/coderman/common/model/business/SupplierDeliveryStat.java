package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 供应商交付统计实体
 */
@Data
@Table(name = "biz_supplier_delivery_stat")
public class SupplierDeliveryStat {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "p_num")
    private String pNum;

    @Column(name = "total_deliveries")
    private Integer totalDeliveries;

    @Column(name = "on_time_deliveries")
    private Integer onTimeDeliveries;

    @Column(name = "avg_lead_days")
    private BigDecimal avgLeadDays;

    @Column(name = "max_lead_days")
    private Integer maxLeadDays;

    @Column(name = "min_lead_days")
    private Integer minLeadDays;

    @Column(name = "on_time_rate")
    private BigDecimal onTimeRate;

    @Column(name = "last_delivery_time")
    private Date lastDeliveryTime;

    @Column(name = "promised_lead_days")
    private Integer promisedLeadDays;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "modified_time")
    private Date modifiedTime;
}
