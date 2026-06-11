package com.coderman.business.service;

import com.coderman.business.mapper.BatchTraceEventMapper;
import com.coderman.business.mapper.ConsumerMapper;
import com.coderman.business.mapper.ProductBatchMapper;
import com.coderman.business.mapper.ProductMapper;
import com.coderman.business.mapper.SupplierMapper;
import com.coderman.business.service.imp.ProductBatchServiceImpl;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.BatchTraceEvent;
import com.coderman.common.model.business.Consumer;
import com.coderman.common.model.business.Product;
import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.model.business.Supplier;
import com.coderman.common.vo.business.BatchAllocationResultVO;
import com.coderman.common.vo.business.ProductBatchVO;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import tk.mybatis.mapper.entity.Config;
import tk.mybatis.mapper.mapperhelper.EntityHelper;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 物资批次服务测试
 * 覆盖：近效期出库、并发分配、库存锁定释放、追溯查询
 */
@RunWith(MockitoJUnitRunner.class)
public class ProductBatchServiceTest {

    @InjectMocks
    private ProductBatchServiceImpl productBatchService;

    @Mock
    private ProductBatchMapper productBatchMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private SupplierMapper supplierMapper;

    @Mock
    private ConsumerMapper consumerMapper;

    @Mock
    private BatchTraceEventMapper batchTraceEventMapper;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    private ProductBatch batch1;
    private ProductBatch batch2;
    private ProductBatch batch3NearExpiry;
    private Product product;
    private Supplier supplier;

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(ProductBatch.class, config);
        EntityHelper.initEntityNameMap(Product.class, config);
        EntityHelper.initEntityNameMap(Supplier.class, config);
        EntityHelper.initEntityNameMap(Consumer.class, config);
        EntityHelper.initEntityNameMap(BatchTraceEvent.class, config);
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

        batch3NearExpiry = new ProductBatch();
        batch3NearExpiry.setId(3L);
        batch3NearExpiry.setBatchNumber("BATCH-003-NEAR");
        batch3NearExpiry.setPNum("P001");
        batch3NearExpiry.setInNum("IN003");
        batch3NearExpiry.setSupplierId(1L);
        batch3NearExpiry.setProductionDate(dateFormat.parse("2024-06-01"));
        batch3NearExpiry.setExpiryDate(dateFormat.parse("2025-03-01"));
        batch3NearExpiry.setQualityStatus(1);
        batch3NearExpiry.setReserveLevel(1);
        batch3NearExpiry.setQuantity(40L);
        batch3NearExpiry.setLockedQuantity(0L);
        batch3NearExpiry.setStatus(0);
    }

    // ==================== 创建批次 ====================

    @Test
    public void testCreateBatch_Success() {
        when(productBatchMapper.insertSelective(any(ProductBatch.class))).thenReturn(1);
        when(batchTraceEventMapper.insertSelective(any(BatchTraceEvent.class))).thenReturn(1);

        ProductBatch batch = new ProductBatch();
        batch.setBatchNumber("BATCH-TEST");
        batch.setPNum("P001");
        batch.setQuantity(50L);

        ProductBatch result = productBatchService.createBatch(batch);

        assertNotNull(result);
        assertNotNull(result.getCreateTime());
        assertNotNull(result.getModifiedTime());
        assertEquals(Long.valueOf(0L), result.getLockedQuantity());
        verify(productBatchMapper, times(1)).insertSelective(any(ProductBatch.class));
        // 验证记录了IN_STOCK追溯事件
        verify(batchTraceEventMapper, times(1)).insertSelective(any(BatchTraceEvent.class));
    }

    @Test
    public void testCreateBatch_WithRelatedNum() {
        when(productBatchMapper.insertSelective(any(ProductBatch.class))).thenReturn(1);
        when(batchTraceEventMapper.insertSelective(any(BatchTraceEvent.class))).thenReturn(1);

        ProductBatch batch = new ProductBatch();
        batch.setBatchNumber("BATCH-REL");
        batch.setPNum("P001");
        batch.setQuantity(30L);

        productBatchService.createBatch(batch, "IN-20250101-001");

        verify(batchTraceEventMapper).insertSelective(argThat(event ->
                "IN-20250101-001".equals(event.getRelatedNum())
                && "IN_STOCK".equals(event.getEventType())));
    }

    @Test
    public void testCreateBatches_Bulk() {
        when(productBatchMapper.insertSelective(any(ProductBatch.class))).thenReturn(1);
        when(batchTraceEventMapper.insertSelective(any(BatchTraceEvent.class))).thenReturn(1);

        List<ProductBatch> batches = new ArrayList<>();
        batches.add(new ProductBatch());
        batches.add(new ProductBatch());

        productBatchService.createBatches(batches);

        verify(productBatchMapper, times(2)).insertSelective(any(ProductBatch.class));
        verify(batchTraceEventMapper, times(2)).insertSelective(any(BatchTraceEvent.class));
    }

    // ==================== 近效期出库分配 ====================

    @Test
    public void testAllocateBatches_NearExpiryFirst() {
        // 三个批次按近效期排序: batch3NearExpiry(2025-03-01) < batch1(2025-06-01) < batch2(2025-12-01)
        List<ProductBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(batch3NearExpiry); // 40 available, 最近效期
        sortedBatches.add(batch1);           // 60 available
        sortedBatches.add(batch2);           // 200 available

        when(consumerMapper.selectByPrimaryKey(anyLong())).thenReturn(null);
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("expiry_date ASC")))
                .thenReturn(sortedBatches);
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        // 请求80个：batch3只有40，batch1有60可以补40
        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 80L, 1L, "NEAR_EXPIRY");

        assertTrue(result.isFullyAllocated());
        assertEquals(Long.valueOf(80L), result.getAllocatedQuantity());
        assertEquals(2, result.getItems().size());
        // 第一个应该是最近效期的batch3，分配40
        assertEquals("BATCH-003-NEAR", result.getItems().get(0).getBatchNumber());
        assertEquals(Long.valueOf(40L), result.getItems().get(0).getAllocatedQuantity());
        // 第二个是batch1，分配剩余的40
        assertEquals("BATCH-001", result.getItems().get(1).getBatchNumber());
        assertEquals(Long.valueOf(40L), result.getItems().get(1).getAllocatedQuantity());
    }

    @Test
    public void testAllocateBatches_NearExpiry_SkipsFullyLockedBatches() {
        // 模拟batch3NearExpiry被其他调拨完全锁定(locked=40, quantity=40, available=0)
        ProductBatch lockedBatch = new ProductBatch();
        lockedBatch.setId(3L);
        lockedBatch.setBatchNumber("BATCH-003-LOCKED");
        lockedBatch.setPNum("P001");
        lockedBatch.setQuantity(40L);
        lockedBatch.setLockedQuantity(40L); // 完全锁定
        lockedBatch.setStatus(0);
        lockedBatch.setQualityStatus(1);

        List<ProductBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(lockedBatch); // available=0, 应被跳过
        sortedBatches.add(batch1);      // 60 available
        sortedBatches.add(batch2);      // 200 available

        when(consumerMapper.selectByPrimaryKey(anyLong())).thenReturn(null);
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("expiry_date ASC")))
                .thenReturn(sortedBatches);
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 50L, 1L, "NEAR_EXPIRY");

        assertTrue(result.isFullyAllocated());
        // 应该跳过已锁定的批次，直接从batch1分配
        assertEquals(1, result.getItems().size());
        assertEquals("BATCH-001", result.getItems().get(0).getBatchNumber());
        assertEquals(Long.valueOf(50L), result.getItems().get(0).getAllocatedQuantity());
    }

    // ==================== FIFO分配 ====================

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

    // ==================== 隔离点分配 ====================

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
    public void testAllocateBatches_IsolationPoint_LockedBatchNotReallocated() {
        // 隔离点需求不能分配到已被其他调拨锁定的批次
        Consumer isolationConsumer = new Consumer();
        isolationConsumer.setId(2L);
        isolationConsumer.setIsIsolationPoint(1);

        ProductBatch lockedBatch = new ProductBatch();
        lockedBatch.setId(3L);
        lockedBatch.setBatchNumber("BATCH-LOCKED");
        lockedBatch.setPNum("P001");
        lockedBatch.setQuantity(100L);
        lockedBatch.setLockedQuantity(100L); // 全部锁定
        lockedBatch.setReserveLevel(3); // 高储备，本应优先
        lockedBatch.setStatus(0);
        lockedBatch.setQualityStatus(1);

        List<ProductBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(lockedBatch); // 高储备但完全锁定
        sortedBatches.add(batch2);      // reserveLevel=2, 200 available

        when(consumerMapper.selectByPrimaryKey(2L)).thenReturn(isolationConsumer);
        when(productBatchMapper.findAvailableBatchesForIsolationPoint("P001"))
                .thenReturn(sortedBatches);
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 50L, 2L, "NEAR_EXPIRY");

        assertTrue(result.isFullyAllocated());
        // 应该跳过锁定的高储备批次，使用batch2
        assertEquals(1, result.getItems().size());
        assertEquals("BATCH-002", result.getItems().get(0).getBatchNumber());
    }

    // ==================== 库存不足 ====================

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

    // ==================== 锁定 ====================

    @Test
    public void testLockBatches_Success() {
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchMapper.selectByPrimaryKey(2L)).thenReturn(batch2);
        when(productBatchMapper.lockBatchQuantity(eq(1L), eq(50L))).thenReturn(1);
        when(productBatchMapper.lockBatchQuantity(eq(2L), eq(30L))).thenReturn(1);
        when(batchTraceEventMapper.insertSelective(any(BatchTraceEvent.class))).thenReturn(1);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L));
        items.add(new ProductBatchService.BatchLockItem(2L, 30L));

        productBatchService.lockBatches(items, "TRANSFER-001");

        verify(productBatchMapper).lockBatchQuantity(1L, 50L);
        verify(productBatchMapper).lockBatchQuantity(2L, 30L);
        // 验证记录了LOCKED追溯事件
        verify(batchTraceEventMapper, times(2)).insertSelective(argThat(event ->
                "LOCKED".equals(event.getEventType())
                && "TRANSFER-001".equals(event.getRelatedNum())));
    }

    @Test(expected = ServiceException.class)
    public void testLockBatches_InsufficientStock_ThrowsException() {
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchMapper.lockBatchQuantity(eq(1L), eq(50L))).thenReturn(0);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L));

        productBatchService.lockBatches(items);
    }

    // ==================== 并发调拨锁定冲突 ====================

    @Test(expected = ServiceException.class)
    public void testLockBatches_ConcurrentTransfer_LockConflict() {
        // 模拟并发场景：另一个调拨已锁定该批次，本次锁定失败
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchMapper.lockBatchQuantity(eq(1L), eq(60L))).thenReturn(0); // 原子锁定失败

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 60L));

        productBatchService.lockBatches(items, "TRANSFER-CONCURRENT");
    }

    // ==================== 解锁 ====================

    @Test
    public void testUnlockBatches_Success() {
        ProductBatch lockedBatch = new ProductBatch();
        lockedBatch.setId(1L);
        lockedBatch.setBatchNumber("BATCH-001");
        lockedBatch.setPNum("P001");
        lockedBatch.setQuantity(60L);
        lockedBatch.setLockedQuantity(50L);
        lockedBatch.setStatus(0);

        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(lockedBatch);
        when(productBatchMapper.unlockBatchQuantity(eq(1L), eq(50L))).thenReturn(1);
        when(batchTraceEventMapper.insertSelective(any(BatchTraceEvent.class))).thenReturn(1);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L));

        productBatchService.unlockBatches(items, "TRANSFER-ROLLBACK");

        verify(productBatchMapper).unlockBatchQuantity(1L, 50L);
        verify(batchTraceEventMapper).insertSelective(argThat(event ->
                "UNLOCKED".equals(event.getEventType())));
    }

    // ==================== 库存锁定释放安全校验 ====================

    @Test(expected = ServiceException.class)
    public void testUnlockBatches_ExceedsLocked_ThrowsException() {
        // 解锁量超过已锁定量，应抛异常防止locked_quantity为负
        ProductBatch partialLocked = new ProductBatch();
        partialLocked.setId(1L);
        partialLocked.setBatchNumber("BATCH-001");
        partialLocked.setPNum("P001");
        partialLocked.setQuantity(60L);
        partialLocked.setLockedQuantity(20L); // 只锁了20
        partialLocked.setStatus(0);

        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(partialLocked);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L)); // 尝试解锁50

        productBatchService.unlockBatches(items, "TRANSFER-BAD");
    }

    @Test(expected = ServiceException.class)
    public void testUnlockBatches_ZeroLocked_ThrowsException() {
        // 批次没有任何锁定，尝试解锁应失败
        ProductBatch noLock = new ProductBatch();
        noLock.setId(1L);
        noLock.setBatchNumber("BATCH-001");
        noLock.setPNum("P001");
        noLock.setQuantity(60L);
        noLock.setLockedQuantity(0L); // 无锁定
        noLock.setStatus(0);

        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(noLock);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 10L));

        productBatchService.unlockBatches(items);
    }

    // ==================== 确认扣减 ====================

    @Test
    public void testConfirmBatchDeductions_Success() {
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchMapper.confirmBatchDeduction(eq(1L), eq(30L))).thenReturn(1);
        when(batchTraceEventMapper.insertSelective(any(BatchTraceEvent.class))).thenReturn(1);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 30L));

        productBatchService.confirmBatchDeductions(items, "OUT-001");

        verify(productBatchMapper).confirmBatchDeduction(1L, 30L);
        verify(batchTraceEventMapper).insertSelective(argThat(event ->
                "DEDUCTED".equals(event.getEventType())
                && "OUT-001".equals(event.getRelatedNum())));
    }

    @Test(expected = ServiceException.class)
    public void testConfirmBatchDeductions_InsufficientLocked_ThrowsException() {
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchMapper.confirmBatchDeduction(eq(1L), eq(30L))).thenReturn(0);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 30L));

        productBatchService.confirmBatchDeductions(items);
    }

    // ==================== 回滚扣减 ====================

    @Test
    public void testRollbackBatchDeductions_Success() {
        ProductBatch deductedBatch = new ProductBatch();
        deductedBatch.setId(1L);
        deductedBatch.setBatchNumber("BATCH-001");
        deductedBatch.setPNum("P001");
        deductedBatch.setQuantity(30L); // 已扣减后的数量
        deductedBatch.setLockedQuantity(0L);
        deductedBatch.setStatus(0);

        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(deductedBatch);
        when(productBatchMapper.rollbackBatchDeduction(eq(1L), eq(30L))).thenReturn(1);
        when(batchTraceEventMapper.insertSelective(any(BatchTraceEvent.class))).thenReturn(1);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 30L));

        productBatchService.rollbackBatchDeductions(items, "TRANSFER-ROLLBACK");

        verify(productBatchMapper).rollbackBatchDeduction(1L, 30L);
        verify(batchTraceEventMapper).insertSelective(argThat(event ->
                "ROLLBACK".equals(event.getEventType())
                && "TRANSFER-ROLLBACK".equals(event.getRelatedNum())));
    }

    @Test(expected = ServiceException.class)
    public void testRollbackBatchDeductions_BatchNotFound_ThrowsException() {
        when(productBatchMapper.selectByPrimaryKey(999L)).thenReturn(null);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(999L, 30L));

        productBatchService.rollbackBatchDeductions(items, "TRANSFER-ROLLBACK");
    }

    // ==================== 质检状态更新 ====================

    @Test
    public void testUpdateQualityStatus_Pass() {
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchMapper.updateByPrimaryKeySelective(any(ProductBatch.class))).thenReturn(1);
        when(batchTraceEventMapper.insertSelective(any(BatchTraceEvent.class))).thenReturn(1);

        productBatchService.updateQualityStatus(1L, 1); // 合格

        verify(batchTraceEventMapper).insertSelective(argThat(event ->
                "QUALITY_PASS".equals(event.getEventType())));
    }

    @Test
    public void testUpdateQualityStatus_Expired() {
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(batch1);
        when(productBatchMapper.updateByPrimaryKeySelective(any(ProductBatch.class))).thenReturn(1);
        when(batchTraceEventMapper.insertSelective(any(BatchTraceEvent.class))).thenReturn(1);

        productBatchService.updateQualityStatus(1L, 3); // 过期

        verify(batchTraceEventMapper).insertSelective(argThat(event ->
                "EXPIRED".equals(event.getEventType())));
    }

    @Test(expected = ServiceException.class)
    public void testUpdateQualityStatus_BatchNotFound() {
        when(productBatchMapper.selectByPrimaryKey(999L)).thenReturn(null);
        productBatchService.updateQualityStatus(999L, 1);
    }

    // ==================== 追溯查询 ====================

    @Test
    public void testGetTraceability_WithEvents() {
        when(productBatchMapper.findTraceByBatchNumber("BATCH-001")).thenReturn(batch1);
        when(productMapper.selectByExample(any(tk.mybatis.mapper.entity.Example.class)))
                .thenReturn(Collections.singletonList(product));
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        // 模拟追溯事件链
        List<BatchTraceEvent> events = new ArrayList<>();
        BatchTraceEvent e1 = new BatchTraceEvent();
        e1.setEventType("IN_STOCK");
        e1.setBatchNumber("BATCH-001");
        e1.setEventTime(new Date());
        events.add(e1);

        BatchTraceEvent e2 = new BatchTraceEvent();
        e2.setEventType("QUALITY_PASS");
        e2.setBatchNumber("BATCH-001");
        e2.setEventTime(new Date());
        events.add(e2);

        BatchTraceEvent e3 = new BatchTraceEvent();
        e3.setEventType("LOCKED");
        e3.setBatchNumber("BATCH-001");
        e3.setRelatedNum("TRANSFER-001");
        e3.setEventTime(new Date());
        events.add(e3);

        BatchTraceEvent e4 = new BatchTraceEvent();
        e4.setEventType("UNLOCKED");
        e4.setBatchNumber("BATCH-001");
        e4.setRelatedNum("TRANSFER-001");
        e4.setRemark("审批拒绝回滚");
        e4.setEventTime(new Date());
        events.add(e4);

        when(batchTraceEventMapper.findByBatchNumber("BATCH-001")).thenReturn(events);

        ProductBatchVO vo = productBatchService.getTraceability("BATCH-001");

        assertNotNull(vo);
        assertEquals("BATCH-001", vo.getBatchNumber());
        assertEquals("N95口罩", vo.getProductName());
        assertNotNull(vo.getTraceEvents());
        assertEquals(4, vo.getTraceEvents().size());
        assertEquals("IN_STOCK", vo.getTraceEvents().get(0).getEventType());
        assertEquals("QUALITY_PASS", vo.getTraceEvents().get(1).getEventType());
        assertEquals("LOCKED", vo.getTraceEvents().get(2).getEventType());
        assertEquals("UNLOCKED", vo.getTraceEvents().get(3).getEventType());
    }

    @Test
    public void testGetTraceability_NotFound() {
        when(productBatchMapper.findTraceByBatchNumber("NON-EXIST")).thenReturn(null);

        ProductBatchVO vo = productBatchService.getTraceability("NON-EXIST");

        assertNull(vo);
    }

    // ==================== 近效期批次查询 ====================

    @Test
    public void testFindNearExpiryBatches() {
        when(productBatchMapper.findNearExpiryBatches(30))
                .thenReturn(Collections.singletonList(batch3NearExpiry));
        when(productMapper.selectByExample(any(tk.mybatis.mapper.entity.Example.class)))
                .thenReturn(Collections.singletonList(product));
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        List<ProductBatchVO> result = productBatchService.findNearExpiryBatches(30);

        assertEquals(1, result.size());
        assertEquals("BATCH-003-NEAR", result.get(0).getBatchNumber());
    }
}
