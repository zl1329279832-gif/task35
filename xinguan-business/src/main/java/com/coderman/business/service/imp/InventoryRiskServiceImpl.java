package com.coderman.business.service.imp;

import com.coderman.business.mapper.*;
import com.coderman.business.service.InventoryRiskService;
import com.coderman.business.service.ReplenishRuleService;
import com.coderman.common.enums.buisiness.RiskLevel;
import com.coderman.common.exception.ErrorCodeEnum;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.InventoryRiskSnapshot;
import com.coderman.common.model.business.Product;
import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.model.business.ReplenishRuleVersion;
import com.coderman.common.vo.business.*;
import com.coderman.common.vo.system.PageVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 库存风险服务实现
 */
@Service
public class InventoryRiskServiceImpl implements InventoryRiskService {

    @Autowired
    private InventoryRiskSnapshotMapper snapshotMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private OutStockInfoMapper outStockInfoMapper;

    @Autowired
    private TransferRequestMapper transferRequestMapper;

    @Autowired
    private ReplenishRuleVersionMapper ruleVersionMapper;

    @Autowired
    private ProductMapper productMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void calculateAllRiskSnapshots() {
        ReplenishRuleVersion rule = ruleVersionMapper.findActiveRule();
        if (rule == null) {
            throw new ServiceException(ErrorCodeEnum.RISK_RULE_NOT_FOUND);
        }

        // 查询所有正常状态的物资
        Example productExample = new Example(Product.class);
        productExample.createCriteria().andEqualTo("status", 0);
        List<Product> products = productMapper.selectByExample(productExample);

        // 批量查询聚合数据
        Map<String, long[]> batchSumMap = buildBatchSumMap();
        Map<String, Long> inTransitMap = buildInTransitMap();
        Map<String, Long> outboundMap = buildOutboundMap(rule.getLookbackDays());

        Date snapshotTime = new Date();

        for (Product product : products) {
            String pNum = product.getPNum();
            InventoryRiskSnapshot snapshot = computeSnapshot(
                    pNum, rule, snapshotTime, batchSumMap, inTransitMap, outboundMap);
            snapshotMapper.insertSelective(snapshot);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public InventoryRiskSnapshotVO calculateRiskForProduct(String pNum) {
        ReplenishRuleVersion rule = ruleVersionMapper.findActiveRule();
        if (rule == null) {
            throw new ServiceException(ErrorCodeEnum.RISK_RULE_NOT_FOUND);
        }

        Map<String, long[]> batchSumMap = buildBatchSumMap();
        Map<String, Long> inTransitMap = buildInTransitMap();
        Map<String, Long> outboundMap = buildOutboundMap(rule.getLookbackDays());

        InventoryRiskSnapshot snapshot = computeSnapshot(
                pNum, rule, new Date(), batchSumMap, inTransitMap, outboundMap);
        snapshotMapper.insertSelective(snapshot);
        return convertToVO(snapshot);
    }

    @Override
    public PageVO<InventoryRiskSnapshotVO> findLatestRisks(Integer pageNum, Integer pageSize, String riskLevel) {
        PageHelper.startPage(pageNum, pageSize);
        List<InventoryRiskSnapshot> snapshots;
        if (riskLevel != null && !riskLevel.isEmpty()) {
            snapshots = snapshotMapper.findByRiskLevel(riskLevel);
        } else {
            snapshots = snapshotMapper.findLatestAll();
        }
        PageInfo<InventoryRiskSnapshot> pageInfo = new PageInfo<>(snapshots);

        List<InventoryRiskSnapshotVO> voList = enrichSnapshotVOs(snapshots);
        return new PageVO<>(pageInfo.getTotal(), voList);
    }

    @Override
    public InventoryRiskDashboardVO getRiskDashboard() {
        InventoryRiskDashboardVO dashboard = new InventoryRiskDashboardVO();

        List<Map<String, Object>> counts = snapshotMapper.countByRiskLevel();
        int total = 0;
        int criticalCount = 0, highCount = 0, mediumCount = 0, lowCount = 0, safeCount = 0;

        for (Map<String, Object> row : counts) {
            String level = (String) row.get("riskLevel");
            int cnt = ((Number) row.get("cnt")).intValue();
            total += cnt;
            switch (level) {
                case RiskLevel.CRITICAL: criticalCount = cnt; break;
                case RiskLevel.HIGH: highCount = cnt; break;
                case RiskLevel.MEDIUM: mediumCount = cnt; break;
                case RiskLevel.LOW: lowCount = cnt; break;
                case RiskLevel.SAFE: safeCount = cnt; break;
            }
        }

        dashboard.setTotalProducts(total);
        dashboard.setCriticalCount(criticalCount);
        dashboard.setHighCount(highCount);
        dashboard.setMediumCount(mediumCount);
        dashboard.setLowCount(lowCount);
        dashboard.setSafeCount(safeCount);

        // 统计有过期风险的数量
        List<InventoryRiskSnapshot> allLatest = snapshotMapper.findLatestAll();
        int expiryCount = 0;
        for (InventoryRiskSnapshot s : allLatest) {
            if (s.getHasExpiryRisk() != null && s.getHasExpiryRisk() == 1) {
                expiryCount++;
            }
        }
        dashboard.setExpiryRiskCount(expiryCount);

        // 待处理建议数量通过controller层注入
        dashboard.setPendingSuggestionCount(0);

        // 返回非SAFE的风险项
        List<InventoryRiskSnapshotVO> riskItems = new ArrayList<>();
        for (InventoryRiskSnapshot s : allLatest) {
            if (!RiskLevel.SAFE.equals(s.getRiskLevel())) {
                riskItems.add(convertToVO(s));
            }
        }
        dashboard.setRiskItems(riskItems);

        return dashboard;
    }

    @Override
    public PageVO<InventoryRiskSnapshotVO> findRiskHistory(String pNum, Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);
        Example example = new Example(InventoryRiskSnapshot.class);
        example.createCriteria().andEqualTo("pNum", pNum);
        example.setOrderByClause("snapshot_time desc");
        List<InventoryRiskSnapshot> snapshots = snapshotMapper.selectByExample(example);
        PageInfo<InventoryRiskSnapshot> pageInfo = new PageInfo<>(snapshots);

        List<InventoryRiskSnapshotVO> voList = new ArrayList<>();
        for (InventoryRiskSnapshot s : snapshots) {
            voList.add(convertToVO(s));
        }
        return new PageVO<>(pageInfo.getTotal(), voList);
    }

    @Override
    public InventoryRiskSnapshotVO getLatestRisk(String pNum) {
        InventoryRiskSnapshot snapshot = snapshotMapper.findLatestByPNum(pNum);
        if (snapshot == null) {
            return null;
        }
        InventoryRiskSnapshotVO vo = convertToVO(snapshot);
        // 填充近效期批次详情
        ReplenishRuleVersion rule = ruleVersionMapper.findActiveRule();
        if (rule != null) {
            List<ProductBatch> nearExpiry = productBatchMapper.findNearExpiryBatchesByPNum(pNum, rule.getNearExpiryDays());
            if (nearExpiry != null && !nearExpiry.isEmpty()) {
                List<ProductBatchVO> batchVOs = new ArrayList<>();
                for (ProductBatch b : nearExpiry) {
                    ProductBatchVO bvo = new ProductBatchVO();
                    BeanUtils.copyProperties(b, bvo);
                    bvo.setAvailableQuantity(b.getQuantity() - b.getLockedQuantity());
                    batchVOs.add(bvo);
                }
                vo.setNearExpiryBatches(batchVOs);
            }
        }
        return vo;
    }

    // ===== 内部方法 =====

    private InventoryRiskSnapshot computeSnapshot(String pNum, ReplenishRuleVersion rule, Date snapshotTime,
                                                   Map<String, long[]> batchSumMap,
                                                   Map<String, Long> inTransitMap,
                                                   Map<String, Long> outboundMap) {
        InventoryRiskSnapshot snapshot = new InventoryRiskSnapshot();
        snapshot.setPNum(pNum);
        snapshot.setSnapshotTime(snapshotTime);
        snapshot.setRuleVersionId(rule.getId());
        snapshot.setLookbackDays(rule.getLookbackDays());
        snapshot.setCreateTime(new Date());

        // 批次总量和锁定量
        long[] batchData = batchSumMap.getOrDefault(pNum, new long[]{0, 0});
        long totalBatchQuantity = batchData[0];
        long lockedQuantity = batchData[1];

        // 在途调拨量
        long inTransitQuantity = inTransitMap.getOrDefault(pNum, 0L);

        // 可用库存
        long availableStock = totalBatchQuantity - lockedQuantity - inTransitQuantity;
        if (availableStock < 0) {
            availableStock = 0;
        }

        snapshot.setTotalBatchQuantity(totalBatchQuantity);
        snapshot.setLockedQuantity(lockedQuantity);
        snapshot.setInTransitQuantity(inTransitQuantity);
        snapshot.setAvailableStock(availableStock);

        // 日均消耗率
        long totalOutbound = outboundMap.getOrDefault(pNum, 0L);
        BigDecimal dailyRate = BigDecimal.ZERO;
        if (rule.getLookbackDays() > 0 && totalOutbound > 0) {
            dailyRate = BigDecimal.valueOf(totalOutbound)
                    .divide(BigDecimal.valueOf(rule.getLookbackDays()), 4, RoundingMode.HALF_UP);
        }
        snapshot.setDailyConsumptionRate(dailyRate);

        // 可用天数
        BigDecimal availableDays = null;
        if (dailyRate.compareTo(BigDecimal.ZERO) > 0) {
            availableDays = BigDecimal.valueOf(availableStock)
                    .divide(dailyRate, 2, RoundingMode.HALF_UP);
        }
        snapshot.setAvailableDays(availableDays);

        // 近效期统计
        List<ProductBatch> nearExpiry = productBatchMapper.findNearExpiryBatchesByPNum(pNum, rule.getNearExpiryDays());
        int nearExpiryCount = nearExpiry != null ? nearExpiry.size() : 0;
        long nearExpiryQty = 0;
        if (nearExpiry != null) {
            for (ProductBatch b : nearExpiry) {
                nearExpiryQty += b.getQuantity();
            }
        }
        snapshot.setNearExpiryBatchCount(nearExpiryCount);
        snapshot.setNearExpiryQuantity(nearExpiryQty);
        snapshot.setHasExpiryRisk(nearExpiryCount > 0 ? 1 : 0);

        // 风险等级判定
        String riskLevel = determineRiskLevel(availableDays, dailyRate, rule);
        snapshot.setRiskLevel(riskLevel);

        return snapshot;
    }

    private String determineRiskLevel(BigDecimal availableDays, BigDecimal dailyRate, ReplenishRuleVersion rule) {
        // 无消耗记录,视为安全
        if (dailyRate.compareTo(BigDecimal.ZERO) == 0) {
            return RiskLevel.SAFE;
        }
        if (availableDays == null) {
            return RiskLevel.SAFE;
        }

        double days = availableDays.doubleValue();
        if (days >= rule.getSafeDays()) {
            return RiskLevel.SAFE;
        } else if (days >= rule.getLowDays()) {
            return RiskLevel.LOW;
        } else if (days >= rule.getMediumDays()) {
            return RiskLevel.MEDIUM;
        } else if (days >= rule.getHighDays()) {
            return RiskLevel.HIGH;
        } else {
            return RiskLevel.CRITICAL;
        }
    }

    private Map<String, long[]> buildBatchSumMap() {
        Map<String, long[]> map = new HashMap<>();
        List<Map<String, Object>> rows = productBatchMapper.sumAvailableByPNum();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String pNum = (String) row.get("pNum");
                long totalQty = ((Number) row.get("totalQuantity")).longValue();
                long totalLocked = ((Number) row.get("totalLocked")).longValue();
                map.put(pNum, new long[]{totalQty, totalLocked});
            }
        }
        return map;
    }

    private Map<String, Long> buildInTransitMap() {
        Map<String, Long> map = new HashMap<>();
        List<Map<String, Object>> rows = transferRequestMapper.sumInTransitByPNum();
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String pNum = (String) row.get("pNum");
                long qty = ((Number) row.get("inTransitQuantity")).longValue();
                map.put(pNum, qty);
            }
        }
        return map;
    }

    private Map<String, Long> buildOutboundMap(int lookbackDays) {
        Map<String, Long> map = new HashMap<>();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_MONTH, -lookbackDays);
        Date sinceDate = cal.getTime();

        List<Map<String, Object>> rows = outStockInfoMapper.sumOutboundByPNumSince(sinceDate);
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                String pNum = (String) row.get("pNum");
                long total = ((Number) row.get("totalOutbound")).longValue();
                map.put(pNum, total);
            }
        }
        return map;
    }

    private List<InventoryRiskSnapshotVO> enrichSnapshotVOs(List<InventoryRiskSnapshot> snapshots) {
        // 批量查产品名称
        Set<String> pNums = new HashSet<>();
        for (InventoryRiskSnapshot s : snapshots) {
            pNums.add(s.getPNum());
        }
        Map<String, String> nameMap = new HashMap<>();
        if (!pNums.isEmpty()) {
            Example productExample = new Example(Product.class);
            productExample.createCriteria().andIn("pNum", pNums);
            List<Product> products = productMapper.selectByExample(productExample);
            for (Product p : products) {
                nameMap.put(p.getPNum(), p.getName());
            }
        }

        List<InventoryRiskSnapshotVO> voList = new ArrayList<>();
        for (InventoryRiskSnapshot s : snapshots) {
            InventoryRiskSnapshotVO vo = convertToVO(s);
            vo.setProductName(nameMap.get(s.getPNum()));
            voList.add(vo);
        }
        return voList;
    }

    private InventoryRiskSnapshotVO convertToVO(InventoryRiskSnapshot snapshot) {
        InventoryRiskSnapshotVO vo = new InventoryRiskSnapshotVO();
        BeanUtils.copyProperties(snapshot, vo);
        return vo;
    }
}
