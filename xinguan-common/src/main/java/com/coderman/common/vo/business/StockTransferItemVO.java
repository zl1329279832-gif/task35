package com.coderman.common.vo.business;

import lombok.Data;

/**
 * 调拨单明细VO
 */
@Data
public class StockTransferItemVO {

    private Long id;

    private String transferNum;

    private String pNum;

    private String productName;

    private String batchNum;

    private Integer quantity;

    private String model;

    private String unit;

    private String imageUrl;
}
