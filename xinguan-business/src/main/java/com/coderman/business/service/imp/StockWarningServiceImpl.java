package com.coderman.business.service.imp;

import com.coderman.business.mapper.*;
import com.coderman.business.service.StockWarningService;
import com.coderman.common.enums.buisiness.RiskLevel;
import com.coderman.common.enums.buisiness.SuggestionStatus;
import com.coderman.common.model.business.*;
import com.coderman.common.vo.business.RiskDashboardVO;
import com.coderman.common.vo.business.StockRiskSnapshotVO;
import com.coderman.common.vo.system.PageVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 库存预警服务实现
 * 风险计算引擎：根据批次有效期、在途调拨、锁定库存、历史出库速度计算可用天数和缺口风险
 */
@Service
public class StockWarningServiceImpl implements StockWarningService {

    /** 默认近效期天数 */
    private static final int DEFAULT_NEAR_EXPIRY_DAYS = 30;
    /** 默认最低可用天数 */
    private static final int DEFAULT_MIN_STOCK_DAYS = 14;
    /** 默认安全库存 */
    private static final long DEFAULT_SAFETY_STOCK = 100;
    /** 计算日均出库的历史天数 */
    private static final int OUTBOUND_HISTORY_DAYS = 30;

    @Autowired
    private ProductStockMapper productStockMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private TransferRequestMapper transferRequestMapper;

    @Autowired
    private OutStockMapper outStockMapper;

    @Autowired
    private OutStockInfoMapper outStockInfoMapper;

    @Autowired
    private StockWarningRuleMapper stockWarningRuleMapper;

    @Autowired
    private StockRiskSnapshotMapper stockRiskSnapshotMapper;

    @Autowired
    private ReplenishmentSuggestionMapper replenishmentSuggestionMapper;

    // ==================== 生成风险快照 ====================

    @Override
    @Transactional(readOnly = true)
    public StockRiskSnapshotVO generateRiskSnapshot(String pNum) {
        // 1. 查活跃规则（物资级优先，fallback全局）
        StockWarningRule rule = stockWarningRuleMapper.findActiveRule(pNum);
        if (rule == null) {
            rule = stockWarningRuleMapper.findGlobalRule();
        }

        int nearExpiryDays = rule != null ? rule.getNearExpiryDays() : DEFAULT_NEAR_EXPIRY_DAYS;
        int minStockDays = rule != null ? rule.getMinStockDays() : DEFAULT_MIN_STOCK_DAYS;
        long safetyStock = rule != null ? rule.getSafetyStock() : DEFAULT_SAFETY_STOCK;
        int ruleVersion = rule != null ? rule.getVersion() : 1;

        // 2. 查当前库存
        Example stockExample = new Example(ProductStock.class);
        stockExample.createCriteria().andEqualTo("pNum", pNum);
        ProductStock stock = productStockMapper.selectOneByExample(stockExample);
        long totalStock = stock != null ? stock.getStock() : 0L;

        // 3. 查已锁定库存（所有批次的locked_quantity之和）
        Example batchExample = new Example(ProductBatch.class);
        batchExample.createCriteria()
                .andEqualTo("pNum", pNum)
                .andEqualTo("status", 0);
        List<ProductBatch> batches = productBatchMapper.selectByExample(batchExample);
        long lockedStock = 0L;
        for (ProductBatch batch : batches) {
            lockedStock += (batch.getLockedQuantity() != null ? batch.getLockedQuantity() : 0L);
        }
        long availableStock = totalStock - lockedStock;

        // 4. 查在途调拨（status=3已审批 或 status=4已发送）
        long inTransitStock = calculateInTransitStock(pNum);

        // 5. 查近效期批次
        long nearExpiryStock = calculateNearExpiryStock(pNum, nearExpiryDays);

        // 6. 计算日均出库速度
        BigDecimal avgDailyOutbound = calculateAvgDailyOutbound(pNum);

        // 7. 计算可用天数
        BigDecimal availableDays = BigDecimal.ZERO;
        if (avgDailyOutbound.compareTo(BigDecimal.ZERO) > 0) {
            availableDays = BigDecimal.valueOf(availableStock + inTransitStock)
                    .divide(avgDailyOutbound, 2, RoundingMode.HALF_UP);
        } else if (availableStock + inTransitStock > 0) {
            // 无出库记录但有库存，视为安全
            availableDays = BigDecimal.valueOf(9999);
        }

        // 8. 计算缺口量
        long gapQuantity = Math.max(0, safetyStock - availableStock);

        // 9. 风险等级判定
        int riskLevel = determineRiskLevel(availableDays, gapQuantity, safetyStock,
                nearExpiryStock, minStockDays);

        // 10. 保存快照
        StockRiskSnapshot snapshot = new StockRiskSnapshot();
        snapshot.setSnapshotNum(UUID.randomUUID().toString().replace("-", ""));
        snapshot.setPNum(pNum);
        snapshot.setTotalStock(totalStock);
        snapshot.setAvailableStock(availableStock);
        snapshot.setLockedStock(lockedStock);
        snapshot.setInTransitStock(inTransitStock);
        snapshot.setNearExpiryStock(nearExpiryStock);
        snapshot.setAvgDailyOutbound(avgDailyOutbound);
        snapshot.setAvailableDays(availableDays);
        snapshot.setGapQuantity(gapQuantity);
        snapshot.setRiskLevel(riskLevel);
        snapshot.setRuleVersion(ruleVersion);
        snapshot.setCreateTime(new Date());
        stockRiskSnapshotMapper.insertSelective(snapshot);

        return convertToVO(snapshot);
    }

    // ==================== 批量生成快照 ====================

    @Override
    public List<StockRiskSnapshotVO> generateAllRiskSnapshots() {
        // 查所有有库存的物资
        Example stockExample = new Example(ProductStock.class);
        stockExample.createCriteria().andGreaterThan("stock", 0);
        List<ProductStock> stocks = productStockMapper.selectByExample(stockExample);

        List<StockRiskSnapshotVO> results = new ArrayList<>();
        for (ProductStock stock : stocks) {
            try {
                results.add(generateRiskSnapshot(stock.getPNum()));
            } catch (Exception e) {
                // 单个物资计算失败不影响其他物资
            }
        }
        return results;
    }

    // ==================== 风险重算 ====================

    @Override
    public void recalculateRisk(String pNum) {
        generateRiskSnapshot(pNum);
    }

    // ==================== 风险概览 ====================

    @Override
    public RiskDashboardVO getRiskDashboard() {
        RiskDashboardVO dashboard = new RiskDashboardVO();
        dashboard.setNormalCount(stockRiskSnapshotMapper.countByRiskLevel(RiskLevel.NORMAL));
        dashboard.setLowCount(stockRiskSnapshotMapper.countByRiskLevel(RiskLevel.LOW));
        dashboard.setMediumCount(stockRiskSnapshotMapper.countByRiskLevel(RiskLevel.MEDIUM));
        dashboard.setHighCount(stockRiskSnapshotMapper.countByRiskLevel(RiskLevel.HIGH));
        dashboard.setCriticalCount(stockRiskSnapshotMapper.countByRiskLevel(RiskLevel.CRITICAL));
        dashboard.setTotalCount(dashboard.getNormalCount() + dashboard.getLowCount()
                + dashboard.getMediumCount() + dashboard.getHighCount() + dashboard.getCriticalCount());

        // 统计待处理建议数
        Example suggestionExample = new Example(ReplenishmentSuggestion.class);
        suggestionExample.createCriteria().andEqualTo("status", SuggestionStatus.PENDING);
        dashboard.setPendingSuggestionCount(replenishmentSuggestionMapper.selectCountByExample(suggestionExample));

        return dashboard;
    }

    // ==================== 快照查询 ====================

    @Override
    public PageVO<StockRiskSnapshotVO> findSnapshots(Integer riskLevel, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);

        List<StockRiskSnapshot> snapshots;
        if (riskLevel != null) {
            snapshots = stockRiskSnapshotMapper.findLatestSnapshotsByRiskLevel(riskLevel);
        } else {
            snapshots = stockRiskSnapshotMapper.findAllLatestSnapshots();
        }

        List<StockRiskSnapshotVO> voList = convertToVOList(snapshots);
        PageInfo<StockRiskSnapshot> pageInfo = new PageInfo<>(snapshots);
        return new PageVO<>(pageInfo.getTotal(), voList);
    }

    @Override
    public PageVO<StockRiskSnapshotVO> findSnapshotsByPNum(String pNum, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);

        Example example = new Example(StockRiskSnapshot.class);
        example.createCriteria().andEqualTo("pNum", pNum);
        example.setOrderByClause("create_time DESC");
        List<StockRiskSnapshot> snapshots = stockRiskSnapshotMapper.selectByExample(example);

        List<StockRiskSnapshotVO> voList = convertToVOList(snapshots);
        PageInfo<StockRiskSnapshot> pageInfo = new PageInfo<>(snapshots);
        return new PageVO<>(pageInfo.getTotal(), voList);
    }

    // ==================== 内部计算方法 ====================

    /**
     * 计算在途调拨库存
     * 在途 = 已审批(3) + 已发送(4) 的调拨数量之和
     */
    private long calculateInTransitStock(String pNum) {
        Example example = new Example(TransferRequest.class);
        example.createCriteria()
                .andEqualTo("pNum", pNum)
                .andIn("status", Arrays.asList(3, 4)); // APPROVED, SENT
        List<TransferRequest> transfers = transferRequestMapper.selectByExample(example);

        long inTransit = 0L;
        if (!CollectionUtils.isEmpty(transfers)) {
            for (TransferRequest t : transfers) {
                inTransit += (t.getTransferQuantity() != null ? t.getTransferQuantity() : 0L);
            }
        }
        return inTransit;
    }

    /**
     * 计算近效期库存量
     */
    private long calculateNearExpiryStock(String pNum, int nearExpiryDays) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, nearExpiryDays);
        Date expiryThreshold = cal.getTime();

        Example example = new Example(ProductBatch.class);
        example.createCriteria()
                .andEqualTo("pNum", pNum)
                .andEqualTo("status", 0)
                .andIn("qualityStatus", Arrays.asList(0, 1))
                .andLessThanOrEqualTo("expiryDate", expiryThreshold);
        List<ProductBatch> batches = productBatchMapper.selectByExample(example);

        long nearExpiry = 0L;
        if (!CollectionUtils.isEmpty(batches)) {
            for (ProductBatch batch : batches) {
                long available = batch.getQuantity() - (batch.getLockedQuantity() != null ? batch.getLockedQuantity() : 0L);
                nearExpiry += Math.max(0, available);
            }
        }
        return nearExpiry;
    }

    /**
     * 计算日均出库速度（近30天）
     */
    private BigDecimal calculateAvgDailyOutbound(String pNum) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -OUTBOUND_HISTORY_DAYS);
        Date startDate = cal.getTime();

        // 查近30天已审批(status=0)的出库单
        Example outStockExample = new Example(OutStock.class);
        outStockExample.createCriteria()
                .andEqualTo("status", 0)
                .andGreaterThanOrEqualTo("createTime", startDate);
        List<OutStock> outStocks = outStockMapper.selectByExample(outStockExample);

        if (CollectionUtils.isEmpty(outStocks)) {
            return BigDecimal.ZERO;
        }

        // 收集所有出库单号
        Set<String> outNums = new HashSet<>();
        for (OutStock os : outStocks) {
            outNums.add(os.getOutNum());
        }

        // 查这些出库单中该物资的出库数量
        long totalOutbound = 0L;
        for (String outNum : outNums) {
            Example infoExample = new Example(OutStockInfo.class);
            infoExample.createCriteria()
                    .andEqualTo("outNum", outNum)
                    .andEqualTo("pNum", pNum);
            List<OutStockInfo> infos = outStockInfoMapper.selectByExample(infoExample);
            for (OutStockInfo info : infos) {
                totalOutbound += (info.getProductNumber() != null ? info.getProductNumber() : 0);
            }
        }

        return BigDecimal.valueOf(totalOutbound)
                .divide(BigDecimal.valueOf(OUTBOUND_HISTORY_DAYS), 2, RoundingMode.HALF_UP);
    }

    /**
     * 风险等级判定
     */
    private int determineRiskLevel(BigDecimal availableDays, long gapQuantity, long safetyStock,
                                    long nearExpiryStock, int minStockDays) {
        double days = availableDays.doubleValue();

        // CRITICAL: 可用天数 <= 3 或 缺口 > 2倍安全库存
        if (days <= 3 || gapQuantity > 2 * safetyStock) {
            return RiskLevel.CRITICAL;
        }
        // HIGH: 可用天数 <= 7
        if (days <= 7) {
            return RiskLevel.HIGH;
        }
        // MEDIUM: 可用天数 <= 最低可用天数阈值
        if (days <= minStockDays) {
            return RiskLevel.MEDIUM;
        }
        // LOW: 有近效期批次但可用天数充足
        if (nearExpiryStock > 0) {
            return RiskLevel.LOW;
        }
        // NORMAL: 一切正常
        return RiskLevel.NORMAL;
    }

    // ==================== 转换方法 ====================

    private StockRiskSnapshotVO convertToVO(StockRiskSnapshot snapshot) {
        StockRiskSnapshotVO vo = new StockRiskSnapshotVO();
        BeanUtils.copyProperties(snapshot, vo);
        vo.setRiskLevelDesc(getRiskLevelDesc(snapshot.getRiskLevel()));

        // 关联物资名称
        Example productExample = new Example(Product.class);
        productExample.createCriteria().andEqualTo("pNum", snapshot.getPNum());
        List<Product> products = productMapper.selectByExample(productExample);
        if (!CollectionUtils.isEmpty(products)) {
            vo.setProductName(products.get(0).getName());
        }
        return vo;
    }

    private List<StockRiskSnapshotVO> convertToVOList(List<StockRiskSnapshot> snapshots) {
        List<StockRiskSnapshotVO> voList = new ArrayList<>();
        if (CollectionUtils.isEmpty(snapshots)) {
            return voList;
        }
        for (StockRiskSnapshot snapshot : snapshots) {
            voList.add(convertToVO(snapshot));
        }
        return voList;
    }

    private String getRiskLevelDesc(int riskLevel) {
        switch (riskLevel) {
            case RiskLevel.NORMAL: return "正常";
            case RiskLevel.LOW: return "低风险";
            case RiskLevel.MEDIUM: return "中风险";
            case RiskLevel.HIGH: return "高风险";
            case RiskLevel.CRITICAL: return "紧急";
            default: return "未知";
        }
    }
}
