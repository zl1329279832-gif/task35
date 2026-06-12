package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

/**
 * 库存预警规则实体
 */
@Data
@Table(name = "biz_stock_warning_rule")
public class StockWarningRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "p_num")
    private String pNum;

    @Column(name = "min_stock_days")
    private Integer minStockDays;

    @Column(name = "near_expiry_days")
    private Integer nearExpiryDays;

    @Column(name = "safety_stock")
    private Long safetyStock;

    @Column(name = "reorder_point")
    private Long reorderPoint;

    @Column(name = "version")
    private Integer version;

    @Column(name = "status")
    private Integer status;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "modified_time")
    private Date modifiedTime;
}
