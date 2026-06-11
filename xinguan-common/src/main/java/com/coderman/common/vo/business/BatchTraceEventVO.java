package com.coderman.common.vo.business;

import lombok.Data;

import java.util.Date;

/**
 * 批次追溯事件VO
 */
@Data
public class BatchTraceEventVO {

    private Long id;
    private String batchNumber;
    private String pNum;
    private String eventType;
    private String relatedNum;
    private Long quantity;
    private Long beforeQuantity;
    private Long beforeLockedQuantity;
    private Long afterQuantity;
    private Long afterLockedQuantity;
    private String operator;
    private String remark;
    private Date eventTime;
}
