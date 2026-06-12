package com.coderman.common.exception;

import lombok.Getter;

/**
 *
 * 业务错误码：返回结果的状态码
 *
 * 如果想要代码更具维护性一点,可以定义不同种类的错误码,都实现 BaseCodeInterface
 * @Author zhangyukang
 * @Date 2020/3/1 14:51
 * @Version 1.0
 **/
@Getter
public enum  ErrorCodeEnum implements BaseCodeInterface {

    // 数据操作错误定义
    BODY_NOT_MATCH(400,"请求的数据格式不符!"),
    SIGNATURE_NOT_MATCH(401,"请求的数字签名不匹配!"),
    NOT_FOUND(404, "未找到该资源!"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误!"),
    SERVER_BUSY(503,"服务器正忙，请稍后再试!"),
    //用户相关：10000**
    USER_ACCOUNT_NOT_FOUND(10001, "账号不存在!"),
    DoNotAllowToDisableTheCurrentUser(10002,"不允许禁用当前用户"),
    //业务异常
    PRODUCT_IS_REMOVE(30001,"物资已移入回收站"),
    PRODUCT_NOT_FOUND(30002,"物资找不到"),
    PRODUCT_WAIT_PASS(30003,"物资等待审核"),
    PRODUCT_STATUS_ERROR(30004,"物资状态错误"),
    PRODUCT_IN_STOCK_NUMBER_ERROR(30005,"物资入库数量非法"),
    PRODUCT_OUT_STOCK_NUMBER_ERROR(30008,"物资发放数量非法"),
    PRODUCT_IN_STOCK_EMPTY(30006,"物资入库不能为空"),
    PRODUCT_OUT_STOCK_EMPTY(30007,"物资发放不能为空"),
    PRODUCT_STOCK_ERROR(30009,"物资库存不足"),

    //批次相关
    BATCH_NOT_FOUND(30010,"批次不存在"),
    BATCH_STOCK_INSUFFICIENT(30011,"批次可用库存不足"),
    BATCH_LOCK_FAILED(30012,"批次库存锁定失败"),
    BATCH_ALLOCATION_FAILED(30013,"批次分配失败"),

    //调拨相关
    TRANSFER_NOT_FOUND(30020,"调拨申请不存在"),
    TRANSFER_STATUS_ERROR(30021,"调拨状态错误"),
    TRANSFER_STOCK_CONFLICT(30022,"库存更新冲突,请重试"),
    TRANSFER_INSUFFICIENT_STOCK(30023,"库存不足,无法调拨"),
    TRANSFER_DUPLICATE_SEND(30024,"调拨出库已确认,请勿重复操作"),
    TRANSFER_DUPLICATE_RECEIVE(30025,"调拨接收已确认,请勿重复操作"),
    TRANSFER_ROLLBACK_FAILED(30026,"调拨回滚失败"),

    //库存锁定相关
    STOCK_LOCK_CONFLICT(30030,"库存锁定冲突,批次已被其他操作锁定"),
    STOCK_UPDATE_CONFLICT(30031,"库存更新冲突,请重试"),
    STOCK_LOCK_RELEASE_FAILED(30032,"库存锁定释放失败"),

    //幂等相关
    IDEMPOTENT_DUPLICATE(30040,"重复操作,该操作已执行过"),

    //预警与建议相关
    WARNING_RULE_NOT_FOUND(30050, "预警规则不存在"),
    SNAPSHOT_NOT_FOUND(30051, "风险快照不存在"),
    SUGGESTION_NOT_FOUND(30052, "补货建议不存在"),
    SUGGESTION_STATUS_ERROR(30053, "建议单状态错误"),
    SUGGESTION_DUPLICATE(30054, "重复的补货建议"),
    SUGGESTION_ALREADY_PROCESSED(30055, "建议单已处理");

    /** 错误码 */
    private int resultCode;

    /** 错误描述 */
    private String resultMsg;

    ErrorCodeEnum(int resultCode, String resultMsg) {
        this.resultCode = resultCode;
        this.resultMsg = resultMsg;
    }

}
