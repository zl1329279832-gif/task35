package com.coderman.business.service;

import com.coderman.business.mapper.ConsumerMapper;
import com.coderman.business.mapper.ProductBatchMapper;
import com.coderman.business.mapper.ProductBatchTraceMapper;
import com.coderman.business.mapper.ProductMapper;
import com.coderman.business.mapper.SupplierMapper;
import com.coderman.business.service.imp.ProductBatchServiceImpl;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.Consumer;
import com.coderman.common.model.business.Product;
import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.model.business.ProductBatchTrace;
import com.coderman.common.model.business.Supplier;
import com.coderman.common.vo.business.BatchAllocationResultVO;
import com.coderman.common.vo.business.ProductBatchTraceVO;
import com.coderman.common.vo.business.ProductBatchVO;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import tk.mybatis.mapper.entity.Config;
import tk.mybatis.mapper.mapperhelper.EntityHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 物资批次服务测试
 */
@RunWith(MockitoJUnitRunner.class)
public class ProductBatchServiceTest {

    @InjectMocks
    private ProductBatchServiceImpl productBatchService;

    @Mock
    private ProductBatchMapper productBatchMapper;

    @Mock
    private ProductBatchTraceMapper productBatchTraceMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private ConsumerMapper consumerMapper;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private ProductBatch batch1;
    private ProductBatch batch2;
    private Product product;
    private Supplier supplier;

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(ProductBatch.class, config);
        EntityHelper.initEntityNameMap(Product.class, config);
        EntityHelper.initEntityNameMap(Supplier.class, config);
        EntityHelper.initEntityNameMap(Consumer.class, config);
        EntityHelper.initEntityNameMap(ProductBatchTrace.class, config);
    }

    @Before
    public void setUp() throws Exception {
        product = new Product();
        product.setId(1L);
        product.setPNum("P001");
        product.setName("N95口罩");

        supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("供应商A");

        batch1 = new ProductBatch();
        batch1.setId(1L);
        batch1.setBatchNumber("BATCH-001");
        batch1.setPNum("P001");
        batch1.setInNum("IN001");
        batch1.setSupplierId(1L);
        batch1.setProductionDate(dateFormat.parse("2025-01-01"));
        batch1.setExpiryDate(dateFormat.parse("2025-06-01"));
        batch1.setQualityStatus(1);
        batch1.setReserveLevel(1);
        batch1.setQuantity(60L);
        batch1.setLockedQuantity(0L);
        batch1.setStatus(0);

        batch2 = new ProductBatch();
        batch2.setId(2L);
        batch2.setBatchNumber("BATCH-002");
        batch2.setPNum("P001");
        batch2.setInNum("IN002");
        batch2.setSupplierId(1L);
        batch2.setProductionDate(dateFormat.parse("2025-03-01"));
        batch2.setExpiryDate(dateFormat.parse("2025-12-01"));
        batch2.setQualityStatus(1);
        batch2.setReserveLevel(2);
        batch2.setQuantity(200L);
        batch2.setLockedQuantity(0L);
        batch2.setStatus(0);
    }

    @Test
    public void testCreateBatch_Success() {
        when(productBatchMapper.insertSelective(any(ProductBatch.class))).thenReturn(1);
        when(productBatchTraceMapper.insertSelective(any(ProductBatchTrace.class))).thenReturn(1);

        ProductBatch batch = new ProductBatch();
        batch.setBatchNumber("BATCH-TEST");
        batch.setPNum("P001");
        batch.setQuantity(50L);
        batch.setInNum("IN-TEST");

        ProductBatch result = productBatchService.createBatch(batch);

        assertNotNull(result);
        assertNotNull(result.getCreateTime());
        assertNotNull(result.getModifiedTime());
        verify(productBatchMapper, times(1)).insertSelective(any(ProductBatch.class));
        // 验证记录了IN追溯事件
        ArgumentCaptor<ProductBatchTrace> traceCaptor = ArgumentCaptor.forClass(ProductBatchTrace.class);
        verify(productBatchTraceMapper).insertSelective(traceCaptor.capture());
        assertEquals("IN", traceCaptor.getValue().getEventType());
        assertEquals("BATCH-TEST", traceCaptor.getValue().getBatchNumber());
    }

    @Test
    public void testCreateBatches_Bulk() {
        when(productBatchMapper.insertSelective(any(ProductBatch.class))).thenReturn(1);
        when(productBatchTraceMapper.insertSelective(any(ProductBatchTrace.class))).thenReturn(1);

        List<ProductBatch> batches = new ArrayList<>();
        ProductBatch b1 = new ProductBatch();
        b1.setBatchNumber("B1");
        b1.setPNum("P001");
        b1.setQuantity(10L);
        batches.add(b1);

        ProductBatch b2 = new ProductBatch();
        b2.setBatchNumber("B2");
        b2.setPNum("P001");
        b2.setQuantity(20L);
        batches.add(b2);

        productBatchService.createBatches(batches);

        verify(productBatchMapper, times(2)).insertSelective(any(ProductBatch.class));
        verify(productBatchTraceMapper, times(2)).insertSelective(any(ProductBatchTrace.class));
    }

    @Test
    public void testAllocateBatches_NearExpiryFirst() {
        // batch1 近效期(2025-06-01, qty=60), batch2 远效期(2025-12-01, qty=200)
        List<ProductBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(batch1);
        sortedBatches.add(batch2);

        when(consumerMapper.selectByPrimaryKey(anyLong())).thenReturn(null);
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("expiry_date ASC")))
                .thenReturn(sortedBatches);
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        // 请求80个：batch1只有60，需要batch2补20
        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 80L, 1L, "NEAR_EXPIRY");

        assertTrue(result.isFullyAllocated());
        assertEquals(Long.valueOf(80L), result.getAllocatedQuantity());
        assertEquals(2, result.getItems().size());
        assertEquals("BATCH-001", result.getItems().get(0).getBatchNumber());
        assertEquals(Long.valueOf(60L), result.getItems().get(0).getAllocatedQuantity());
        assertEquals("BATCH-002", result.getItems().get(1).getBatchNumber());
        assertEquals(Long.valueOf(20L), result.getItems().get(1).getAllocatedQuantity());
    }

    @Test
    public void testAllocateBatches_NearExpirySkipsLockedBatches() {
        // batch1 已锁定30，可用只有30；batch2 200可用
        ProductBatch lockedBatch1 = new ProductBatch();
        lockedBatch1.setId(1L);
        lockedBatch1.setBatchNumber("BATCH-001");
        lockedBatch1.setPNum("P001");
        lockedBatch1.setSupplierId(1L);
        lockedBatch1.setQuantity(60L);
        lockedBatch1.setLockedQuantity(30L); // 已锁定30，可用30
        lockedBatch1.setExpiryDate(batch1.getExpiryDate());
        lockedBatch1.setReserveLevel(1);
        lockedBatch1.setStatus(0);

        List<ProductBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(lockedBatch1); // 可用30
        sortedBatches.add(batch2);       // 可用200

        when(consumerMapper.selectByPrimaryKey(anyLong())).thenReturn(null);
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("expiry_date ASC")))
                .thenReturn(sortedBatches);
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        // 请求50个：lockedBatch1可用30，batch2补20
        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 50L, 1L, "NEAR_EXPIRY");

        assertTrue(result.isFullyAllocated());
        assertEquals(Long.valueOf(50L), result.getAllocatedQuantity());
        assertEquals(2, result.getItems().size());
        // 近效期的batch1只能分配30（60-30锁定=30可用）
        assertEquals("BATCH-001", result.getItems().get(0).getBatchNumber());
        assertEquals(Long.valueOf(30L), result.getItems().get(0).getAllocatedQuantity());
        // batch2补剩余20
        assertEquals("BATCH-002", result.getItems().get(1).getBatchNumber());
        assertEquals(Long.valueOf(20L), result.getItems().get(1).getAllocatedQuantity());
    }

    @Test
    public void testAllocateBatches_FIFO() {
        List<ProductBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(batch1);
        sortedBatches.add(batch2);

        when(consumerMapper.selectByPrimaryKey(anyLong())).thenReturn(null);
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("production_date ASC")))
                .thenReturn(sortedBatches);
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 50L, 1L, "FIFO");

        assertTrue(result.isFullyAllocated());
        assertEquals("BATCH-001", result.getItems().get(0).getBatchNumber());
        assertEquals(Long.valueOf(50L), result.getItems().get(0).getAllocatedQuantity());
    }

    @Test
    public void testAllocateBatches_IsolationPoint_HighReserveFirst() {
        Consumer isolationConsumer = new Consumer();
        isolationConsumer.setId(2L);
        isolationConsumer.setIsIsolationPoint(1);

        List<ProductBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(batch2); // reserveLevel=2
        sortedBatches.add(batch1); // reserveLevel=1

        when(consumerMapper.selectByPrimaryKey(2L)).thenReturn(isolationConsumer);
        when(productBatchMapper.findAvailableBatchesForIsolationPoint("P001"))
                .thenReturn(sortedBatches);
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 80L, 2L, "NEAR_EXPIRY");

        assertTrue(result.isFullyAllocated());
        assertEquals("BATCH-002", result.getItems().get(0).getBatchNumber());
        assertEquals(Long.valueOf(80L), result.getItems().get(0).getAllocatedQuantity());
    }

    @Test
    public void testAllocateBatches_InsufficientStock() {
        when(consumerMapper.selectByPrimaryKey(anyLong())).thenReturn(null);
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("expiry_date ASC")))
                .thenReturn(Collections.emptyList());

        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 500L, 1L, "NEAR_EXPIRY");

        assertFalse(result.isFullyAllocated());
        assertEquals(Long.valueOf(0L), result.getAllocatedQuantity());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    public void testLockBatches_Success() {
        when(productBatchMapper.lockBatchQuantity(eq(1L), eq(50L))).thenReturn(1);
        when(productBatchMapper.lockBatchQuantity(eq(2L), eq(30L))).thenReturn(1);
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchMapper.selectByPrimaryKey(2L)).thenReturn(batch2);
        when(productBatchTraceMapper.insertSelective(any(ProductBatchTrace.class))).thenReturn(1);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L));
        items.add(new ProductBatchService.BatchLockItem(2L, 30L));

        productBatchService.lockBatches(items);

        verify(productBatchMapper).lockBatchQuantity(1L, 50L);
        verify(productBatchMapper).lockBatchQuantity(2L, 30L);
        // 验证记录了LOCK追溯事件
        ArgumentCaptor<ProductBatchTrace> traceCaptor = ArgumentCaptor.forClass(ProductBatchTrace.class);
        verify(productBatchTraceMapper, times(2)).insertSelective(traceCaptor.capture());
        List<ProductBatchTrace> traces = traceCaptor.getAllValues();
        assertEquals("LOCK", traces.get(0).getEventType());
        assertEquals("LOCK", traces.get(1).getEventType());
    }

    @Test(expected = ServiceException.class)
    public void testLockBatches_InsufficientStock_ThrowsException() {
        when(productBatchMapper.lockBatchQuantity(eq(1L), eq(50L))).thenReturn(0);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L));

        productBatchService.lockBatches(items);
    }

    @Test
    public void testUnlockBatches_Success() {
        when(productBatchMapper.unlockBatchQuantity(eq(1L), eq(50L))).thenReturn(1);
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchTraceMapper.insertSelective(any(ProductBatchTrace.class))).thenReturn(1);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L));

        productBatchService.unlockBatches(items);

        verify(productBatchMapper).unlockBatchQuantity(1L, 50L);
        // 验证记录了UNLOCK追溯事件
        ArgumentCaptor<ProductBatchTrace> traceCaptor = ArgumentCaptor.forClass(ProductBatchTrace.class);
        verify(productBatchTraceMapper).insertSelective(traceCaptor.capture());
        assertEquals("UNLOCK", traceCaptor.getValue().getEventType());
        assertEquals(Long.valueOf(50L), traceCaptor.getValue().getQuantity());
    }

    @Test
    public void testUnlockBatches_FailedUnlock_ThrowsException() {
        when(productBatchMapper.unlockBatchQuantity(eq(1L), eq(50L))).thenReturn(0);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L));

        try {
            productBatchService.unlockBatches(items);
            fail("Should have thrown ServiceException");
        } catch (ServiceException e) {
            // 验证没有记录追溯事件（操作失败不应记录）
            verify(productBatchTraceMapper, never()).insertSelective(any(ProductBatchTrace.class));
        }
    }

    @Test
    public void testRestoreBatchQuantities_Success() {
        when(productBatchMapper.restoreBatchQuantity(eq(1L), eq(30L))).thenReturn(1);
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchTraceMapper.insertSelective(any(ProductBatchTrace.class))).thenReturn(1);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 30L));

        productBatchService.restoreBatchQuantities(items);

        verify(productBatchMapper).restoreBatchQuantity(1L, 30L);
        // 验证记录了ROLLBACK追溯事件
        ArgumentCaptor<ProductBatchTrace> traceCaptor = ArgumentCaptor.forClass(ProductBatchTrace.class);
        verify(productBatchTraceMapper).insertSelective(traceCaptor.capture());
        assertEquals("ROLLBACK", traceCaptor.getValue().getEventType());
        assertEquals(Long.valueOf(30L), traceCaptor.getValue().getQuantity());
    }

    @Test(expected = ServiceException.class)
    public void testRestoreBatchQuantities_NotFound_ThrowsException() {
        when(productBatchMapper.restoreBatchQuantity(eq(99L), eq(30L))).thenReturn(0);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(99L, 30L));

        productBatchService.restoreBatchQuantities(items);
    }

    @Test
    public void testGetFullTraceability_ReturnsChain() {
        List<ProductBatchTrace> traces = new ArrayList<>();

        ProductBatchTrace inTrace = new ProductBatchTrace();
        inTrace.setId(1L);
        inTrace.setBatchNumber("BATCH-001");
        inTrace.setPNum("P001");
        inTrace.setEventType("IN");
        inTrace.setQuantity(100L);
        inTrace.setRefNum("IN001");
        inTrace.setRemark("批次入库");
        inTrace.setCreateTime(new Date());
        traces.add(inTrace);

        ProductBatchTrace qcTrace = new ProductBatchTrace();
        qcTrace.setId(2L);
        qcTrace.setBatchNumber("BATCH-001");
        qcTrace.setPNum("P001");
        qcTrace.setEventType("QC");
        qcTrace.setQuantity(100L);
        qcTrace.setRemark("质检通过");
        qcTrace.setCreateTime(new Date());
        traces.add(qcTrace);

        ProductBatchTrace lockTrace = new ProductBatchTrace();
        lockTrace.setId(3L);
        lockTrace.setBatchNumber("BATCH-001");
        lockTrace.setPNum("P001");
        lockTrace.setEventType("LOCK");
        lockTrace.setQuantity(30L);
        lockTrace.setRemark("批次库存锁定");
        lockTrace.setCreateTime(new Date());
        traces.add(lockTrace);

        ProductBatchTrace outTrace = new ProductBatchTrace();
        outTrace.setId(4L);
        outTrace.setBatchNumber("BATCH-001");
        outTrace.setPNum("P001");
        outTrace.setEventType("OUT");
        outTrace.setQuantity(30L);
        outTrace.setRefNum("TRANSFER001");
        outTrace.setRemark("调拨出库");
        outTrace.setCreateTime(new Date());
        traces.add(outTrace);

        ProductBatchTrace rollbackTrace = new ProductBatchTrace();
        rollbackTrace.setId(5L);
        rollbackTrace.setBatchNumber("BATCH-001");
        rollbackTrace.setPNum("P001");
        rollbackTrace.setEventType("ROLLBACK");
        rollbackTrace.setQuantity(30L);
        rollbackTrace.setRefNum("TRANSFER001");
        rollbackTrace.setRemark("调拨出库回滚");
        rollbackTrace.setCreateTime(new Date());
        traces.add(rollbackTrace);

        when(productBatchTraceMapper.findByBatchNumber("BATCH-001")).thenReturn(traces);

        List<ProductBatchTraceVO> result = productBatchService.getFullTraceability("BATCH-001");

        assertNotNull(result);
        assertEquals(5, result.size());
        // 验证事件顺序: IN -> QC -> LOCK -> OUT -> ROLLBACK
        assertEquals("IN", result.get(0).getEventType());
        assertEquals("QC", result.get(1).getEventType());
        assertEquals("LOCK", result.get(2).getEventType());
        assertEquals("OUT", result.get(3).getEventType());
        assertEquals("ROLLBACK", result.get(4).getEventType());
        // 验证关联单号
        assertEquals("IN001", result.get(0).getRefNum());
        assertEquals("TRANSFER001", result.get(3).getRefNum());
    }

    @Test
    public void testGetFullTraceability_EmptyForNonExistent() {
        when(productBatchTraceMapper.findByBatchNumber("NON-EXIST")).thenReturn(Collections.emptyList());

        List<ProductBatchTraceVO> result = productBatchService.getFullTraceability("NON-EXIST");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetTraceability_Success() {
        when(productBatchMapper.findTraceByBatchNumber("BATCH-001")).thenReturn(batch1);
        when(productMapper.selectByExample(any(tk.mybatis.mapper.entity.Example.class)))
                .thenReturn(Collections.singletonList(product));
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        ProductBatchVO vo = productBatchService.getTraceability("BATCH-001");

        assertNotNull(vo);
        assertEquals("BATCH-001", vo.getBatchNumber());
        assertEquals("P001", vo.getPNum());
        assertEquals("N95口罩", vo.getProductName());
        assertEquals("供应商A", vo.getSupplierName());
        assertEquals(Long.valueOf(60L), vo.getAvailableQuantity());
    }

    @Test
    public void testGetTraceability_NotFound() {
        when(productBatchMapper.findTraceByBatchNumber("NON-EXIST")).thenReturn(null);

        ProductBatchVO vo = productBatchService.getTraceability("NON-EXIST");

        assertNull(vo);
    }

    @Test
    public void testConfirmBatchDeductions_RecordsTraceEvents() {
        when(productBatchMapper.confirmBatchDeduction(eq(1L), eq(30L))).thenReturn(1);
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchTraceMapper.insertSelective(any(ProductBatchTrace.class))).thenReturn(1);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 30L));

        productBatchService.confirmBatchDeductions(items);

        verify(productBatchMapper).confirmBatchDeduction(1L, 30L);
        ArgumentCaptor<ProductBatchTrace> traceCaptor = ArgumentCaptor.forClass(ProductBatchTrace.class);
        verify(productBatchTraceMapper).insertSelective(traceCaptor.capture());
        assertEquals("OUT", traceCaptor.getValue().getEventType());
        assertEquals(Long.valueOf(30L), traceCaptor.getValue().getQuantity());
    }
}
