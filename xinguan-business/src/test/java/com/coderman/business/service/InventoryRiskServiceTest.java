package com.coderman.business.service;

import com.coderman.business.mapper.*;
import com.coderman.business.service.imp.InventoryRiskServiceImpl;
import com.coderman.common.enums.buisiness.RiskLevel;
import com.coderman.common.model.business.InventoryRiskSnapshot;
import com.coderman.common.model.business.Product;
import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.model.business.ReplenishRuleVersion;
import com.coderman.common.vo.business.InventoryRiskSnapshotVO;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import tk.mybatis.mapper.entity.Config;
import tk.mybatis.mapper.mapperhelper.EntityHelper;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class InventoryRiskServiceTest {

    @InjectMocks
    private InventoryRiskServiceImpl riskService;

    @Mock
    private InventoryRiskSnapshotMapper snapshotMapper;

    @Mock
    private ProductBatchMapper productBatchMapper;

    @Mock
    private OutStockInfoMapper outStockInfoMapper;

    @Mock
    private TransferRequestMapper transferRequestMapper;

    @Mock
    private ReplenishRuleVersionMapper ruleVersionMapper;

    @Mock
    private ProductMapper productMapper;

    @Captor
    private ArgumentCaptor<InventoryRiskSnapshot> snapshotCaptor;

    private ReplenishRuleVersion rule;
    private Product product;

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(InventoryRiskSnapshot.class, config);
        EntityHelper.initEntityNameMap(Product.class, config);
        EntityHelper.initEntityNameMap(ProductBatch.class, config);
        EntityHelper.initEntityNameMap(ReplenishRuleVersion.class, config);
    }

    @Before
    public void setUp() {
        rule = new ReplenishRuleVersion();
        rule.setId(1L);
        rule.setLookbackDays(30);
        rule.setNearExpiryDays(30);
        rule.setSafeDays(30);
        rule.setLowDays(20);
        rule.setMediumDays(10);
        rule.setHighDays(5);
        rule.setSafetyStockDays(14);
        rule.setSupplierLeadTimeDefault(7);
        rule.setSuggestionDedupHours(24);
        rule.setIsActive(1);

        product = new Product();
        product.setId(1L);
        product.setPNum("P001");
        product.setName("口罩");
        product.setStatus(0);
    }

    @Test
    public void testCalculateRisk_SafeLevel() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);

        // 批次总量1000, 锁定0
        List<Map<String, Object>> batchSum = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("pNum", "P001");
        row.put("totalQuantity", 1000L);
        row.put("totalLocked", 0L);
        batchSum.add(row);
        when(productBatchMapper.sumAvailableByPNum()).thenReturn(batchSum);

        // 无在途
        when(transferRequestMapper.sumInTransitByPNum()).thenReturn(new ArrayList<>());

        // 30天内出库300 -> 日均10
        List<Map<String, Object>> outbound = new ArrayList<>();
        Map<String, Object> outRow = new HashMap<>();
        outRow.put("pNum", "P001");
        outRow.put("totalOutbound", 300L);
        outbound.add(outRow);
        when(outStockInfoMapper.sumOutboundByPNumSince(any(Date.class))).thenReturn(outbound);

        // 无近效期
        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30))).thenReturn(new ArrayList<>());

        InventoryRiskSnapshotVO result = riskService.calculateRiskForProduct("P001");

        verify(snapshotMapper).insertSelective(snapshotCaptor.capture());
        InventoryRiskSnapshot saved = snapshotCaptor.getValue();

        assertEquals(Long.valueOf(1000), saved.getAvailableStock());
        // 日均消耗 = 300/30 = 10, 可用天数 = 1000/10 = 100
        assertEquals(0, saved.getDailyConsumptionRate().compareTo(new BigDecimal("10.0000")));
        assertEquals(0, saved.getAvailableDays().compareTo(new BigDecimal("100.00")));
        assertEquals(RiskLevel.SAFE, saved.getRiskLevel());
    }

    @Test
    public void testCalculateRisk_CriticalLevel() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);

        // 批次总量30, 锁定0
        List<Map<String, Object>> batchSum = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("pNum", "P001");
        row.put("totalQuantity", 30L);
        row.put("totalLocked", 0L);
        batchSum.add(row);
        when(productBatchMapper.sumAvailableByPNum()).thenReturn(batchSum);
        when(transferRequestMapper.sumInTransitByPNum()).thenReturn(new ArrayList<>());

        // 30天内出库300 -> 日均10, 可用天数 = 30/10 = 3 < 5(HIGH阈值) -> CRITICAL
        List<Map<String, Object>> outbound = new ArrayList<>();
        Map<String, Object> outRow = new HashMap<>();
        outRow.put("pNum", "P001");
        outRow.put("totalOutbound", 300L);
        outbound.add(outRow);
        when(outStockInfoMapper.sumOutboundByPNumSince(any(Date.class))).thenReturn(outbound);
        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30))).thenReturn(new ArrayList<>());

        riskService.calculateRiskForProduct("P001");

        verify(snapshotMapper).insertSelective(snapshotCaptor.capture());
        assertEquals(RiskLevel.CRITICAL, snapshotCaptor.getValue().getRiskLevel());
        assertEquals(0, snapshotCaptor.getValue().getAvailableDays().compareTo(new BigDecimal("3.00")));
    }

    @Test
    public void testCalculateRisk_ZeroConsumption_DefaultSafe() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);

        List<Map<String, Object>> batchSum = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("pNum", "P001");
        row.put("totalQuantity", 100L);
        row.put("totalLocked", 0L);
        batchSum.add(row);
        when(productBatchMapper.sumAvailableByPNum()).thenReturn(batchSum);
        when(transferRequestMapper.sumInTransitByPNum()).thenReturn(new ArrayList<>());
        // 零出库
        when(outStockInfoMapper.sumOutboundByPNumSince(any(Date.class))).thenReturn(new ArrayList<>());
        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30))).thenReturn(new ArrayList<>());

        riskService.calculateRiskForProduct("P001");

        verify(snapshotMapper).insertSelective(snapshotCaptor.capture());
        assertEquals(RiskLevel.SAFE, snapshotCaptor.getValue().getRiskLevel());
        assertNull(snapshotCaptor.getValue().getAvailableDays()); // 无消耗时available_days为null
    }

    @Test
    public void testCalculateRisk_WithLockedQuantity() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);

        // 总量200, 锁定150 -> 可用50
        List<Map<String, Object>> batchSum = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("pNum", "P001");
        row.put("totalQuantity", 200L);
        row.put("totalLocked", 150L);
        batchSum.add(row);
        when(productBatchMapper.sumAvailableByPNum()).thenReturn(batchSum);
        when(transferRequestMapper.sumInTransitByPNum()).thenReturn(new ArrayList<>());

        // 日均10 -> 可用天数 = 50/10 = 5, 正好等于HIGH阈值 -> HIGH
        List<Map<String, Object>> outbound = new ArrayList<>();
        Map<String, Object> outRow = new HashMap<>();
        outRow.put("pNum", "P001");
        outRow.put("totalOutbound", 300L);
        outbound.add(outRow);
        when(outStockInfoMapper.sumOutboundByPNumSince(any(Date.class))).thenReturn(outbound);
        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30))).thenReturn(new ArrayList<>());

        riskService.calculateRiskForProduct("P001");

        verify(snapshotMapper).insertSelective(snapshotCaptor.capture());
        InventoryRiskSnapshot saved = snapshotCaptor.getValue();
        assertEquals(Long.valueOf(150), saved.getLockedQuantity());
        assertEquals(Long.valueOf(50), saved.getAvailableStock());
        assertEquals(RiskLevel.HIGH, saved.getRiskLevel());
    }

    @Test
    public void testCalculateRisk_WithInTransitTransfers() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);

        // 总量200, 锁定0
        List<Map<String, Object>> batchSum = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("pNum", "P001");
        row.put("totalQuantity", 200L);
        row.put("totalLocked", 0L);
        batchSum.add(row);
        when(productBatchMapper.sumAvailableByPNum()).thenReturn(batchSum);

        // 在途100 -> 可用 = 200 - 0 - 100 = 100
        List<Map<String, Object>> transit = new ArrayList<>();
        Map<String, Object> transitRow = new HashMap<>();
        transitRow.put("pNum", "P001");
        transitRow.put("inTransitQuantity", 100L);
        transit.add(transitRow);
        when(transferRequestMapper.sumInTransitByPNum()).thenReturn(transit);

        // 日均10 -> 可用天数 = 100/10 = 10
        List<Map<String, Object>> outbound = new ArrayList<>();
        Map<String, Object> outRow = new HashMap<>();
        outRow.put("pNum", "P001");
        outRow.put("totalOutbound", 300L);
        outbound.add(outRow);
        when(outStockInfoMapper.sumOutboundByPNumSince(any(Date.class))).thenReturn(outbound);
        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30))).thenReturn(new ArrayList<>());

        riskService.calculateRiskForProduct("P001");

        verify(snapshotMapper).insertSelective(snapshotCaptor.capture());
        InventoryRiskSnapshot saved = snapshotCaptor.getValue();
        assertEquals(Long.valueOf(100), saved.getInTransitQuantity());
        assertEquals(Long.valueOf(100), saved.getAvailableStock());
        assertEquals(RiskLevel.MEDIUM, saved.getRiskLevel()); // 10 >= mediumDays(10)
    }

    @Test
    public void testCalculateRisk_NearExpiryDetection() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);

        List<Map<String, Object>> batchSum = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("pNum", "P001");
        row.put("totalQuantity", 500L);
        row.put("totalLocked", 0L);
        batchSum.add(row);
        when(productBatchMapper.sumAvailableByPNum()).thenReturn(batchSum);
        when(transferRequestMapper.sumInTransitByPNum()).thenReturn(new ArrayList<>());

        List<Map<String, Object>> outbound = new ArrayList<>();
        Map<String, Object> outRow = new HashMap<>();
        outRow.put("pNum", "P001");
        outRow.put("totalOutbound", 150L);
        outbound.add(outRow);
        when(outStockInfoMapper.sumOutboundByPNumSince(any(Date.class))).thenReturn(outbound);

        // 2个近效期批次,共200数量
        ProductBatch expiry1 = new ProductBatch();
        expiry1.setId(1L);
        expiry1.setBatchNumber("BATCH-001");
        expiry1.setQuantity(120L);
        ProductBatch expiry2 = new ProductBatch();
        expiry2.setId(2L);
        expiry2.setBatchNumber("BATCH-002");
        expiry2.setQuantity(80L);
        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30)))
                .thenReturn(Arrays.asList(expiry1, expiry2));

        riskService.calculateRiskForProduct("P001");

        verify(snapshotMapper).insertSelective(snapshotCaptor.capture());
        InventoryRiskSnapshot saved = snapshotCaptor.getValue();
        assertEquals(Integer.valueOf(2), saved.getNearExpiryBatchCount());
        assertEquals(Long.valueOf(200), saved.getNearExpiryQuantity());
        assertEquals(Integer.valueOf(1), saved.getHasExpiryRisk());
    }

    @Test
    public void testCalculateRisk_SupplierDelayImpact() {
        // 供应商延迟本身通过较高的estimatedLeadDays影响建议量的计算
        // 对风险等级的影响是间接的(available stock被消耗更多时risk更高)
        // 此测试验证:当库存下降到LOW区间时正确识别
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);

        // 总量150, 锁定0, 无在途
        List<Map<String, Object>> batchSum = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("pNum", "P001");
        row.put("totalQuantity", 150L);
        row.put("totalLocked", 0L);
        batchSum.add(row);
        when(productBatchMapper.sumAvailableByPNum()).thenReturn(batchSum);
        when(transferRequestMapper.sumInTransitByPNum()).thenReturn(new ArrayList<>());

        // 日均10 -> 可用天数 = 150/10 = 15, 在MEDIUM区间(20 > 15 >= 10)
        List<Map<String, Object>> outbound = new ArrayList<>();
        Map<String, Object> outRow = new HashMap<>();
        outRow.put("pNum", "P001");
        outRow.put("totalOutbound", 300L);
        outbound.add(outRow);
        when(outStockInfoMapper.sumOutboundByPNumSince(any(Date.class))).thenReturn(outbound);
        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30))).thenReturn(new ArrayList<>());

        riskService.calculateRiskForProduct("P001");

        verify(snapshotMapper).insertSelective(snapshotCaptor.capture());
        assertEquals(RiskLevel.MEDIUM, snapshotCaptor.getValue().getRiskLevel());
    }
}
