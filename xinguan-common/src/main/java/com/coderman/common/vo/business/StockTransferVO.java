package com.coderman.common.vo.business;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 调拨单VO
 */
@Data
public class StockTransferVO {

    private Long id;

    private String transferNum;

    @NotNull(message = "调出部门不能为空")
    private Long fromDepartmentId;

    private String fromDepartmentName;

    @NotNull(message = "调入部门不能为空")
    private Long toDepartmentId;

    private String toDepartmentName;

    /** 调拨状态: 0-待审批, 1-已审批, 2-已拒绝, 3-已锁定库存, 4-已出库, 5-已接收, 6-已回滚 */
    private Integer status;

    private String approver;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date approveTime;

    private String approveRemark;

    private String operator;

    @NotBlank(message = "调拨原因不能为空")
    private String reason;

    private Integer productNumber;

    @JsonFormat(timezone = "GMT+8", pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    private String remark;

    /** 调拨物资明细 */
    private List<Object> items = new ArrayList<>();
}
