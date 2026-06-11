package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

/**
 * 调拨单明细
 */
@Data
@Table(name = "biz_stock_transfer_item")
public class StockTransferItem {

    @Id
    private Long id;

    /** 调拨单号 */
    private String transferNum;

    /** 物资编号 */
    private String pNum;

    /** 批次号 */
    private String batchNum;

    /** 调拨数量 */
    private Integer quantity;

    /** 创建时间 */
    private Date createTime;

    /** 修改时间 */
    private Date modifiedTime;
}
