package com.coderman.business.mapper;

import com.coderman.common.model.business.InventoryRiskSnapshot;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;
import java.util.Map;

/**
 * 库存风险快照Mapper
 */
public interface InventoryRiskSnapshotMapper extends Mapper<InventoryRiskSnapshot> {

    /**
     * 查询指定物资的最新快照
     */
    InventoryRiskSnapshot findLatestByPNum(@Param("pNum") String pNum);

    /**
     * 查询所有物资的最新快照(每个pNum一条)
     */
    List<InventoryRiskSnapshot> findLatestAll();

    /**
     * 按风险等级查询最新快照
     */
    List<InventoryRiskSnapshot> findByRiskLevel(@Param("riskLevel") String riskLevel);

    /**
     * 按风险等级统计数量(仪表盘)
     */
    List<Map<String, Object>> countByRiskLevel();
}
