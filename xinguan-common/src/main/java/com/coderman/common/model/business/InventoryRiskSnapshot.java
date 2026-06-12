package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存风险快照实体
 */
@Data
@Table(name = "biz_inventory_risk_snapshot")
public class InventoryRiskSnapshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "p_num")
    private String pNum;

    @Column(name = "snapshot_time")
    private Date snapshotTime;

    @Column(name = "total_batch_quantity")
    private Long totalBatchQuantity;

    @Column(name = "locked_quantity")
    private Long lockedQuantity;

    @Column(name = "in_transit_quantity")
    private Long inTransitQuantity;

    @Column(name = "available_stock")
    private Long availableStock;

    @Column(name = "daily_consumption_rate")
    private BigDecimal dailyConsumptionRate;

    @Column(name = "lookback_days")
    private Integer lookbackDays;

    @Column(name = "available_days")
    private BigDecimal availableDays;

    @Column(name = "near_expiry_batch_count")
    private Integer nearExpiryBatchCount;

    @Column(name = "near_expiry_quantity")
    private Long nearExpiryQuantity;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "has_expiry_risk")
    private Integer hasExpiryRisk;

    @Column(name = "rule_version_id")
    private Long ruleVersionId;

    @Column(name = "create_time")
    private Date createTime;
}
