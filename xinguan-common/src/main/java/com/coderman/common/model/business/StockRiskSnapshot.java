package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 库存风险快照实体
 */
@Data
@Table(name = "biz_stock_risk_snapshot")
public class StockRiskSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "snapshot_num")
    private String snapshotNum;

    @Column(name = "p_num")
    private String pNum;

    @Column(name = "total_stock")
    private Long totalStock;

    @Column(name = "available_stock")
    private Long availableStock;

    @Column(name = "locked_stock")
    private Long lockedStock;

    @Column(name = "in_transit_stock")
    private Long inTransitStock;

    @Column(name = "near_expiry_stock")
    private Long nearExpiryStock;

    @Column(name = "avg_daily_outbound")
    private BigDecimal avgDailyOutbound;

    @Column(name = "available_days")
    private BigDecimal availableDays;

    @Column(name = "gap_quantity")
    private Long gapQuantity;

    @Column(name = "risk_level")
    private Integer riskLevel;

    @Column(name = "rule_version")
    private Integer ruleVersion;

    @Column(name = "create_time")
    private Date createTime;
}
