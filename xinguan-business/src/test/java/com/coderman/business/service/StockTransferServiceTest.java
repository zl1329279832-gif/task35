package com.coderman.business.service;

import com.coderman.business.mapper.*;
import com.coderman.business.service.imp.StockTransferServiceImpl;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.*;
import com.coderman.common.vo.business.StockTransferVO;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.*;
import org.mockito.junit.MockitoJUnitRunner;
import tk.mybatis.mapper.entity.Config;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 调拨服务测试
 * 覆盖: 并发调拨、审批拒绝回滚、库存锁定、异常回滚
 */
@RunWith(MockitoJUnitRunner.class)
public class StockTransferServiceTest {

    @Mock
    private StockTransferMapper stockTransferMapper;

    @Mock
    private StockTransferItemMapper stockTransferItemMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private ProductStockMapper productStockMapper;

    @Mock
    private ProductBatchService productBatchService;

    @Mock
    private ProductBatchMapper productBatchMapper;

    @InjectMocks
    private StockTransferServiceImpl stockTransferService;

    @Captor
    private ArgumentCaptor<StockTransfer> transferCaptor;

    @Captor
    private ArgumentCaptor<ProductStock> stockCaptor;

    @Captor
    private ArgumentCaptor<ProductBatch> batchCaptor;

    private StockTransfer sampleTransfer;
    private StockTransferItem sampleItem;
    private Product sampleProduct;
    private ProductStock sampleStock;

    @Before
    public void setUp() {
        Config config = new Config();
        tk.mybatis.mapper.mapperhelper.EntityHelper.initEntityNameMap(StockTransfer.class, config);
        tk.mybatis.mapper.mapperhelper.EntityHelper.initEntityNameMap(StockTransferItem.class, config);
        tk.mybatis.mapper.mapperhelper.EntityHelper.initEntityNameMap(ProductStock.class, config);
        tk.mybatis.mapper.mapperhelper.EntityHelper.initEntityNameMap(Product.class, config);
        tk.mybatis.mapper.mapperhelper.EntityHelper.initEntityNameMap(ProductBatch.class, config);

        sampleProduct = new Product();
        sampleProduct.setId(1L);
        sampleProduct.setPNum("P001");
        sampleProduct.setName("N95口罩");
        sampleProduct.setStatus(0);

        sampleStock = new ProductStock();
        sampleStock.setId(1L);
        sampleStock.setPNum("P001");
        sampleStock.setStock(1000L);

        sampleTransfer = new StockTransfer();
        sampleTransfer.setId(1L);
        sampleTransfer.setTransferNum("TRANS001");
        sampleTransfer.setFromDepartmentId(1L);
        sampleTransfer.setToDepartmentId(2L);
        sampleTransfer.setStatus(0);
        sampleTransfer.setProductNumber(100);
        sampleTransfer.setOperator("admin");

        sampleItem = new StockTransferItem();
        sampleItem.setId(1L);
        sampleItem.setTransferNum("TRANS001");
        sampleItem.setPNum("P001");
        sampleItem.setBatchNum("BATCH001");
        sampleItem.setQuantity(100);
    }

    // ========== 审批状态校验测试 ==========

    @Test(expected = ServiceException.class)
    public void testApprove_TransferNotFound() {
        when(stockTransferMapper.selectByPrimaryKey(999L)).thenReturn(null);
        stockTransferService.approve(999L, "同意");
    }

    @Test(expected = ServiceException.class)
    public void testApprove_WrongStatus() {
        sampleTransfer.setStatus(1); // 已审批, 不能再审批
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        stockTransferService.approve(1L, "同意");
    }

    @Test(expected = ServiceException.class)
    public void testReject_WrongStatus() {
        sampleTransfer.setStatus(3); // 已锁定, 不能拒绝
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        stockTransferService.reject(1L, "拒绝");
    }

    // ========== 库存锁定测试 ==========

    @Test
    public void testLockStock_WithBatchNum() {
        sampleTransfer.setStatus(1);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        when(stockTransferItemMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleItem));
        when(stockTransferMapper.updateByPrimaryKeySelective(any(StockTransfer.class))).thenReturn(1);
        doNothing().when(productBatchService).lockBatchStock(anyString(), anyLong());

        stockTransferService.lockStock(1L);

        verify(productBatchService).lockBatchStock("BATCH001", 100L);
        verify(stockTransferMapper).updateByPrimaryKeySelective(transferCaptor.capture());
        assertEquals(Integer.valueOf(3), transferCaptor.getValue().getStatus());
    }

    @Test
    public void testLockStock_WithoutBatchNum_AutoAllocate() {
        sampleTransfer.setStatus(1);
        sampleItem.setBatchNum(null);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        when(stockTransferItemMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleItem));
        when(stockTransferMapper.updateByPrimaryKeySelective(any(StockTransfer.class))).thenReturn(1);
        when(stockTransferItemMapper.updateByPrimaryKeySelective(any(StockTransferItem.class))).thenReturn(1);

        ProductBatch allocatedBatch = new ProductBatch();
        allocatedBatch.setBatchNum("AUTO-BATCH");
        allocatedBatch.setBatchStock(100L);
        when(productBatchService.allocateBatches(eq("P001"), eq(100), eq(false)))
                .thenReturn(Collections.singletonList(allocatedBatch));
        doNothing().when(productBatchService).lockBatchStock(anyString(), anyLong());

        stockTransferService.lockStock(1L);

        verify(productBatchService).allocateBatches("P001", 100, false);
        verify(productBatchService).lockBatchStock("AUTO-BATCH", 100L);
    }

    @Test(expected = ServiceException.class)
    public void testLockStock_WrongStatus() {
        sampleTransfer.setStatus(0); // 待审批, 不能锁定
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        stockTransferService.lockStock(1L);
    }

    @Test(expected = ServiceException.class)
    public void testLockStock_EmptyItems() {
        sampleTransfer.setStatus(1);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        when(stockTransferItemMapper.selectByExample(any())).thenReturn(Collections.emptyList());
        stockTransferService.lockStock(1L);
    }

    // ========== 出库确认测试 ==========

    @Test
    public void testConfirmShipment_Success() {
        sampleTransfer.setStatus(3);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        when(stockTransferItemMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleItem));
        when(productStockMapper.selectOneByExample(any())).thenReturn(sampleStock);
        when(productStockMapper.updateByPrimaryKey(any(ProductStock.class))).thenReturn(1);
        when(stockTransferMapper.updateByPrimaryKeySelective(any(StockTransfer.class))).thenReturn(1);
        doNothing().when(productBatchService).deductBatchStock(anyString(), anyLong());

        stockTransferService.confirmShipment(1L);

        verify(productStockMapper).updateByPrimaryKey(stockCaptor.capture());
        assertEquals(Long.valueOf(900L), stockCaptor.getValue().getStock());
        verify(productBatchService).deductBatchStock("BATCH001", 100L);
        verify(stockTransferMapper).updateByPrimaryKeySelective(transferCaptor.capture());
        assertEquals(Integer.valueOf(4), transferCaptor.getValue().getStatus());
    }

    @Test(expected = ServiceException.class)
    public void testConfirmShipment_InsufficientStock() {
        sampleTransfer.setStatus(3);
        sampleStock.setStock(50L);
        sampleItem.setQuantity(100);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        when(stockTransferItemMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleItem));
        when(productStockMapper.selectOneByExample(any())).thenReturn(sampleStock);
        stockTransferService.confirmShipment(1L);
    }

    @Test(expected = ServiceException.class)
    public void testConfirmShipment_WrongStatus() {
        sampleTransfer.setStatus(1); // 已审批但未锁定
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        stockTransferService.confirmShipment(1L);
    }

    // ========== 接收确认测试 ==========

    @Test
    public void testConfirmReceive_Success() {
        sampleTransfer.setStatus(4);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        when(stockTransferMapper.updateByPrimaryKeySelective(any(StockTransfer.class))).thenReturn(1);

        stockTransferService.confirmReceive(1L);

        verify(stockTransferMapper).updateByPrimaryKeySelective(transferCaptor.capture());
        assertEquals(Integer.valueOf(5), transferCaptor.getValue().getStatus());
    }

    @Test(expected = ServiceException.class)
    public void testConfirmReceive_WrongStatus() {
        sampleTransfer.setStatus(3);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        stockTransferService.confirmReceive(1L);
    }

    // ========== 异常回滚测试 ==========

    @Test
    public void testRollback_FromLockedStatus_ReleasesLocks() {
        sampleTransfer.setStatus(3);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        when(stockTransferItemMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleItem));
        when(stockTransferMapper.updateByPrimaryKeySelective(any(StockTransfer.class))).thenReturn(1);
        doNothing().when(productBatchService).unlockBatchStock(anyString(), anyLong());

        stockTransferService.rollback(1L, "物资需求变更");

        verify(productBatchService).unlockBatchStock("BATCH001", 100L);
        verify(stockTransferMapper).updateByPrimaryKeySelective(transferCaptor.capture());
        assertEquals(Integer.valueOf(6), transferCaptor.getValue().getStatus());
    }

    @Test
    public void testRollback_FromShippedStatus_RestoresStock() {
        sampleTransfer.setStatus(4);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        when(stockTransferItemMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleItem));
        when(productStockMapper.selectOneByExample(any())).thenReturn(sampleStock);
        when(productStockMapper.updateByPrimaryKey(any(ProductStock.class))).thenReturn(1);
        when(stockTransferMapper.updateByPrimaryKeySelective(any(StockTransfer.class))).thenReturn(1);

        ProductBatch batch = new ProductBatch();
        batch.setBatchNum("BATCH001");
        batch.setBatchStock(0L);
        batch.setLockedStock(0L);
        when(productBatchMapper.selectOneByExample(any())).thenReturn(batch);
        when(productBatchMapper.updateByPrimaryKeySelective(any(ProductBatch.class))).thenReturn(1);

        stockTransferService.rollback(1L, "运输异常");

        // 验证回补总库存 1000 + 100 = 1100
        verify(productStockMapper).updateByPrimaryKey(stockCaptor.capture());
        assertEquals(Long.valueOf(1100L), stockCaptor.getValue().getStock());

        // 验证回补批次库存 0 + 100 = 100
        verify(productBatchMapper).updateByPrimaryKeySelective(batchCaptor.capture());
        assertEquals(Long.valueOf(100L), batchCaptor.getValue().getBatchStock());
    }

    @Test
    public void testRollback_FromApprovedStatus_NoStockOps() {
        sampleTransfer.setStatus(1);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        when(stockTransferMapper.updateByPrimaryKeySelective(any(StockTransfer.class))).thenReturn(1);

        stockTransferService.rollback(1L, "审批后取消");

        verify(productBatchService, never()).unlockBatchStock(anyString(), anyLong());
        verify(productStockMapper, never()).updateByPrimaryKey(any(ProductStock.class));
        verify(stockTransferMapper).updateByPrimaryKeySelective(transferCaptor.capture());
        assertEquals(Integer.valueOf(6), transferCaptor.getValue().getStatus());
    }

    @Test(expected = ServiceException.class)
    public void testRollback_FromReceivedStatus_Fails() {
        sampleTransfer.setStatus(5);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        stockTransferService.rollback(1L, "尝试回滚");
    }

    @Test(expected = ServiceException.class)
    public void testRollback_FromRejectedStatus_Fails() {
        sampleTransfer.setStatus(2);
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        stockTransferService.rollback(1L, "尝试回滚");
    }

    // ========== 并发调拨测试 ==========

    @Test
    public void testConcurrentLockStock_OnlyOneSucceeds() throws Exception {
        StockTransfer transfer1 = new StockTransfer();
        transfer1.setId(1L);
        transfer1.setTransferNum("T001");
        transfer1.setStatus(1);

        StockTransfer transfer2 = new StockTransfer();
        transfer2.setId(2L);
        transfer2.setTransferNum("T002");
        transfer2.setStatus(1);

        StockTransferItem item1 = new StockTransferItem();
        item1.setTransferNum("T001");
        item1.setPNum("P001");
        item1.setBatchNum("BATCH001");
        item1.setQuantity(80);

        StockTransferItem item2 = new StockTransferItem();
        item2.setTransferNum("T002");
        item2.setPNum("P001");
        item2.setBatchNum("BATCH001");
        item2.setQuantity(80);

        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(transfer1);
        when(stockTransferMapper.selectByPrimaryKey(2L)).thenReturn(transfer2);
        when(stockTransferMapper.updateByPrimaryKeySelective(any(StockTransfer.class))).thenReturn(1);

        when(stockTransferItemMapper.selectByExample(any()))
                .thenReturn(Collections.singletonList(item1))
                .thenReturn(Collections.singletonList(item2));

        // 第一次锁定成功, 第二次抛异常模拟并发冲突
        AtomicInteger lockCallCount = new AtomicInteger(0);
        doAnswer(invocation -> {
            int call = lockCallCount.incrementAndGet();
            if (call > 1) {
                throw new ServiceException("批次库存不足");
            }
            return null;
        }).when(productBatchService).lockBatchStock(eq("BATCH001"), anyLong());

        ExecutorService executor = Executors.newFixedThreadPool(2);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        Future<?> future1 = executor.submit(() -> {
            try {
                stockTransferService.lockStock(1L);
                successCount.incrementAndGet();
            } catch (ServiceException e) {
                failCount.incrementAndGet();
            }
        });

        Future<?> future2 = executor.submit(() -> {
            try {
                stockTransferService.lockStock(2L);
                successCount.incrementAndGet();
            } catch (ServiceException e) {
                failCount.incrementAndGet();
            }
        });

        future1.get(5, TimeUnit.SECONDS);
        future2.get(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertEquals(1, successCount.get());
        assertEquals(1, failCount.get());
    }

    // ========== 调拨单详情测试 ==========

    @Test
    public void testDetail_Success() {
        when(stockTransferMapper.selectByPrimaryKey(1L)).thenReturn(sampleTransfer);
        when(stockTransferItemMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleItem));
        when(productMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleProduct));

        StockTransferVO detail = stockTransferService.detail(1L);

        assertNotNull(detail);
        assertEquals("TRANS001", detail.getTransferNum());
        assertEquals(1, detail.getItems().size());
    }

    @Test(expected = ServiceException.class)
    public void testDetail_NotFound() {
        when(stockTransferMapper.selectByPrimaryKey(999L)).thenReturn(null);
        stockTransferService.detail(999L);
    }
}
