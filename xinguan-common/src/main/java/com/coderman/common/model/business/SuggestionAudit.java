package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

/**
 * 建议采纳审计实体
 */
@Data
@Table(name = "biz_suggestion_audit")
public class SuggestionAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggestion_id")
    private Long suggestionId;

    @Column(name = "action")
    private String action;

    @Column(name = "operator")
    private String operator;

    @Column(name = "reason")
    private String reason;

    @Column(name = "create_time")
    private Date createTime;
}
