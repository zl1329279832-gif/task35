package com.coderman.business.service;

import com.coderman.common.vo.business.InventoryRiskDashboardVO;
import com.coderman.common.vo.business.InventoryRiskSnapshotVO;
import com.coderman.common.vo.system.PageVO;

/**
 * 库存风险服务
 */
public interface InventoryRiskService {

    /**
     * 计算所有物资的风险快照
     */
    void calculateAllRiskSnapshots();

    /**
     * 计算单个物资的风险快照
     */
    InventoryRiskSnapshotVO calculateRiskForProduct(String pNum);

    /**
     * 分页查询最新风险快照
     */
    PageVO<InventoryRiskSnapshotVO> findLatestRisks(Integer pageNum, Integer pageSize, String riskLevel);

    /**
     * 查询风险仪表盘
     */
    InventoryRiskDashboardVO getRiskDashboard();

    /**
     * 查询物资风险历史
     */
    PageVO<InventoryRiskSnapshotVO> findRiskHistory(String pNum, Integer pageNum, Integer pageSize);

    /**
     * 获取物资最新风险快照
     */
    InventoryRiskSnapshotVO getLatestRisk(String pNum);
}
