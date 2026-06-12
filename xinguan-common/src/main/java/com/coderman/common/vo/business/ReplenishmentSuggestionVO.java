package com.coderman.common.vo.business;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 补货/调拨建议单VO
 */
@Data
public class ReplenishmentSuggestionVO {

    private Long id;

    private String suggestionNum;

    private Long snapshotId;

    private String pNum;

    private String productName;

    private Integer suggestionType;

    private String suggestionTypeDesc;

    private Long suggestedQuantity;

    private String sourceDepartment;

    private String targetDepartment;

    private Long supplierId;

    private String supplierName;

    private Integer expectedDeliveryDays;

    private Integer priority;

    private String priorityDesc;

    private Integer status;

    private String statusDesc;

    private Integer ruleVersion;

    private String operator;

    private String remark;

    private Date createTime;

    private Date modifiedTime;

    /** 关联的审计记录 */
    private List<SuggestionAuditVO> auditRecords;

    /** 关联的风险快照 */
    private StockRiskSnapshotVO snapshot;
}
