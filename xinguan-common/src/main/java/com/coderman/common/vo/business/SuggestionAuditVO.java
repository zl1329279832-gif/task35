package com.coderman.common.vo.business;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 建议审计VO
 */
@Data
public class SuggestionAuditVO {
    private Long id;
    private Long suggestionId;
    private String suggestionNum;
    private String action;
    private Integer beforeStatus;
    private Integer afterStatus;
    private String operator;
    private String remark;
    private String relatedNum;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    private Date eventTime;
}
