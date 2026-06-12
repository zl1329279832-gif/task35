package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

/**
 * 补货/调拨建议单实体
 */
@Data
@Table(name = "biz_replenishment_suggestion")
public class ReplenishmentSuggestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggestion_num")
    private String suggestionNum;

    @Column(name = "snapshot_id")
    private Long snapshotId;

    @Column(name = "p_num")
    private String pNum;

    @Column(name = "suggestion_type")
    private Integer suggestionType;

    @Column(name = "suggested_quantity")
    private Long suggestedQuantity;

    @Column(name = "source_department")
    private String sourceDepartment;

    @Column(name = "target_department")
    private String targetDepartment;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "expected_delivery_days")
    private Integer expectedDeliveryDays;

    @Column(name = "priority")
    private Integer priority;

    @Column(name = "status")
    private Integer status;

    @Column(name = "rule_version")
    private Integer ruleVersion;

    @Column(name = "idempotent_key")
    private String idempotentKey;

    @Column(name = "operator")
    private String operator;

    @Column(name = "remark")
    private String remark;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "modified_time")
    private Date modifiedTime;
}
