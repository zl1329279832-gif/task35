package com.coderman.common.vo.business;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 补货建议VO
 */
@Data
public class ReplenishSuggestionVO {
    private Long id;
    private String suggestionNum;
    private String pNum;
    private String productName;
    private String suggestionType;
    private Long riskSnapshotId;
    private String riskLevel;
    private Long suggestedQuantity;
    private String fromDepartment;
    private String toDepartment;
    private Long supplierId;
    private String supplierName;
    private Integer estimatedLeadDays;
    private String priorityBatches;
    private String reason;
    private Integer status;
    private String adoptedBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date adoptedTime;

    private String relatedTransferNum;
    private String relatedInNum;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date modifiedTime;

    /** 审计轨迹(详情查询时填充) */
    private List<SuggestionAuditVO> auditTrail;
}
