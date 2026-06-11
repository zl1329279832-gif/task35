package com.coderman.business.mapper;

import com.coderman.common.model.business.ProductBatchTrace;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * 物资批次追溯事件Mapper
 */
public interface ProductBatchTraceMapper extends Mapper<ProductBatchTrace> {

    /**
     * 按批次号查询全链路追溯事件(按时间正序)
     */
    List<ProductBatchTrace> findByBatchNumber(@Param("batchNumber") String batchNumber);
}
