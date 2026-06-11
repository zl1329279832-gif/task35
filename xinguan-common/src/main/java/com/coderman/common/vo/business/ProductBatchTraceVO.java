package com.coderman.common.vo.business;

import lombok.Data;

import java.util.Date;

/**
 * 物资批次追溯事件VO
 */
@Data
public class ProductBatchTraceVO {

    private Long id;
    private String batchNumber;
    private String pNum;
    private String eventType;
    private Long quantity;
    private String refNum;
    private String operator;
    private String remark;
    private Date createTime;
}
