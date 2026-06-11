package com.coderman.business.service;

import com.coderman.business.mapper.ProductBatchMapper;
import com.coderman.business.service.imp.ProductBatchServiceImpl;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.ProductBatch;
import com.coderman.common.vo.business.ProductBatchVO;
import com.coderman.common.vo.business.TraceVO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import tk.mybatis.mapper.entity.Config;

import java.util.*;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 批次服务测试
 * 覆盖: 批次入库、近效期出库分配、库存锁定、追溯查询
 */
@RunWith(MockitoJUnitRunner.class)
public class ProductBatchServiceTest {

    @Mock
    private ProductBatchMapper productBatchMapper;

    @InjectMocks
    private ProductBatchServiceImpl productBatchService;

    @Captor
    private ArgumentCaptor<ProductBatch> batchCaptor;

    private ProductBatch sampleBatch;

    @Before
    public void setUp() {
        Config config = new Config();
        tk.mybatis.mapper.mapperhelper.EntityHelper.initEntityNameMap(ProductBatch.class, config);

        sampleBatch = new ProductBatch();
        sampleBatch.setId(1L);
        sampleBatch.setPNum("P001");
        sampleBatch.setBatchNum("BATCH-2024-001");
        sampleBatch.setSupplierId(1L);
        sampleBatch.setProductionDate(new Date());
        sampleBatch.setExpiryDate(addDays(new Date(), 30));
        sampleBatch.setInspectionStatus(1);
        sampleBatch.setReserveLevel(1);
        sampleBatch.setBatchStock(100L);
        sampleBatch.setLockedStock(0L);
        sampleBatch.setInNum("IN001");
    }

    // ========== 批次入库测试 ==========

    @Test
    public void testAddBatch_Success() {
        when(productBatchMapper.insert(any(ProductBatch.class))).thenReturn(1);

        ProductBatch batch = new ProductBatch();
        batch.setPNum("P001");
        batch.setBatchNum("BATCH-NEW");
        batch.setSupplierId(1L);
        batch.setBatchStock(50L);

        productBatchService.addBatch(batch);

        verify(productBatchMapper, times(1)).insert(any(ProductBatch.class));
        assertNotNull(batch.getCreateTime());
        assertNotNull(batch.getModifiedTime());
        assertEquals(Long.valueOf(0L), batch.getLockedStock());
        assertEquals(Integer.valueOf(0), batch.getInspectionStatus());
        assertEquals(Integer.valueOf(1), batch.getReserveLevel());
    }

    @Test
    public void testAddBatch_WithExistingValues() {
        when(productBatchMapper.insert(any(ProductBatch.class))).thenReturn(1);

        ProductBatch batch = new ProductBatch();
        batch.setPNum("P001");
        batch.setBatchNum("BATCH-NEW");
        batch.setInspectionStatus(1);
        batch.setReserveLevel(3);
        batch.setLockedStock(10L);
        batch.setBatchStock(50L);

        productBatchService.addBatch(batch);

        assertEquals(Integer.valueOf(1), batch.getInspectionStatus());
        assertEquals(Integer.valueOf(3), batch.getReserveLevel());
        assertEquals(Long.valueOf(10L), batch.getLockedStock());
    }

    // ========== 近效期出库分配测试 ==========

    @Test
    public void testAllocateBatches_FIFO_NearExpiryPriority() {
        ProductBatch batch1 = createBatch(1L, "B001", 60, 50L, 0L, 1);
        ProductBatch batch2 = createBatch(2L, "B002", 10, 30L, 0L, 1);
        ProductBatch batch3 = createBatch(3L, "B003", 90, 40L, 0L, 1);

        List<ProductBatch> availableBatches = Arrays.asList(batch2, batch1, batch3);
        when(productBatchMapper.findAvailableBatchesFIFO("P001")).thenReturn(availableBatches);

        List<ProductBatch> allocated = productBatchService.allocateBatches("P001", 40, false);

        assertEquals(2, allocated.size());
        assertEquals("B002", allocated.get(0).getBatchNum());
        assertEquals(Long.valueOf(30L), allocated.get(0).getBatchStock());
        assertEquals("B001", allocated.get(1).getBatchNum());
        assertEquals(Long.valueOf(10L), allocated.get(1).getBatchStock());
    }

    @Test
    public void testAllocateBatches_IsolationPriority() {
        ProductBatch normalBatch = createBatch(1L, "B001", 10, 50L, 0L, 1);
        ProductBatch urgentBatch = createBatch(2L, "B002", 30, 30L, 0L, 3);
        ProductBatch importantBatch = createBatch(3L, "B003", 20, 40L, 0L, 2);

        List<ProductBatch> availableBatches = Arrays.asList(normalBatch, urgentBatch, importantBatch);
        when(productBatchMapper.findAvailableBatchesFIFO("P001")).thenReturn(availableBatches);

        List<ProductBatch> allocated = productBatchService.allocateBatches("P001", 25, true);

        assertEquals(1, allocated.size());
        assertEquals("B002", allocated.get(0).getBatchNum());
        assertEquals(Long.valueOf(25L), allocated.get(0).getBatchStock());
    }

    @Test(expected = ServiceException.class)
    public void testAllocateBatches_StockNotEnough() {
        ProductBatch batch = createBatch(1L, "B001", 30, 10L, 0L, 1);
        when(productBatchMapper.findAvailableBatchesFIFO("P001")).thenReturn(Collections.singletonList(batch));

        productBatchService.allocateBatches("P001", 20, false);
    }

    @Test(expected = ServiceException.class)
    public void testAllocateBatches_NoBatchesAvailable() {
        when(productBatchMapper.findAvailableBatchesFIFO("P001")).thenReturn(Collections.emptyList());

        productBatchService.allocateBatches("P001", 10, false);
    }

    @Test
    public void testAllocateBatches_SkipLockedStock() {
        ProductBatch batch = createBatch(1L, "B001", 30, 50L, 30L, 1);
        when(productBatchMapper.findAvailableBatchesFIFO("P001")).thenReturn(Collections.singletonList(batch));

        List<ProductBatch> allocated = productBatchService.allocateBatches("P001", 15, false);

        assertEquals(1, allocated.size());
        assertEquals(Long.valueOf(15L), allocated.get(0).getBatchStock());
    }

    // ========== 库存锁定测试 ==========

    @Test
    public void testLockBatchStock_Success() {
        when(productBatchMapper.selectOneByExample(any())).thenReturn(sampleBatch);
        when(productBatchMapper.updateByPrimaryKeySelective(any(ProductBatch.class))).thenReturn(1);

        productBatchService.lockBatchStock("BATCH-2024-001", 30L);

        verify(productBatchMapper).updateByPrimaryKeySelective(batchCaptor.capture());
        assertEquals(Long.valueOf(30L), batchCaptor.getValue().getLockedStock());
    }

    @Test(expected = ServiceException.class)
    public void testLockBatchStock_InsufficientAvailable() {
        sampleBatch.setLockedStock(90L);
        when(productBatchMapper.selectOneByExample(any())).thenReturn(sampleBatch);

        productBatchService.lockBatchStock("BATCH-2024-001", 20L);
    }

    @Test(expected = ServiceException.class)
    public void testLockBatchStock_BatchNotFound() {
        when(productBatchMapper.selectOneByExample(any())).thenReturn(null);

        productBatchService.lockBatchStock("NOT-EXIST", 10L);
    }

    @Test
    public void testUnlockBatchStock_Success() {
        sampleBatch.setLockedStock(50L);
        when(productBatchMapper.selectOneByExample(any())).thenReturn(sampleBatch);
        when(productBatchMapper.updateByPrimaryKeySelective(any(ProductBatch.class))).thenReturn(1);

        productBatchService.unlockBatchStock("BATCH-2024-001", 30L);

        verify(productBatchMapper).updateByPrimaryKeySelective(batchCaptor.capture());
        assertEquals(Long.valueOf(20L), batchCaptor.getValue().getLockedStock());
    }

    @Test
    public void testUnlockBatchStock_PreventNegative() {
        sampleBatch.setLockedStock(10L);
        when(productBatchMapper.selectOneByExample(any())).thenReturn(sampleBatch);
        when(productBatchMapper.updateByPrimaryKeySelective(any(ProductBatch.class))).thenReturn(1);

        productBatchService.unlockBatchStock("BATCH-2024-001", 20L);

        verify(productBatchMapper).updateByPrimaryKeySelective(batchCaptor.capture());
        assertEquals(Long.valueOf(0L), batchCaptor.getValue().getLockedStock());
    }

    // ========== 批次库存扣减测试 ==========

    @Test
    public void testDeductBatchStock_Success() {
        sampleBatch.setLockedStock(30L);
        when(productBatchMapper.selectOneByExample(any())).thenReturn(sampleBatch);
        when(productBatchMapper.updateByPrimaryKeySelective(any(ProductBatch.class))).thenReturn(1);

        productBatchService.deductBatchStock("BATCH-2024-001", 20L);

        verify(productBatchMapper).updateByPrimaryKeySelective(batchCaptor.capture());
        ProductBatch captured = batchCaptor.getValue();
        assertEquals(Long.valueOf(80L), captured.getBatchStock());
        assertEquals(Long.valueOf(10L), captured.getLockedStock());
    }

    @Test(expected = ServiceException.class)
    public void testDeductBatchStock_InsufficientStock() {
        sampleBatch.setBatchStock(10L);
        when(productBatchMapper.selectOneByExample(any())).thenReturn(sampleBatch);

        productBatchService.deductBatchStock("BATCH-2024-001", 20L);
    }

    // ========== 追溯查询测试 ==========

    @Test
    public void testTraceBatch_Success() {
        TraceVO trace = new TraceVO();
        trace.setBatchNum("BATCH-2024-001");
        trace.setPNum("P001");
        trace.setProductName("N95口罩");
        trace.setSupplierName("某某供应商");
        trace.setInspectionStatus(1);

        when(productBatchMapper.traceBatch("BATCH-2024-001")).thenReturn(trace);

        TraceVO result = productBatchService.traceBatch("BATCH-2024-001");

        assertNotNull(result);
        assertEquals("BATCH-2024-001", result.getBatchNum());
        assertEquals("P001", result.getPNum());
        assertEquals("N95口罩", result.getProductName());
        assertEquals("某某供应商", result.getSupplierName());
    }

    @Test(expected = ServiceException.class)
    public void testTraceBatch_NotFound() {
        when(productBatchMapper.traceBatch("NOT-EXIST")).thenReturn(null);

        productBatchService.traceBatch("NOT-EXIST");
    }

    // ========== 质检状态更新测试 ==========

    @Test
    public void testUpdateInspectionStatus_Success() {
        when(productBatchMapper.selectByPrimaryKey(1L)).thenReturn(sampleBatch);
        when(productBatchMapper.updateByPrimaryKeySelective(any(ProductBatch.class))).thenReturn(1);

        productBatchService.updateInspectionStatus(1L, 2);

        verify(productBatchMapper).updateByPrimaryKeySelective(batchCaptor.capture());
        assertEquals(Integer.valueOf(2), batchCaptor.getValue().getInspectionStatus());
    }

    @Test(expected = ServiceException.class)
    public void testUpdateInspectionStatus_BatchNotFound() {
        when(productBatchMapper.selectByPrimaryKey(999L)).thenReturn(null);

        productBatchService.updateInspectionStatus(999L, 1);
    }

    // ========== Helper ==========

    private ProductBatch createBatch(Long id, String batchNum, int daysToExpiry, Long stock, Long locked, int reserveLevel) {
        ProductBatch batch = new ProductBatch();
        batch.setId(id);
        batch.setPNum("P001");
        batch.setBatchNum(batchNum);
        batch.setExpiryDate(addDays(new Date(), daysToExpiry));
        batch.setInspectionStatus(1);
        batch.setReserveLevel(reserveLevel);
        batch.setBatchStock(stock);
        batch.setLockedStock(locked);
        return batch;
    }

    private Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }
}
