package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

/**
 * 跨部门调拨单
 */
@Data
@Table(name = "biz_stock_transfer")
public class StockTransfer {

    @Id
    private Long id;

    /** 调拨单号 */
    private String transferNum;

    /** 调出部门ID */
    private Long fromDepartmentId;

    /** 调入部门ID */
    private Long toDepartmentId;

    /** 调拨状态: 0-待审批, 1-已审批, 2-已拒绝, 3-已锁定库存, 4-已出库, 5-已接收, 6-已回滚 */
    private Integer status;

    /** 审批人 */
    private String approver;

    /** 审批时间 */
    private Date approveTime;

    /** 审批意见 */
    private String approveRemark;

    /** 发起人 */
    private String operator;

    /** 调拨原因 */
    private String reason;

    /** 物资总数 */
    private Integer productNumber;

    /** 创建时间 */
    private Date createTime;

    /** 修改时间 */
    private Date modifiedTime;

    /** 备注 */
    private String remark;
}
