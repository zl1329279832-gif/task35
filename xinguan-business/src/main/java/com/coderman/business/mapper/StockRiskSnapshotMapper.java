package com.coderman.business.mapper;

import com.coderman.common.model.business.StockRiskSnapshot;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * 库存风险快照Mapper
 */
public interface StockRiskSnapshotMapper extends Mapper<StockRiskSnapshot> {

    /**
     * 查找某物资的最新快照
     */
    StockRiskSnapshot findLatestByPNum(@Param("pNum") String pNum);

    /**
     * 按风险等级查询最新快照（每个物资取最新一条）
     */
    List<StockRiskSnapshot> findLatestSnapshotsByRiskLevel(@Param("riskLevel") Integer riskLevel);

    /**
     * 查询所有物资的最新快照
     */
    List<StockRiskSnapshot> findAllLatestSnapshots();

    /**
     * 统计各风险等级的物资数量（基于最新快照）
     */
    int countByRiskLevel(@Param("riskLevel") Integer riskLevel);
}
