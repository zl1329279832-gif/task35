package com.coderman.common.vo.business;

import lombok.Data;

import java.util.Date;
import java.util.List;

/**
 * 调拨申请VO
 */
@Data
public class TransferRequestVO {

    private Long id;
    private String transferNum;
    private String pNum;
    private String productName;
    private Long transferQuantity;
    private String fromDepartment;
    private String toDepartment;
    private String reason;
    private Integer emergencyLevel;
    private String operator;
    private Integer status;
    private Date createTime;
    private Date modifiedTime;
    private List<TransferRequestInfoVO> details;
}
