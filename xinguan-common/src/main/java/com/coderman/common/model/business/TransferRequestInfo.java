package com.coderman.common.model.business;

import lombok.Data;

import javax.persistence.*;

/**
 * 调拨明细实体
 */
@Data
@Table(name = "biz_transfer_request_info")
public class TransferRequestInfo {

    @Id
    private Long id;

    @Column(name = "transfer_num")
    private String transferNum;

    @Column(name = "batch_number")
    private String batchNumber;

    @Column(name = "allocated_quantity")
    private Long allocatedQuantity;

    @Column(name = "confirmed_quantity")
    private Long confirmedQuantity;
}
