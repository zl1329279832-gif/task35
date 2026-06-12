package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

/**
 * 补货规则版本实体
 */
@Data
@Table(name = "biz_replenish_rule_version")
public class ReplenishRuleVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "version_num")
    private String versionNum;

    @Column(name = "lookback_days")
    private Integer lookbackDays;

    @Column(name = "near_expiry_days")
    private Integer nearExpiryDays;

    @Column(name = "safe_days")
    private Integer safeDays;

    @Column(name = "low_days")
    private Integer lowDays;

    @Column(name = "medium_days")
    private Integer mediumDays;

    @Column(name = "high_days")
    private Integer highDays;

    @Column(name = "safety_stock_days")
    private Integer safetyStockDays;

    @Column(name = "supplier_lead_time_default")
    private Integer supplierLeadTimeDefault;

    @Column(name = "suggestion_dedup_hours")
    private Integer suggestionDedupHours;

    @Column(name = "is_active")
    private Integer isActive;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "modified_time")
    private Date modifiedTime;
}
