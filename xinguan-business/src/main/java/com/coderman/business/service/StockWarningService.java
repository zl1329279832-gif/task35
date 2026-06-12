package com.coderman.business.service;

import com.coderman.common.vo.business.RiskDashboardVO;
import com.coderman.common.vo.business.StockRiskSnapshotVO;
import com.coderman.common.vo.system.PageVO;

import java.util.List;

/**
 * 库存预警服务接口
 */
public interface StockWarningService {

    /**
     * 生成单个物资的风险快照
     * @param pNum 物资编号
     * @return 生成的快照
     */
    StockRiskSnapshotVO generateRiskSnapshot(String pNum);

    /**
     * 生成所有物资的风险快照
     * @return 生成的快照列表
     */
    List<StockRiskSnapshotVO> generateAllRiskSnapshots();

    /**
     * 手工改库存后的风险重算
     * @param pNum 物资编号
     */
    void recalculateRisk(String pNum);

    /**
     * 风险概览
     * @return 风险概览数据
     */
    RiskDashboardVO getRiskDashboard();

    /**
     * 分页查询快照历史
     * @param riskLevel 风险等级（可选）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 快照列表
     */
    PageVO<StockRiskSnapshotVO> findSnapshots(Integer riskLevel, Integer pageNum, Integer pageSize);

    /**
     * 查询某物资的快照历史
     * @param pNum 物资编号
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 快照列表
     */
    PageVO<StockRiskSnapshotVO> findSnapshotsByPNum(String pNum, Integer pageNum, Integer pageSize);
}
