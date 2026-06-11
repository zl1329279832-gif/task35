package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;
import java.util.Date;

/**
 * 调拨申请实体
 */
@Data
@Table(name = "biz_transfer_request")
public class TransferRequest {

    @Id
    private Long id;

    @Column(name = "transfer_num")
    private String transferNum;

    @Column(name = "p_num")
    private String pNum;

    @Column(name = "transfer_quantity")
    private Long transferQuantity;

    @Column(name = "from_department")
    private String fromDepartment;

    @Column(name = "to_department")
    private String toDepartment;

    @Column(name = "reason")
    private String reason;

    @Column(name = "emergency_level")
    private Integer emergencyLevel;

    @Column(name = "operator")
    private String operator;

    @Column(name = "status")
    private Integer status;

    @Column(name = "create_time")
    private Date createTime;

    @Column(name = "modified_time")
    private Date modifiedTime;
}
