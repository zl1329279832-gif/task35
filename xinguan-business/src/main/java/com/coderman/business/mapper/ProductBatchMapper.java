package com.coderman.business.mapper;

import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.vo.business.ProductBatchVO;
import com.coderman.common.vo.business.TraceVO;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * 物资批次Mapper
 */
public interface ProductBatchMapper extends Mapper<ProductBatch> {

    /**
     * 按先进先出+近效期优先查询可用批次
     */
    List<ProductBatch> findAvailableBatchesFIFO(@Param("pNum") String pNum);

    /**
     * 查询近效期批次(30天内过期)
     */
    List<ProductBatchVO> findNearExpiryBatches(@Param("days") int days);

    /**
     * 批次追溯查询
     */
    TraceVO traceBatch(@Param("batchNum") String batchNum);

    /**
     * 查询物资的所有批次(带供应商和物资名称)
     */
    List<ProductBatchVO> findBatchList(@Param("pNum") String pNum, @Param("batchNum") String batchNum);
}
