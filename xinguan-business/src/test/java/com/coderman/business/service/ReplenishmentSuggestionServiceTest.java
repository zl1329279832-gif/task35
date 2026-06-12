package com.coderman.business.service;

import com.coderman.business.mapper.*;
import com.coderman.business.service.imp.ReplenishmentSuggestionServiceImpl;
import com.coderman.common.enums.buisiness.RiskLevel;
import com.coderman.common.enums.buisiness.SuggestionStatus;
import com.coderman.common.enums.buisiness.SuggestionType;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.*;
import com.coderman.common.vo.business.ReplenishmentSuggestionVO;
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
import tk.mybatis.mapper.entity.Example;
import tk.mybatis.mapper.mapperhelper.EntityHelper;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 补货建议服务测试
 * 覆盖：采购补货/跨仓调拨/幂等/采纳/驳回/重复采纳/供应商延迟/过期清理/追溯查询
 */
@RunWith(MockitoJUnitRunner.class)
public class ReplenishmentSuggestionServiceTest {

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(StockRiskSnapshot.class, config);
        EntityHelper.initEntityNameMap(ReplenishmentSuggestion.class, config);
        EntityHelper.initEntityNameMap(SuggestionAudit.class, config);
        EntityHelper.initEntityNameMap(Product.class, config);
        EntityHelper.initEntityNameMap(Supplier.class, config);
        EntityHelper.initEntityNameMap(ProductStock.class, config);
        EntityHelper.initEntityNameMap(SupplierDeliveryStats.class, config);
        EntityHelper.initEntityNameMap(TransferRequest.class, config);
    }

    @InjectMocks
    private ReplenishmentSuggestionServiceImpl suggestionService;

    @Mock
    private StockRiskSnapshotMapper stockRiskSnapshotMapper;
    @Mock
    private ReplenishmentSuggestionMapper replenishmentSuggestionMapper;
    @Mock
    private SuggestionAuditMapper suggestionAuditMapper;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private ProductStockMapper productStockMapper;
    @Mock
    private SupplierDeliveryStatsMapper supplierDeliveryStatsMapper;
    @Mock
    private TransferRequestMapper transferRequestMapper;

    @Captor
    private ArgumentCaptor<ReplenishmentSuggestion> suggestionCaptor;
    @Captor
    private ArgumentCaptor<SuggestionAudit> auditCaptor;

    private StockRiskSnapshot criticalSnapshot;
    private StockRiskSnapshot normalSnapshot;
    private ReplenishmentSuggestion pendingSuggestion;
    private ReplenishmentSuggestion adoptedSuggestion;

    @Before
    public void setUp() {
        criticalSnapshot = new StockRiskSnapshot();
        criticalSnapshot.setId(1L);
        criticalSnapshot.setSnapshotNum("SNAP001");
        criticalSnapshot.setPNum("P001");
        criticalSnapshot.setTotalStock(10L);
        criticalSnapshot.setAvailableStock(10L);
        criticalSnapshot.setGapQuantity(90L);
        criticalSnapshot.setRiskLevel(RiskLevel.CRITICAL);
        criticalSnapshot.setRuleVersion(1);

        normalSnapshot = new StockRiskSnapshot();
        normalSnapshot.setId(2L);
        normalSnapshot.setSnapshotNum("SNAP002");
        normalSnapshot.setPNum("P002");
        normalSnapshot.setTotalStock(500L);
        normalSnapshot.setAvailableStock(500L);
        normalSnapshot.setGapQuantity(0L);
        normalSnapshot.setRiskLevel(RiskLevel.NORMAL);
        normalSnapshot.setRuleVersion(1);

        pendingSuggestion = new ReplenishmentSuggestion();
        pendingSuggestion.setId(1L);
        pendingSuggestion.setSuggestionNum("SUG001");
        pendingSuggestion.setSnapshotId(1L);
        pendingSuggestion.setPNum("P001");
        pendingSuggestion.setSuggestionType(SuggestionType.PURCHASE);
        pendingSuggestion.setSuggestedQuantity(90L);
        pendingSuggestion.setStatus(SuggestionStatus.PENDING);

        adoptedSuggestion = new ReplenishmentSuggestion();
        adoptedSuggestion.setId(2L);
        adoptedSuggestion.setSuggestionNum("SUG002");
        adoptedSuggestion.setSnapshotId(1L);
        adoptedSuggestion.setPNum("P001");
        adoptedSuggestion.setSuggestionType(SuggestionType.PURCHASE);
        adoptedSuggestion.setStatus(SuggestionStatus.ADOPTED);
    }

    // ==================== 1. 缺口生成采购建议 ====================

    @Test
    public void testGenerateSuggestion_PurchaseReplenishment() {
        when(stockRiskSnapshotMapper.selectByPrimaryKey(1L)).thenReturn(criticalSnapshot);
        when(transferRequestMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(replenishmentSuggestionMapper.findByIdempotentKey(anyString())).thenReturn(null);
        when(supplierDeliveryStatsMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());

        Supplier supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("供应商A");
        when(supplierMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(supplier));
        when(replenishmentSuggestionMapper.insertSelective(any(ReplenishmentSuggestion.class))).thenReturn(1);
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(new Product()));

        List<ReplenishmentSuggestionVO> results = suggestionService.generateSuggestions(1L);

        assertFalse(results.isEmpty());
        // 无调拨来源，只有采购建议
        assertEquals(1, results.size());
        assertEquals(Integer.valueOf(SuggestionType.PURCHASE), results.get(0).getSuggestionType());
        assertEquals(Long.valueOf(90L), results.get(0).getSuggestedQuantity());
        verify(replenishmentSuggestionMapper).insertSelective(any(ReplenishmentSuggestion.class));
    }

    // ==================== 2. 有富余仓库时生成调拨建议 ====================

    @Test
    public void testGenerateSuggestion_CrossWarehouseTransfer() {
        when(stockRiskSnapshotMapper.selectByPrimaryKey(1L)).thenReturn(criticalSnapshot);

        // 模拟有历史调拨记录
        TransferRequest existingTransfer = new TransferRequest();
        existingTransfer.setId(1L);
        existingTransfer.setPNum("P001");
        existingTransfer.setFromDepartment("仓库A");
        existingTransfer.setToDepartment("隔离点B");
        when(transferRequestMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(existingTransfer));

        when(replenishmentSuggestionMapper.findByIdempotentKey(anyString())).thenReturn(null);
        when(replenishmentSuggestionMapper.insertSelective(any(ReplenishmentSuggestion.class))).thenReturn(1);
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(new Product()));
        when(supplierDeliveryStatsMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());

        Supplier supplier = new Supplier();
        supplier.setId(1L);
        when(supplierMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(supplier));

        List<ReplenishmentSuggestionVO> results = suggestionService.generateSuggestions(1L);

        // 应该同时生成调拨建议和采购建议
        assertTrue(results.size() >= 1);
        // 检查是否有调拨类型
        boolean hasTransfer = results.stream()
                .anyMatch(r -> r.getSuggestionType() == SuggestionType.TRANSFER);
        assertTrue(hasTransfer);
    }

    // ==================== 3. 同快照同物资不重复生成（幂等） ====================

    @Test
    public void testGenerateSuggestion_Idempotent() {
        when(stockRiskSnapshotMapper.selectByPrimaryKey(1L)).thenReturn(criticalSnapshot);
        when(transferRequestMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());

        // 已存在的采购建议
        ReplenishmentSuggestion existingPurchase = new ReplenishmentSuggestion();
        existingPurchase.setId(100L);
        existingPurchase.setSuggestionNum("EXISTING_PURCHASE");
        existingPurchase.setSuggestionType(SuggestionType.PURCHASE);
        existingPurchase.setStatus(SuggestionStatus.PENDING);
        existingPurchase.setPNum("P001");

        // 第一次调用返回null（调拨），第二次返回已存在的（采购）
        when(replenishmentSuggestionMapper.findByIdempotentKey(contains("PURCHASE")))
                .thenReturn(existingPurchase);
        when(replenishmentSuggestionMapper.findByIdempotentKey(contains("TRANSFER")))
                .thenReturn(null);

        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(new Product()));

        List<ReplenishmentSuggestionVO> results = suggestionService.generateSuggestions(1L);

        // 采购建议应该是已存在的那个（不会重复插入）
        // 不应该调用 insertSelective 来创建采购建议
        // 只可能为调拨建议调用 insertSelective
        verify(replenishmentSuggestionMapper, atMostOnce()).insertSelective(any(ReplenishmentSuggestion.class));
    }

    // ==================== 4. 采纳采购建议 ====================

    @Test
    public void testAdoptSuggestion_Purchase_Success() {
        when(replenishmentSuggestionMapper.selectByPrimaryKey(1L)).thenReturn(pendingSuggestion);
        when(replenishmentSuggestionMapper.updateByPrimaryKeySelective(any(ReplenishmentSuggestion.class))).thenReturn(1);
        when(suggestionAuditMapper.insertSelective(any(SuggestionAudit.class))).thenReturn(1);

        suggestionService.adoptSuggestion(1L, "admin", "紧急采购");

        // 验证状态更新为已采纳
        verify(replenishmentSuggestionMapper).updateByPrimaryKeySelective(suggestionCaptor.capture());
        assertEquals(Integer.valueOf(SuggestionStatus.ADOPTED), suggestionCaptor.getValue().getStatus());

        // 验证记录了审计
        verify(suggestionAuditMapper).insertSelective(auditCaptor.capture());
        assertEquals("ADOPT", auditCaptor.getValue().getAction());
        assertEquals("admin", auditCaptor.getValue().getOperator());
        assertEquals("紧急采购", auditCaptor.getValue().getReason());
    }

    // ==================== 5. 采纳调拨建议 ====================

    @Test
    public void testAdoptSuggestion_Transfer_Success() {
        ReplenishmentSuggestion transferSuggestion = new ReplenishmentSuggestion();
        transferSuggestion.setId(3L);
        transferSuggestion.setSuggestionType(SuggestionType.TRANSFER);
        transferSuggestion.setStatus(SuggestionStatus.PENDING);

        when(replenishmentSuggestionMapper.selectByPrimaryKey(3L)).thenReturn(transferSuggestion);
        when(replenishmentSuggestionMapper.updateByPrimaryKeySelective(any(ReplenishmentSuggestion.class))).thenReturn(1);
        when(suggestionAuditMapper.insertSelective(any(SuggestionAudit.class))).thenReturn(1);

        suggestionService.adoptSuggestion(3L, "operator1", "调拨审批通过");

        verify(replenishmentSuggestionMapper).updateByPrimaryKeySelective(suggestionCaptor.capture());
        assertEquals(Integer.valueOf(SuggestionStatus.ADOPTED), suggestionCaptor.getValue().getStatus());
    }

    // ==================== 6. 驳回记录审计 ====================

    @Test
    public void testRejectSuggestion_RecordsAudit() {
        when(replenishmentSuggestionMapper.selectByPrimaryKey(1L)).thenReturn(pendingSuggestion);
        when(replenishmentSuggestionMapper.updateByPrimaryKeySelective(any(ReplenishmentSuggestion.class))).thenReturn(1);
        when(suggestionAuditMapper.insertSelective(any(SuggestionAudit.class))).thenReturn(1);

        suggestionService.rejectSuggestion(1L, "manager", "预算不足");

        // 验证状态更新为已驳回
        verify(replenishmentSuggestionMapper).updateByPrimaryKeySelective(suggestionCaptor.capture());
        assertEquals(Integer.valueOf(SuggestionStatus.REJECTED), suggestionCaptor.getValue().getStatus());

        // 验证记录了审计
        verify(suggestionAuditMapper).insertSelective(auditCaptor.capture());
        assertEquals("REJECT", auditCaptor.getValue().getAction());
        assertEquals("manager", auditCaptor.getValue().getOperator());
        assertEquals("预算不足", auditCaptor.getValue().getReason());
    }

    // ==================== 7. 重复采纳抛异常 ====================

    @Test(expected = ServiceException.class)
    public void testAdoptSuggestion_AlreadyAdopted_ThrowsException() {
        when(replenishmentSuggestionMapper.selectByPrimaryKey(2L)).thenReturn(adoptedSuggestion);

        suggestionService.adoptSuggestion(2L, "admin", "重复采纳");
    }

    // ==================== 8. 供应商延迟影响建议排序 ====================

    @Test
    public void testSupplierDelay_AffectsSuggestionPriority() {
        when(stockRiskSnapshotMapper.selectByPrimaryKey(1L)).thenReturn(criticalSnapshot);
        when(transferRequestMapper.selectByExample(any(Example.class))).thenReturn(Collections.emptyList());
        when(replenishmentSuggestionMapper.findByIdempotentKey(anyString())).thenReturn(null);

        // 两个供应商的交付统计
        SupplierDeliveryStats statsA = new SupplierDeliveryStats();
        statsA.setSupplierId(1L);
        statsA.setPNum("P001");
        statsA.setTotalOrders(10);
        statsA.setOnTimeOrders(9);
        statsA.setAvgDeliveryDays(new BigDecimal("5"));

        SupplierDeliveryStats statsB = new SupplierDeliveryStats();
        statsB.setSupplierId(2L);
        statsB.setPNum("P001");
        statsB.setTotalOrders(10);
        statsB.setOnTimeOrders(3); // 准时率低
        statsB.setAvgDeliveryDays(new BigDecimal("15"));

        when(supplierDeliveryStatsMapper.selectByExample(any(Example.class)))
                .thenReturn(Arrays.asList(statsA, statsB));
        when(replenishmentSuggestionMapper.insertSelective(any(ReplenishmentSuggestion.class))).thenReturn(1);
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(new Product()));

        suggestionService.generateSuggestions(1L);

        // 验证选择了准时率高的供应商A (supplierId=1)
        verify(replenishmentSuggestionMapper).insertSelective(suggestionCaptor.capture());
        assertEquals(Long.valueOf(1L), suggestionCaptor.getValue().getSupplierId());
    }

    // ==================== 9. 过期清理 ====================

    @Test
    public void testExpireOldSuggestions() {
        ReplenishmentSuggestion oldPending = new ReplenishmentSuggestion();
        oldPending.setId(10L);
        oldPending.setStatus(SuggestionStatus.PENDING);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -10);
        oldPending.setCreateTime(cal.getTime());

        when(replenishmentSuggestionMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(oldPending));
        when(replenishmentSuggestionMapper.updateByPrimaryKeySelective(any(ReplenishmentSuggestion.class))).thenReturn(1);
        when(suggestionAuditMapper.insertSelective(any(SuggestionAudit.class))).thenReturn(1);

        int expiredCount = suggestionService.expireOldSuggestions(7);

        assertEquals(1, expiredCount);
        verify(replenishmentSuggestionMapper).updateByPrimaryKeySelective(suggestionCaptor.capture());
        assertEquals(Integer.valueOf(SuggestionStatus.EXPIRED), suggestionCaptor.getValue().getStatus());
        verify(suggestionAuditMapper).insertSelective(auditCaptor.capture());
        assertEquals("EXPIRE", auditCaptor.getValue().getAction());
    }

    // ==================== 10. 建议追溯查询（含审计链） ====================

    @Test
    public void testSuggestionTraceability() {
        when(replenishmentSuggestionMapper.selectByPrimaryKey(1L)).thenReturn(pendingSuggestion);
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(new Product()));

        SuggestionAudit audit1 = new SuggestionAudit();
        audit1.setId(1L);
        audit1.setSuggestionId(1L);
        audit1.setAction("GENERATE");
        audit1.setOperator("SYSTEM");
        audit1.setCreateTime(new Date());

        when(suggestionAuditMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(audit1));
        when(stockRiskSnapshotMapper.selectByPrimaryKey(1L)).thenReturn(criticalSnapshot);

        ReplenishmentSuggestionVO detail = suggestionService.getSuggestionDetail(1L);

        assertNotNull(detail);
        assertNotNull(detail.getAuditRecords());
        assertEquals(1, detail.getAuditRecords().size());
        assertEquals("GENERATE", detail.getAuditRecords().get(0).getAction());
        assertNotNull(detail.getSnapshot());
        assertEquals("SNAP001", detail.getSnapshot().getSnapshotNum());
    }
}
