package com.coderman.business.service;

import com.coderman.business.mapper.*;
import com.coderman.business.service.imp.ReplenishSuggestionServiceImpl;
import com.coderman.common.enums.buisiness.*;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.*;
import com.coderman.common.vo.business.ReplenishSuggestionVO;
import com.coderman.common.vo.business.SuggestionAuditVO;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReplenishSuggestionServiceTest {

    @InjectMocks
    private ReplenishSuggestionServiceImpl suggestionService;

    @Mock
    private ReplenishSuggestionMapper suggestionMapper;

    @Mock
    private SuggestionAuditMapper auditMapper;

    @Mock
    private InventoryRiskSnapshotMapper snapshotMapper;

    @Mock
    private ReplenishRuleVersionMapper ruleVersionMapper;

    @Mock
    private ProductBatchMapper productBatchMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private TransferRequestMapper transferRequestMapper;

    @Mock
    private SupplierDeliveryStatService deliveryStatService;

    @Captor
    private ArgumentCaptor<ReplenishSuggestion> suggestionCaptor;

    @Captor
    private ArgumentCaptor<SuggestionAudit> auditCaptor;

    @Captor
    private ArgumentCaptor<TransferRequest> transferCaptor;

    private ReplenishRuleVersion rule;
    private InventoryRiskSnapshot criticalSnapshot;
    private ReplenishSuggestion pendingSuggestion;
    private ReplenishSuggestion adoptedSuggestion;

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(ReplenishSuggestion.class, config);
        EntityHelper.initEntityNameMap(SuggestionAudit.class, config);
        EntityHelper.initEntityNameMap(InventoryRiskSnapshot.class, config);
        EntityHelper.initEntityNameMap(ReplenishRuleVersion.class, config);
        EntityHelper.initEntityNameMap(ProductBatch.class, config);
        EntityHelper.initEntityNameMap(Product.class, config);
        EntityHelper.initEntityNameMap(TransferRequest.class, config);
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

        criticalSnapshot = new InventoryRiskSnapshot();
        criticalSnapshot.setId(10L);
        criticalSnapshot.setPNum("P001");
        criticalSnapshot.setRiskLevel(RiskLevel.CRITICAL);
        criticalSnapshot.setAvailableStock(20L);
        criticalSnapshot.setDailyConsumptionRate(new BigDecimal("10.0000"));
        criticalSnapshot.setAvailableDays(new BigDecimal("2.00"));

        pendingSuggestion = new ReplenishSuggestion();
        pendingSuggestion.setId(1L);
        pendingSuggestion.setSuggestionNum("SUG-001");
        pendingSuggestion.setPNum("P001");
        pendingSuggestion.setSuggestionType(SuggestionType.PURCHASE);
        pendingSuggestion.setRiskLevel(RiskLevel.CRITICAL);
        pendingSuggestion.setSuggestedQuantity(200L);
        pendingSuggestion.setSupplierId(100L);
        pendingSuggestion.setStatus(SuggestionStatus.PENDING);
        pendingSuggestion.setCreateTime(new Date());

        adoptedSuggestion = new ReplenishSuggestion();
        adoptedSuggestion.setId(2L);
        adoptedSuggestion.setSuggestionNum("SUG-002");
        adoptedSuggestion.setPNum("P001");
        adoptedSuggestion.setSuggestionType(SuggestionType.TRANSFER);
        adoptedSuggestion.setRiskLevel(RiskLevel.HIGH);
        adoptedSuggestion.setSuggestedQuantity(100L);
        adoptedSuggestion.setFromDepartment("仓库A");
        adoptedSuggestion.setToDepartment("仓库B");
        adoptedSuggestion.setStatus(SuggestionStatus.ADOPTED);
        adoptedSuggestion.setRelatedTransferNum("TRANSFER-001");
        adoptedSuggestion.setCreateTime(new Date());
    }

    @Test
    public void testGenerateSuggestion_PurchaseType_CriticalRisk() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);
        when(snapshotMapper.findLatestByPNum("P001")).thenReturn(criticalSnapshot);

        // 无去重冲突
        when(suggestionMapper.findActiveByPNumAndType(eq("P001"), anyString(), eq(24)))
                .thenReturn(new ArrayList<>());

        // 无过往调拨记录(不生成TRANSFER建议)
        when(transferRequestMapper.selectByExample(any())).thenReturn(new ArrayList<>());

        // 近效期批次
        ProductBatch nearExpiry = new ProductBatch();
        nearExpiry.setBatchNumber("BATCH-EXP-001");
        nearExpiry.setQuantity(10L);
        nearExpiry.setLockedQuantity(0L);
        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30)))
                .thenReturn(Collections.singletonList(nearExpiry));
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("production_date ASC")))
                .thenReturn(Collections.singletonList(nearExpiry));

        // 供应商
        ProductBatch recentBatch = new ProductBatch();
        recentBatch.setSupplierId(100L);
        when(productBatchMapper.selectByExample(any())).thenReturn(Collections.singletonList(recentBatch));
        when(deliveryStatService.getEstimatedLeadDays(100L, "P001")).thenReturn(7);

        List<ReplenishSuggestionVO> results = suggestionService.generateSuggestionsForProduct("P001");

        assertFalse(results.isEmpty());
        verify(suggestionMapper, atLeastOnce()).insertSelective(suggestionCaptor.capture());

        // 找到PURCHASE类型的建议
        ReplenishSuggestion purchaseSuggestion = null;
        for (ReplenishSuggestion s : suggestionCaptor.getAllValues()) {
            if (SuggestionType.PURCHASE.equals(s.getSuggestionType())) {
                purchaseSuggestion = s;
                break;
            }
        }
        assertNotNull(purchaseSuggestion);
        assertEquals(SuggestionType.PURCHASE, purchaseSuggestion.getSuggestionType());
        assertEquals(Long.valueOf(100), purchaseSuggestion.getSupplierId());
        // 建议量 = 14*10 - 20 + 7*10 = 140 - 20 + 70 = 190
        assertEquals(Long.valueOf(190), purchaseSuggestion.getSuggestedQuantity());

        // 验证记录了审计
        verify(auditMapper, atLeastOnce()).insertSelective(auditCaptor.capture());
    }

    @Test
    public void testGenerateSuggestion_TransferType_SurplusDepartment() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);
        when(snapshotMapper.findLatestByPNum("P001")).thenReturn(criticalSnapshot);

        // 无去重冲突
        when(suggestionMapper.findActiveByPNumAndType(eq("P001"), eq(SuggestionType.TRANSFER), eq(24)))
                .thenReturn(new ArrayList<>());
        when(suggestionMapper.findActiveByPNumAndType(eq("P001"), eq(SuggestionType.PURCHASE), eq(24)))
                .thenReturn(new ArrayList<>());

        // 有过往调拨记录 -> 存在可调拨来源
        TransferRequest pastTransfer = new TransferRequest();
        pastTransfer.setFromDepartment("仓库A");
        pastTransfer.setToDepartment("仓库B");
        pastTransfer.setPNum("P001");
        pastTransfer.setStatus(TransferStatus.COMPLETED);
        when(transferRequestMapper.selectByExample(any())).thenReturn(Collections.singletonList(pastTransfer));

        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30)))
                .thenReturn(new ArrayList<>());
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("production_date ASC")))
                .thenReturn(new ArrayList<>());

        // 供应商(for purchase suggestion)
        ProductBatch recentBatch = new ProductBatch();
        recentBatch.setSupplierId(100L);
        when(productBatchMapper.selectByExample(any())).thenReturn(Collections.singletonList(recentBatch));
        when(deliveryStatService.getEstimatedLeadDays(100L, "P001")).thenReturn(7);

        List<ReplenishSuggestionVO> results = suggestionService.generateSuggestionsForProduct("P001");

        verify(suggestionMapper, atLeastOnce()).insertSelective(suggestionCaptor.capture());

        // 应该有TRANSFER类型的建议
        boolean hasTransfer = false;
        for (ReplenishSuggestion s : suggestionCaptor.getAllValues()) {
            if (SuggestionType.TRANSFER.equals(s.getSuggestionType())) {
                hasTransfer = true;
                assertEquals("仓库A", s.getFromDepartment());
                assertEquals("仓库B", s.getToDepartment());
            }
        }
        assertTrue("Should generate TRANSFER suggestion", hasTransfer);
    }

    @Test
    public void testGenerateSuggestion_NearExpiryPriority() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);
        when(snapshotMapper.findLatestByPNum("P001")).thenReturn(criticalSnapshot);
        when(suggestionMapper.findActiveByPNumAndType(anyString(), anyString(), anyInt()))
                .thenReturn(new ArrayList<>());
        when(transferRequestMapper.selectByExample(any())).thenReturn(new ArrayList<>());

        // 近效期批次排在前面
        ProductBatch expBatch = new ProductBatch();
        expBatch.setBatchNumber("BATCH-EXPIRING");
        expBatch.setQuantity(50L);
        expBatch.setLockedQuantity(0L);
        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30)))
                .thenReturn(Collections.singletonList(expBatch));

        ProductBatch normalBatch = new ProductBatch();
        normalBatch.setBatchNumber("BATCH-NORMAL");
        normalBatch.setQuantity(100L);
        normalBatch.setLockedQuantity(0L);
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("production_date ASC")))
                .thenReturn(Arrays.asList(normalBatch, expBatch));

        ProductBatch recentBatch = new ProductBatch();
        recentBatch.setSupplierId(100L);
        when(productBatchMapper.selectByExample(any())).thenReturn(Collections.singletonList(recentBatch));
        when(deliveryStatService.getEstimatedLeadDays(100L, "P001")).thenReturn(7);

        suggestionService.generateSuggestionsForProduct("P001");

        verify(suggestionMapper, atLeastOnce()).insertSelective(suggestionCaptor.capture());
        // 验证priorityBatches中近效期排在前面
        for (ReplenishSuggestion s : suggestionCaptor.getAllValues()) {
            if (s.getPriorityBatches() != null) {
                assertTrue(s.getPriorityBatches().startsWith("BATCH-EXPIRING"));
                break;
            }
        }
    }

    @Test
    public void testGenerateSuggestion_FIFOOrdering() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);
        when(snapshotMapper.findLatestByPNum("P001")).thenReturn(criticalSnapshot);
        when(suggestionMapper.findActiveByPNumAndType(anyString(), anyString(), anyInt()))
                .thenReturn(new ArrayList<>());
        when(transferRequestMapper.selectByExample(any())).thenReturn(new ArrayList<>());

        // 无近效期
        when(productBatchMapper.findNearExpiryBatchesByPNum(eq("P001"), eq(30)))
                .thenReturn(new ArrayList<>());

        // FIFO排序的批次
        ProductBatch oldBatch = new ProductBatch();
        oldBatch.setBatchNumber("BATCH-OLD");
        ProductBatch newBatch = new ProductBatch();
        newBatch.setBatchNumber("BATCH-NEW");
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("production_date ASC")))
                .thenReturn(Arrays.asList(oldBatch, newBatch));

        ProductBatch recentBatch = new ProductBatch();
        recentBatch.setSupplierId(100L);
        when(productBatchMapper.selectByExample(any())).thenReturn(Collections.singletonList(recentBatch));
        when(deliveryStatService.getEstimatedLeadDays(100L, "P001")).thenReturn(7);

        suggestionService.generateSuggestionsForProduct("P001");

        verify(suggestionMapper, atLeastOnce()).insertSelective(suggestionCaptor.capture());
        for (ReplenishSuggestion s : suggestionCaptor.getAllValues()) {
            if (s.getPriorityBatches() != null) {
                assertEquals("BATCH-OLD,BATCH-NEW", s.getPriorityBatches());
                break;
            }
        }
    }

    @Test
    public void testGenerateSuggestion_Idempotent_NoDuplicate() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);
        when(snapshotMapper.findLatestByPNum("P001")).thenReturn(criticalSnapshot);

        // 去重窗口内已有PURCHASE建议
        when(suggestionMapper.findActiveByPNumAndType(eq("P001"), eq(SuggestionType.PURCHASE), eq(24)))
                .thenReturn(Collections.singletonList(pendingSuggestion));
        // TRANSFER也已有
        when(suggestionMapper.findActiveByPNumAndType(eq("P001"), eq(SuggestionType.TRANSFER), eq(24)))
                .thenReturn(Collections.singletonList(new ReplenishSuggestion()));

        List<ReplenishSuggestionVO> results = suggestionService.generateSuggestionsForProduct("P001");

        // 不应创建新建议
        verify(suggestionMapper, never()).insertSelective(any(ReplenishSuggestion.class));
        assertTrue(results.isEmpty());
    }

    @Test
    public void testGenerateSuggestion_DedupWindowExpired_CreatesNew() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);
        when(snapshotMapper.findLatestByPNum("P001")).thenReturn(criticalSnapshot);

        // 去重窗口内无建议(窗口过期)
        when(suggestionMapper.findActiveByPNumAndType(anyString(), anyString(), anyInt()))
                .thenReturn(new ArrayList<>());
        when(transferRequestMapper.selectByExample(any())).thenReturn(new ArrayList<>());
        when(productBatchMapper.findNearExpiryBatchesByPNum(anyString(), anyInt()))
                .thenReturn(new ArrayList<>());
        when(productBatchMapper.findAvailableBatches(anyString(), anyString()))
                .thenReturn(new ArrayList<>());

        ProductBatch recentBatch = new ProductBatch();
        recentBatch.setSupplierId(100L);
        when(productBatchMapper.selectByExample(any())).thenReturn(Collections.singletonList(recentBatch));
        when(deliveryStatService.getEstimatedLeadDays(100L, "P001")).thenReturn(7);

        List<ReplenishSuggestionVO> results = suggestionService.generateSuggestionsForProduct("P001");

        assertFalse(results.isEmpty());
        verify(suggestionMapper, atLeastOnce()).insertSelective(any(ReplenishSuggestion.class));
    }

    @Test
    public void testAdopt_TransferSuggestion_Success() {
        ReplenishSuggestion transferSuggestion = new ReplenishSuggestion();
        transferSuggestion.setId(3L);
        transferSuggestion.setSuggestionNum("SUG-003");
        transferSuggestion.setPNum("P001");
        transferSuggestion.setSuggestionType(SuggestionType.TRANSFER);
        transferSuggestion.setRiskLevel(RiskLevel.HIGH);
        transferSuggestion.setSuggestedQuantity(100L);
        transferSuggestion.setFromDepartment("仓库A");
        transferSuggestion.setToDepartment("仓库B");
        transferSuggestion.setReason("测试");
        transferSuggestion.setStatus(SuggestionStatus.PENDING);
        transferSuggestion.setCreateTime(new Date());

        when(suggestionMapper.selectByPrimaryKey(3L)).thenReturn(transferSuggestion);

        suggestionService.adopt(3L);

        // 验证创建了调拨申请
        verify(transferRequestMapper).insertSelective(transferCaptor.capture());
        TransferRequest created = transferCaptor.getValue();
        assertEquals("P001", created.getPNum());
        assertEquals(Long.valueOf(100), created.getTransferQuantity());
        assertEquals("仓库A", created.getFromDepartment());
        assertEquals("仓库B", created.getToDepartment());

        // 验证建议状态更新
        verify(suggestionMapper).updateByPrimaryKeySelective(suggestionCaptor.capture());
        assertEquals(Integer.valueOf(SuggestionStatus.ADOPTED), suggestionCaptor.getValue().getStatus());
        assertNotNull(suggestionCaptor.getValue().getRelatedTransferNum());

        // 验证审计记录
        verify(auditMapper).insertSelective(auditCaptor.capture());
        assertEquals(SuggestionAuditAction.ADOPTED, auditCaptor.getValue().getAction());
    }

    @Test
    public void testAdopt_PurchaseSuggestion_Success() {
        when(suggestionMapper.selectByPrimaryKey(1L)).thenReturn(pendingSuggestion);

        suggestionService.adopt(1L);

        verify(suggestionMapper).updateByPrimaryKeySelective(suggestionCaptor.capture());
        assertEquals(Integer.valueOf(SuggestionStatus.ADOPTED), suggestionCaptor.getValue().getStatus());

        // PURCHASE类型不创建调拨
        verify(transferRequestMapper, never()).insertSelective(any(TransferRequest.class));

        verify(auditMapper).insertSelective(auditCaptor.capture());
        assertEquals(SuggestionAuditAction.ADOPTED, auditCaptor.getValue().getAction());
    }

    @Test(expected = ServiceException.class)
    public void testAdopt_AlreadyAdopted_ThrowsException() {
        ReplenishSuggestion alreadyAdopted = new ReplenishSuggestion();
        alreadyAdopted.setId(5L);
        alreadyAdopted.setStatus(SuggestionStatus.ADOPTED);
        when(suggestionMapper.selectByPrimaryKey(5L)).thenReturn(alreadyAdopted);

        suggestionService.adopt(5L);
    }

    @Test
    public void testReject_Success() {
        when(suggestionMapper.selectByPrimaryKey(1L)).thenReturn(pendingSuggestion);

        suggestionService.reject(1L, "库存已手动补充");

        verify(suggestionMapper).updateByPrimaryKeySelective(suggestionCaptor.capture());
        assertEquals(Integer.valueOf(SuggestionStatus.REJECTED), suggestionCaptor.getValue().getStatus());

        verify(auditMapper).insertSelective(auditCaptor.capture());
        assertEquals(SuggestionAuditAction.REJECTED, auditCaptor.getValue().getAction());
        assertEquals("库存已手动补充", auditCaptor.getValue().getRemark());
    }

    @Test(expected = ServiceException.class)
    public void testReject_AlreadyRejected_ThrowsException() {
        ReplenishSuggestion rejected = new ReplenishSuggestion();
        rejected.setId(6L);
        rejected.setStatus(SuggestionStatus.REJECTED);
        when(suggestionMapper.selectByPrimaryKey(6L)).thenReturn(rejected);

        suggestionService.reject(6L, "test");
    }

    @Test
    public void testOnTransferRejected_UpdatesSuggestionStatus() {
        adoptedSuggestion.setStatus(SuggestionStatus.ADOPTED);
        when(suggestionMapper.findByRelatedTransferNum("TRANSFER-001")).thenReturn(adoptedSuggestion);

        suggestionService.onTransferRejected("TRANSFER-001");

        verify(suggestionMapper).updateByPrimaryKeySelective(suggestionCaptor.capture());
        assertEquals(Integer.valueOf(SuggestionStatus.PENDING), suggestionCaptor.getValue().getStatus());
        assertNull(suggestionCaptor.getValue().getRelatedTransferNum());

        verify(auditMapper).insertSelective(auditCaptor.capture());
        assertEquals(SuggestionAuditAction.TRANSFER_REJECTED, auditCaptor.getValue().getAction());
    }

    @Test
    public void testExpireStaleSuggestions() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);

        // 创建一个超过7*24小时的建议
        ReplenishSuggestion oldSuggestion = new ReplenishSuggestion();
        oldSuggestion.setId(10L);
        oldSuggestion.setSuggestionNum("SUG-OLD");
        oldSuggestion.setStatus(SuggestionStatus.PENDING);
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, -200); // 200小时前 > 168小时(7*24)
        oldSuggestion.setCreateTime(cal.getTime());

        when(suggestionMapper.selectByExample(any())).thenReturn(Collections.singletonList(oldSuggestion));

        suggestionService.expireStaleSuggestions();

        verify(suggestionMapper).updateByPrimaryKeySelective(suggestionCaptor.capture());
        assertEquals(Integer.valueOf(SuggestionStatus.EXPIRED), suggestionCaptor.getValue().getStatus());

        verify(auditMapper).insertSelective(auditCaptor.capture());
        assertEquals(SuggestionAuditAction.EXPIRED, auditCaptor.getValue().getAction());
    }

    @Test
    public void testConcurrentOutbound_TriggersRecalculation() {
        // 模拟在出库后调用calculateRiskForProduct触发重算
        // 验证新快照反映了减少的库存
        // 出库后库存减少到10
        InventoryRiskSnapshot updatedSnapshot = new InventoryRiskSnapshot();
        updatedSnapshot.setId(20L);
        updatedSnapshot.setPNum("P001");
        updatedSnapshot.setRiskLevel(RiskLevel.CRITICAL);
        updatedSnapshot.setAvailableStock(10L);
        updatedSnapshot.setDailyConsumptionRate(new BigDecimal("10.0000"));
        updatedSnapshot.setAvailableDays(new BigDecimal("1.00"));
        when(snapshotMapper.findLatestByPNum("P001")).thenReturn(updatedSnapshot);

        // 验证可以获取到更新后的快照
        InventoryRiskSnapshot latest = snapshotMapper.findLatestByPNum("P001");
        assertNotNull(latest);
        assertEquals(RiskLevel.CRITICAL, latest.getRiskLevel());
        assertEquals(Long.valueOf(10), latest.getAvailableStock());
    }

    @Test
    public void testSuggestionTraceability() {
        SuggestionAudit audit1 = new SuggestionAudit();
        audit1.setId(1L);
        audit1.setSuggestionNum("SUG-001");
        audit1.setAction(SuggestionAuditAction.CREATED);
        audit1.setAfterStatus(SuggestionStatus.PENDING);
        audit1.setEventTime(new Date());

        SuggestionAudit audit2 = new SuggestionAudit();
        audit2.setId(2L);
        audit2.setSuggestionNum("SUG-001");
        audit2.setAction(SuggestionAuditAction.ADOPTED);
        audit2.setBeforeStatus(SuggestionStatus.PENDING);
        audit2.setAfterStatus(SuggestionStatus.ADOPTED);
        audit2.setEventTime(new Date());

        when(auditMapper.findBySuggestionNum("SUG-001")).thenReturn(Arrays.asList(audit1, audit2));

        List<SuggestionAuditVO> trace = suggestionService.getTrace("SUG-001");

        assertEquals(2, trace.size());
        assertEquals(SuggestionAuditAction.CREATED, trace.get(0).getAction());
        assertEquals(SuggestionAuditAction.ADOPTED, trace.get(1).getAction());
    }
}
