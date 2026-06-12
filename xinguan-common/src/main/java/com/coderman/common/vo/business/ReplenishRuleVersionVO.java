package com.coderman.common.vo.business;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 补货规则版本VO
 */
@Data
public class ReplenishRuleVersionVO {
    private Long id;
    private String versionNum;
    private Integer lookbackDays;
    private Integer nearExpiryDays;
    private Integer safeDays;
    private Integer lowDays;
    private Integer mediumDays;
    private Integer highDays;
    private Integer safetyStockDays;
    private Integer supplierLeadTimeDefault;
    private Integer suggestionDedupHours;
    private Integer isActive;
    private String createdBy;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date modifiedTime;
}
