package com.coderman.common.vo.business;

import lombok.Data;

import java.util.Date;

/**
 * 建议采纳审计VO
 */
@Data
public class SuggestionAuditVO {

    private Long id;

    private Long suggestionId;

    private String action;

    private String operator;

    private String reason;

    private Date createTime;
}
