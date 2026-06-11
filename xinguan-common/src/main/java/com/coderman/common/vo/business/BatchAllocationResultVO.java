package com.coderman.common.vo.business;

import lombok.Data;

import java.util.List;

/**
 * 批次分配结果VO
 */
@Data
public class BatchAllocationResultVO {

    private String outNum;
    private String pNum;
    private Long requestedQuantity;
    private Long allocatedQuantity;
    private boolean fullyAllocated;
    private List<BatchAllocationItemVO> items;
}
