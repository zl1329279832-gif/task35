package com.coderman.business.service;

import com.coderman.business.mapper.*;
import com.coderman.business.service.imp.StockWarningServiceImpl;
import com.coderman.common.enums.buisiness.RiskLevel;
import com.coderman.common.model.business.*;
import com.coderman.common.vo.business.RiskDashboardVO;
import com.coderman.common.vo.business.StockRiskSnapshotVO;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import tk.mybatis.mapper.entity.Config;
import tk.mybatis.mapper.entity.Example;
import tk.mybatis.mapper.mapperhelper.EntityHelper;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 库存预警服务测试
 * 覆盖：库存不足/近效期预警/正常库存/在途调拨占用/并发出库/手工改库存重算/锁定库存扣除/批量快照生成
 */
@RunWith(MockitoJUnitRunner.class)
public class StockWarningServiceTest {

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(ProductStock.class, config);
        EntityHelper.initEntityNameMap(ProductBatch.class, config);
        EntityHelper.initEntityNameMap(Product.class, config);
        EntityHelper.initEntityNameMap(TransferRequest.class, config);
        EntityHelper.initEntityNameMap(OutStock.class, config);
        EntityHelper.initEntityNameMap(OutStockInfo.class, config);
        EntityHelper.initEntityNameMap(StockWarningRule.class, config);
        EntityHelper.initEntityNameMap(StockRiskSnapshot.class, config);
        EntityHelper.initEntityNameMap(ReplenishmentSuggestion.class, config);
    }

    @InjectMocks
    private StockWarningServiceImpl stockWarningService;

    @Mock
    private ProductStockMapper productStockMapper;
    @Mock
    private ProductBatchMapper productBatchMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private TransferRequestMapper transferRequestMapper;
    @Mock
    private OutStockMapper outStockMapper;
    @Mock
    private OutStockInfoMapper outStockInfoMapper;
    @Mock
    private StockWarningRuleMapper stockWarningRuleMapper;
    @Mock
    private StockRiskSnapshotMapper stockRiskSnapshotMapper;
    @Mock
    private ReplenishmentSuggestionMapper replenishmentSuggestionMapper;

    private StockWarningRule defaultRule;
    private ProductStock lowStock;
    private ProductStock normalStock;
    private Product product;

    @Before
    public void setUp() {
        defaultRule = new StockWarningRule();
        defaultRule.setId(1L);
        defaultRule.setMinStockDays(14);
        defaultRule.setNearExpiryDays(30);
        defaultRule.setSafetyStock(100L);
        defaultRule.setReorderPoint(50L);
        defaultRule.setVersion(1);
        defaultRule.setStatus(0);

        lowStock = new ProductStock();
        lowStock.setId(1L);
        lowStock.setPNum("P001");
        lowStock.setStock(10L);
        lowStock.setVersion(0);

        normalStock = new ProductStock();
        normalStock.setId(2L);
        normalStock.setPNum("P002");
        normalStock.setStock(500L);
        normalStock.setVersion(0);

        product = new Product();
        product.setId(1L);
        product.setPNum("P001");
        product.setName("N95口罩");
    }

    // ==================== 1. 库存严重不足 → CRITICAL ====================

    @Test
    public void testRiskSnapshot_StockShortage_Critical() {
        when(stockWarningRuleMapper.findActiveRule("P001")).thenReturn(defaultRule);
        when(productStockMapper.selectOneByExample(any(Example.class))).thenReturn(lowStock);
        when(productBatchMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(transferRequestMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(product));

        // 模拟日均出库速度 = 5个/天 (30天出150个)
        mockOutboundData("P001", 150);

        when(stockRiskSnapshotMapper.insertSelective(any(StockRiskSnapshot.class))).thenReturn(1);

        StockRiskSnapshotVO result = stockWarningService.generateRiskSnapshot("P001");

        assertNotNull(result);
        assertEquals("P001", result.getPNum());
        assertEquals(Long.valueOf(10L), result.getTotalStock());
        assertEquals(Long.valueOf(10L), result.getAvailableStock());
        assertEquals(Long.valueOf(90L), result.getGapQuantity()); // safety_stock(100) - available(10) = 90
        // available_days = 10 / 5 = 2天 → CRITICAL (<=3)
        assertEquals(Integer.valueOf(RiskLevel.CRITICAL), result.getRiskLevel());
        verify(stockRiskSnapshotMapper).insertSelective(any(StockRiskSnapshot.class));
    }

    // ==================== 2. 近效期预警 → LOW ====================

    @Test
    public void testRiskSnapshot_NearExpiry_Low() {
        when(stockWarningRuleMapper.findActiveRule("P001")).thenReturn(defaultRule);

        // 库存充足
        ProductStock sufficientStock = new ProductStock();
        sufficientStock.setId(1L);
        sufficientStock.setPNum("P001");
        sufficientStock.setStock(500L);
        sufficientStock.setVersion(0);
        when(productStockMapper.selectOneByExample(any(Example.class))).thenReturn(sufficientStock);

        // 有近效期批次
        ProductBatch nearExpiryBatch = new ProductBatch();
        nearExpiryBatch.setId(1L);
        nearExpiryBatch.setPNum("P001");
        nearExpiryBatch.setQuantity(50L);
        nearExpiryBatch.setLockedQuantity(0L);
        nearExpiryBatch.setStatus(0);
        nearExpiryBatch.setQualityStatus(1);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, 15);
        nearExpiryBatch.setExpiryDate(cal.getTime());

        // 第一批查锁定量，第二批查近效期
        when(productBatchMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(nearExpiryBatch))
                .thenReturn(Collections.singletonList(nearExpiryBatch));

        when(transferRequestMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(product));

        // 日均出库=10，可用天数=500/10=50天 → 充足
        mockOutboundData("P001", 300);

        when(stockRiskSnapshotMapper.insertSelective(any(StockRiskSnapshot.class))).thenReturn(1);

        StockRiskSnapshotVO result = stockWarningService.generateRiskSnapshot("P001");

        assertNotNull(result);
        assertEquals(Long.valueOf(500L), result.getTotalStock());
        assertEquals(Long.valueOf(50L), result.getNearExpiryStock());
        // 可用天数50天 > 14天(min_stock_days)，但有近效期 → LOW
        assertEquals(Integer.valueOf(RiskLevel.LOW), result.getRiskLevel());
    }

    // ==================== 3. 正常库存 → NORMAL ====================

    @Test
    public void testRiskSnapshot_NormalStock() {
        when(stockWarningRuleMapper.findActiveRule("P002")).thenReturn(null);
        when(stockWarningRuleMapper.findGlobalRule()).thenReturn(defaultRule);
        when(productStockMapper.selectOneByExample(any(Example.class))).thenReturn(normalStock);
        when(productBatchMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(transferRequestMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());

        Product p2 = new Product();
        p2.setPNum("P002");
        p2.setName("防护服");
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(p2));

        // 日均出库 = 10个/天
        mockOutboundData("P002", 300);

        when(stockRiskSnapshotMapper.insertSelective(any(StockRiskSnapshot.class))).thenReturn(1);

        StockRiskSnapshotVO result = stockWarningService.generateRiskSnapshot("P002");

        assertNotNull(result);
        assertEquals(Long.valueOf(500L), result.getTotalStock());
        // 可用天数 = 500/10 = 50天，无近效期，无缺口 → NORMAL
        assertEquals(Integer.valueOf(RiskLevel.NORMAL), result.getRiskLevel());
    }

    // ==================== 4. 在途调拨占用 ====================

    @Test
    public void testRiskSnapshot_InTransitOccupation() {
        when(stockWarningRuleMapper.findActiveRule("P001")).thenReturn(defaultRule);
        when(productStockMapper.selectOneByExample(any(Example.class))).thenReturn(lowStock); // 10个
        when(productBatchMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());

        // 模拟在途调拨：2个已审批 + 1个已发送
        TransferRequest approvedTransfer = new TransferRequest();
        approvedTransfer.setId(1L);
        approvedTransfer.setPNum("P001");
        approvedTransfer.setTransferQuantity(30L);
        approvedTransfer.setStatus(3); // APPROVED

        TransferRequest sentTransfer = new TransferRequest();
        sentTransfer.setId(2L);
        sentTransfer.setPNum("P001");
        sentTransfer.setTransferQuantity(20L);
        sentTransfer.setStatus(4); // SENT

        when(transferRequestMapper.selectByExample(any(Example.class)))
                .thenReturn(Arrays.asList(approvedTransfer, sentTransfer));

        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(product));

        // 日均出库 = 5个/天
        mockOutboundData("P001", 150);

        when(stockRiskSnapshotMapper.insertSelective(any(StockRiskSnapshot.class))).thenReturn(1);

        StockRiskSnapshotVO result = stockWarningService.generateRiskSnapshot("P001");

        assertNotNull(result);
        assertEquals(Long.valueOf(10L), result.getTotalStock());
        assertEquals(Long.valueOf(50L), result.getInTransitStock()); // 30 + 20
        // available_days = (10 + 50) / 5 = 12天
        // 12天 < minStockDays(14) → MEDIUM
        assertEquals(Integer.valueOf(RiskLevel.MEDIUM), result.getRiskLevel());
    }

    // ==================== 5. 并发出库后风险重算 ====================

    @Test
    public void testRiskSnapshot_ConcurrentOutbound() {
        when(stockWarningRuleMapper.findActiveRule("P001")).thenReturn(defaultRule);

        // 模拟并发出库后库存已被大幅扣减
        ProductStock depletedStock = new ProductStock();
        depletedStock.setId(1L);
        depletedStock.setPNum("P001");
        depletedStock.setStock(5L);
        depletedStock.setVersion(3); // 经过多次并发更新
        when(productStockMapper.selectOneByExample(any(Example.class))).thenReturn(depletedStock);

        when(productBatchMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(transferRequestMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(product));

        // 日均出库较高
        mockOutboundData("P001", 300);

        when(stockRiskSnapshotMapper.insertSelective(any(StockRiskSnapshot.class))).thenReturn(1);

        StockRiskSnapshotVO result = stockWarningService.generateRiskSnapshot("P001");

        assertNotNull(result);
        assertEquals(Long.valueOf(5L), result.getTotalStock());
        // available_days = 5 / 10 = 0.5天 → CRITICAL
        assertEquals(Integer.valueOf(RiskLevel.CRITICAL), result.getRiskLevel());
    }

    // ==================== 6. 手工改库存触发重算 ====================

    @Test
    public void testRiskSnapshot_ManualStockChange_Recalculate() {
        when(stockWarningRuleMapper.findActiveRule("P001")).thenReturn(defaultRule);

        // 模拟手工调整后的库存
        ProductStock adjustedStock = new ProductStock();
        adjustedStock.setId(1L);
        adjustedStock.setPNum("P001");
        adjustedStock.setStock(200L);
        adjustedStock.setVersion(5);
        when(productStockMapper.selectOneByExample(any(Example.class))).thenReturn(adjustedStock);

        when(productBatchMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(transferRequestMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(product));
        mockOutboundData("P001", 150);
        when(stockRiskSnapshotMapper.insertSelective(any(StockRiskSnapshot.class))).thenReturn(1);

        // recalculateRisk 内部调用 generateRiskSnapshot
        stockWarningService.recalculateRisk("P001");

        // 验证确实生成了新快照
        verify(stockRiskSnapshotMapper).insertSelective(any(StockRiskSnapshot.class));
    }

    // ==================== 7. 锁定库存不计入可用量 ====================

    @Test
    public void testRiskSnapshot_LockedStockDeducted() {
        when(stockWarningRuleMapper.findActiveRule("P001")).thenReturn(defaultRule);

        // 总库存200
        ProductStock stockWithLocks = new ProductStock();
        stockWithLocks.setId(1L);
        stockWithLocks.setPNum("P001");
        stockWithLocks.setStock(200L);
        stockWithLocks.setVersion(0);
        when(productStockMapper.selectOneByExample(any(Example.class))).thenReturn(stockWithLocks);

        // 但150被锁定了
        ProductBatch lockedBatch = new ProductBatch();
        lockedBatch.setId(1L);
        lockedBatch.setPNum("P001");
        lockedBatch.setQuantity(200L);
        lockedBatch.setLockedQuantity(150L);
        lockedBatch.setStatus(0);
        when(productBatchMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(lockedBatch));

        when(transferRequestMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(product));

        // 日均出库=5
        mockOutboundData("P001", 150);
        when(stockRiskSnapshotMapper.insertSelective(any(StockRiskSnapshot.class))).thenReturn(1);

        StockRiskSnapshotVO result = stockWarningService.generateRiskSnapshot("P001");

        assertNotNull(result);
        assertEquals(Long.valueOf(200L), result.getTotalStock());
        assertEquals(Long.valueOf(150L), result.getLockedStock());
        assertEquals(Long.valueOf(50L), result.getAvailableStock()); // 200 - 150 = 50
        // available_days = 50 / 5 = 10天 → 10 < 14(minStockDays) → MEDIUM
        assertEquals(Integer.valueOf(RiskLevel.MEDIUM), result.getRiskLevel());
    }

    // ==================== 8. 批量快照生成 ====================

    @Test
    public void testGenerateAllSnapshots_MultipleProducts() {
        // 模拟3个有库存的物资
        ProductStock stock1 = new ProductStock();
        stock1.setPNum("P001");
        stock1.setStock(10L);

        ProductStock stock2 = new ProductStock();
        stock2.setPNum("P002");
        stock2.setStock(500L);

        ProductStock stock3 = new ProductStock();
        stock3.setPNum("P003");
        stock3.setStock(50L);

        when(productStockMapper.selectByExample(any(Example.class)))
                .thenReturn(Arrays.asList(stock1, stock2, stock3));

        when(stockWarningRuleMapper.findActiveRule(anyString())).thenReturn(null);
        when(stockWarningRuleMapper.findGlobalRule()).thenReturn(defaultRule);
        when(productBatchMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(transferRequestMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());

        Product p3 = new Product();
        p3.setPNum("P003");
        p3.setName("消毒液");
        when(productMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(product))
                .thenReturn(Collections.singletonList(new Product()))
                .thenReturn(Collections.singletonList(p3));

        mockOutboundData("P001", 150);
        when(stockRiskSnapshotMapper.insertSelective(any(StockRiskSnapshot.class))).thenReturn(1);

        List<StockRiskSnapshotVO> results = stockWarningService.generateAllRiskSnapshots();

        // 应该为每个物资生成快照
        verify(stockRiskSnapshotMapper, times(3)).insertSelective(any(StockRiskSnapshot.class));
    }

    // ==================== 辅助方法 ====================

    /**
     * 模拟出库数据
     * @param pNum 物资编号
     * @param totalOutbound 30天内总出库量
     */
    private void mockOutboundData(String pNum, int totalOutbound) {
        OutStock outStock = new OutStock();
        outStock.setId(1L);
        outStock.setOutNum("OUT001");
        outStock.setStatus(0);

        when(outStockMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(outStock));

        OutStockInfo outStockInfo = new OutStockInfo();
        outStockInfo.setId(1L);
        outStockInfo.setOutNum("OUT001");
        outStockInfo.setPNum(pNum);
        outStockInfo.setProductNumber(totalOutbound);

        when(outStockInfoMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(outStockInfo));
    }
}
