package com.coderman.business.service;

import com.coderman.business.mapper.ConsumerMapper;
import com.coderman.business.mapper.ProductBatchMapper;
import com.coderman.business.mapper.ProductMapper;
import com.coderman.business.mapper.SupplierMapper;
import com.coderman.business.service.imp.ProductBatchServiceImpl;
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

        ProductBatch batch = new ProductBatch();
        batch.setBatchNumber("BATCH-TEST");
        batch.setPNum("P001");
        batch.setQuantity(50L);

        ProductBatch result = productBatchService.createBatch(batch);

        assertNotNull(result);
        assertNotNull(result.getCreateTime());
        assertNotNull(result.getModifiedTime());
        verify(productBatchMapper, times(1)).insertSelective(any(ProductBatch.class));
    }

    @Test
    public void testCreateBatches_Bulk() {
        when(productBatchMapper.insertSelective(any(ProductBatch.class))).thenReturn(1);

        List<ProductBatch> batches = new ArrayList<>();
        batches.add(new ProductBatch());
        batches.add(new ProductBatch());

        productBatchService.createBatches(batches);

        verify(productBatchMapper, times(2)).insertSelective(any(ProductBatch.class));
    }

    @Test
    public void testAllocateBatches_NearExpiryFirst() {
        // batch1 近效期(2025-06-01, qty=60), batch2 远效期(2025-12-01, qty=200)
        // 近效期优先排序: batch1在前
        List<ProductBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(batch1); // 60 available
        sortedBatches.add(batch2); // 200 available

        when(consumerMapper.selectByPrimaryKey(anyLong())).thenReturn(null);
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("expiry_date ASC")))
                .thenReturn(sortedBatches);
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        // 请求80个：batch1只有60，需要batch2补20
        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 80L, 1L, "NEAR_EXPIRY");

        assertTrue(result.isFullyAllocated());
        assertEquals(Long.valueOf(80L), result.getAllocatedQuantity());
        assertEquals(2, result.getItems().size());
        // 第一个应该是近效期的batch1，分配60
        assertEquals("BATCH-001", result.getItems().get(0).getBatchNumber());
        assertEquals(Long.valueOf(60L), result.getItems().get(0).getAllocatedQuantity());
        // 第二个是batch2，分配剩余的20
        assertEquals("BATCH-002", result.getItems().get(1).getBatchNumber());
        assertEquals(Long.valueOf(20L), result.getItems().get(1).getAllocatedQuantity());
    }

    @Test
    public void testAllocateBatches_FIFO() {
        // FIFO: batch1(生产日期2025-01-01)在前, batch2(生产日期2025-03-01)在后
        List<ProductBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(batch1);
        sortedBatches.add(batch2);

        when(consumerMapper.selectByPrimaryKey(anyLong())).thenReturn(null);
        when(productBatchMapper.findAvailableBatches(eq("P001"), eq("production_date ASC")))
                .thenReturn(sortedBatches);
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 50L, 1L, "FIFO");

        assertTrue(result.isFullyAllocated());
        // FIFO: batch1(更早生产)优先分配
        assertEquals("BATCH-001", result.getItems().get(0).getBatchNumber());
        assertEquals(Long.valueOf(50L), result.getItems().get(0).getAllocatedQuantity());
    }

    @Test
    public void testAllocateBatches_IsolationPoint_HighReserveFirst() {
        // 隔离点消费者：高储备优先 (batch2 reserveLevel=2 > batch1 reserveLevel=1)
        Consumer isolationConsumer = new Consumer();
        isolationConsumer.setId(2L);
        isolationConsumer.setIsIsolationPoint(1);

        List<ProductBatch> sortedBatches = new ArrayList<>();
        sortedBatches.add(batch2); // reserveLevel=2, 先分配
        sortedBatches.add(batch1); // reserveLevel=1

        when(consumerMapper.selectByPrimaryKey(2L)).thenReturn(isolationConsumer);
        when(productBatchMapper.findAvailableBatchesForIsolationPoint("P001"))
                .thenReturn(sortedBatches);
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        BatchAllocationResultVO result = productBatchService.allocateBatches("P001", 80L, 2L, "NEAR_EXPIRY");

        assertTrue(result.isFullyAllocated());
        // 隔离点应该先分配高储备的batch2
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

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L));
        items.add(new ProductBatchService.BatchLockItem(2L, 30L));

        productBatchService.lockBatches(items);

        verify(productBatchMapper).lockBatchQuantity(1L, 50L);
        verify(productBatchMapper).lockBatchQuantity(2L, 30L);
    }

    @Test(expected = com.coderman.common.exception.ServiceException.class)
    public void testLockBatches_InsufficientStock_ThrowsException() {
        when(productBatchMapper.lockBatchQuantity(eq(1L), eq(50L))).thenReturn(0);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L));

        productBatchService.lockBatches(items);
    }

    @Test
    public void testUnlockBatches_Success() {
        when(productBatchMapper.unlockBatchQuantity(eq(1L), eq(50L))).thenReturn(1);

        List<ProductBatchService.BatchLockItem> items = new ArrayList<>();
        items.add(new ProductBatchService.BatchLockItem(1L, 50L));

        productBatchService.unlockBatches(items);

        verify(productBatchMapper).unlockBatchQuantity(1L, 50L);
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
}
