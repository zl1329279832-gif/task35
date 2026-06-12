package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

/**
 * 补货建议实体
 */
@Data
@Table(name = "biz_replenish_suggestion")
public class ReplenishSuggestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggestion_num")
    private String suggestionNum;

    @Column(name = "p_num")
    private String pNum;

    @Column(name = "suggestion_type")
    private String suggestionType;

    @Column(name = "risk_snapshot_id")
    private Long riskSnapshotId;

    @Column(name = "risk_level")
    private String riskLevel;

    @Column(name = "suggested_quantity")
    private Long suggestedQuantity;

    @Column(name = "from_department")
    private String fromDepartment;

    @Column(name = "to_department")
    private String toDepartment;

    @Column(name = "supplier_id")
    private Long supplierId;

    @Column(name = "estimated_lead_days")
    private Integer estimatedLeadDays;

    @Column(name = "priority_batches")
    private String priorityBatches;

    private String reason;

    private Integer status;

    @Column(name = "adopted_by")
    private String adoptedBy;

    @Column(name = "adopted_time")
    private Date adoptedTime;

    @Column(name = "related_transfer_num")
    private String relatedTransferNum;

    @Column(name = "related_in_num")
    private String relatedInNum;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "modified_time")
    private Date modifiedTime;
}
