package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

/**
 * 建议审计实体
 */
@Data
@Table(name = "biz_suggestion_audit")
public class SuggestionAudit {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "suggestion_id")
    private Long suggestionId;

    @Column(name = "suggestion_num")
    private String suggestionNum;

    private String action;

    @Column(name = "before_status")
    private Integer beforeStatus;

    @Column(name = "after_status")
    private Integer afterStatus;

    private String operator;

    private String remark;

    @Column(name = "related_num")
    private String relatedNum;

    @Column(name = "event_time")
    private Date eventTime;
}
