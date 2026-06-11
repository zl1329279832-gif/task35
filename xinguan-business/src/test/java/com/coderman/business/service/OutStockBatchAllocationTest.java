package com.coderman.business.service;

import com.coderman.business.converter.OutStockConverter;
import com.coderman.business.mapper.*;
import com.coderman.business.service.imp.OutStockServiceImpl;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.*;
import com.coderman.common.vo.business.OutStockVO;
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
 * 出库批次分配测试
 * 覆盖: 近效期出库分配、隔离点优先出库
 */
@RunWith(MockitoJUnitRunner.class)
public class OutStockBatchAllocationTest {

    @Mock
    private OutStockMapper outStockMapper;

    @Mock
    private OutStockConverter outStockConverter;

    @Mock
    private ConsumerMapper consumerMapper;

    @Mock
    private ProductMapper productMapper;

    @Mock
    private OutStockInfoMapper outStockInfoMapper;

    @Mock
    private ProductStockMapper productStockMapper;

    @Mock
    private ProductBatchService productBatchService;

    @InjectMocks
    private OutStockServiceImpl outStockService;

    @Captor
    private ArgumentCaptor<ProductStock> stockCaptor;

    private OutStock sampleOutStock;
    private Consumer sampleConsumer;
    private Product sampleProduct;
    private ProductStock sampleProductStock;
    private OutStockInfo sampleOutStockInfo;

    @Before
    public void setUp() {
        Config config = new Config();
        tk.mybatis.mapper.mapperhelper.EntityHelper.initEntityNameMap(OutStock.class, config);
        tk.mybatis.mapper.mapperhelper.EntityHelper.initEntityNameMap(OutStockInfo.class, config);
        tk.mybatis.mapper.mapperhelper.EntityHelper.initEntityNameMap(Product.class, config);
        tk.mybatis.mapper.mapperhelper.EntityHelper.initEntityNameMap(ProductStock.class, config);

        sampleOutStock = new OutStock();
        sampleOutStock.setId(1L);
        sampleOutStock.setOutNum("OUT001");
        sampleOutStock.setStatus(2);
        sampleOutStock.setConsumerId(1L);
        sampleOutStock.setPriority(0);

        sampleConsumer = new Consumer();
        sampleConsumer.setId(1L);
        sampleConsumer.setName("社区医院");

        sampleProduct = new Product();
        sampleProduct.setId(1L);
        sampleProduct.setPNum("P001");
        sampleProduct.setName("N95口罩");

        sampleProductStock = new ProductStock();
        sampleProductStock.setId(1L);
        sampleProductStock.setPNum("P001");
        sampleProductStock.setStock(500L);

        sampleOutStockInfo = new OutStockInfo();
        sampleOutStockInfo.setId(1L);
        sampleOutStockInfo.setOutNum("OUT001");
        sampleOutStockInfo.setPNum("P001");
        sampleOutStockInfo.setProductNumber(100);
    }

    // ========== 近效期出库分配测试 ==========

    @Test
    public void testPublish_NearExpiryBatchAllocation() {
        when(outStockMapper.selectByPrimaryKey(1L)).thenReturn(sampleOutStock);
        when(consumerMapper.selectByPrimaryKey(1L)).thenReturn(sampleConsumer);
        when(outStockInfoMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleOutStockInfo));
        when(productMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleProduct));
        when(productStockMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleProductStock));
        when(productStockMapper.updateByPrimaryKey(any(ProductStock.class))).thenReturn(1);
        when(outStockMapper.updateByPrimaryKeySelective(any(OutStock.class))).thenReturn(1);

        ProductBatch nearExpiryBatch = new ProductBatch();
        nearExpiryBatch.setBatchNum("BATCH-NEAR-EXPIRY");
        nearExpiryBatch.setBatchStock(100L);
        nearExpiryBatch.setExpiryDate(addDays(new Date(), 5));

        when(productBatchService.allocateBatches(eq("P001"), eq(100), eq(false)))
                .thenReturn(Collections.singletonList(nearExpiryBatch));
        doNothing().when(productBatchService).deductBatchStock(anyString(), anyLong());

        outStockService.publish(1L);

        verify(productBatchService).allocateBatches("P001", 100, false);
        verify(productBatchService).deductBatchStock("BATCH-NEAR-EXPIRY", 100L);
        verify(productStockMapper).updateByPrimaryKey(stockCaptor.capture());
        assertEquals(Long.valueOf(400L), stockCaptor.getValue().getStock());
    }

    // ========== 隔离点需求优先出库测试 ==========

    @Test
    public void testPublish_IsolationPriority() {
        sampleOutStock.setPriority(1);
        when(outStockMapper.selectByPrimaryKey(1L)).thenReturn(sampleOutStock);
        when(consumerMapper.selectByPrimaryKey(1L)).thenReturn(sampleConsumer);
        when(outStockInfoMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleOutStockInfo));
        when(productMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleProduct));
        when(productStockMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleProductStock));
        when(productStockMapper.updateByPrimaryKey(any(ProductStock.class))).thenReturn(1);
        when(outStockMapper.updateByPrimaryKeySelective(any(OutStock.class))).thenReturn(1);

        ProductBatch urgentBatch = new ProductBatch();
        urgentBatch.setBatchNum("BATCH-URGENT");
        urgentBatch.setBatchStock(100L);

        when(productBatchService.allocateBatches(eq("P001"), eq(100), eq(true)))
                .thenReturn(Collections.singletonList(urgentBatch));
        doNothing().when(productBatchService).deductBatchStock(anyString(), anyLong());

        outStockService.publish(1L);

        verify(productBatchService).allocateBatches("P001", 100, true);
        verify(productBatchService).deductBatchStock("BATCH-URGENT", 100L);
    }

    // ========== 批次分配失败降级测试 ==========

    @Test
    public void testPublish_BatchAllocationFallback() {
        when(outStockMapper.selectByPrimaryKey(1L)).thenReturn(sampleOutStock);
        when(consumerMapper.selectByPrimaryKey(1L)).thenReturn(sampleConsumer);
        when(outStockInfoMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleOutStockInfo));
        when(productMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleProduct));
        when(productStockMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleProductStock));
        when(productStockMapper.updateByPrimaryKey(any(ProductStock.class))).thenReturn(1);
        when(outStockMapper.updateByPrimaryKeySelective(any(OutStock.class))).thenReturn(1);

        when(productBatchService.allocateBatches(anyString(), anyInt(), anyBoolean()))
                .thenThrow(new ServiceException("批次库存不足"));

        outStockService.publish(1L);

        verify(productStockMapper).updateByPrimaryKey(stockCaptor.capture());
        assertEquals(Long.valueOf(400L), stockCaptor.getValue().getStock());
    }

    // ========== 库存不足出库测试 ==========

    @Test(expected = ServiceException.class)
    public void testPublish_InsufficientStock() {
        sampleProductStock.setStock(50L);
        sampleOutStockInfo.setProductNumber(100);

        when(outStockMapper.selectByPrimaryKey(1L)).thenReturn(sampleOutStock);
        when(consumerMapper.selectByPrimaryKey(1L)).thenReturn(sampleConsumer);
        when(outStockInfoMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleOutStockInfo));
        when(productMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleProduct));
        when(productStockMapper.selectByExample(any())).thenReturn(Collections.singletonList(sampleProductStock));

        outStockService.publish(1L);
    }

    // ========== 多物资出库测试 ==========

    @Test
    public void testPublish_MultipleProducts() {
        OutStockInfo info1 = new OutStockInfo();
        info1.setOutNum("OUT001");
        info1.setPNum("P001");
        info1.setProductNumber(50);

        OutStockInfo info2 = new OutStockInfo();
        info2.setOutNum("OUT001");
        info2.setPNum("P002");
        info2.setProductNumber(30);

        Product product2 = new Product();
        product2.setPNum("P002");
        product2.setName("防护服");

        ProductStock stock2 = new ProductStock();
        stock2.setPNum("P002");
        stock2.setStock(200L);

        when(outStockMapper.selectByPrimaryKey(1L)).thenReturn(sampleOutStock);
        when(consumerMapper.selectByPrimaryKey(1L)).thenReturn(sampleConsumer);
        when(outStockInfoMapper.selectByExample(any())).thenReturn(Arrays.asList(info1, info2));
        when(productMapper.selectByExample(any()))
                .thenReturn(Collections.singletonList(sampleProduct))
                .thenReturn(Collections.singletonList(product2));
        when(productStockMapper.selectByExample(any()))
                .thenReturn(Collections.singletonList(sampleProductStock))
                .thenReturn(Collections.singletonList(stock2));
        when(productStockMapper.updateByPrimaryKey(any(ProductStock.class))).thenReturn(1);
        when(outStockMapper.updateByPrimaryKeySelective(any(OutStock.class))).thenReturn(1);

        ProductBatch batch1 = new ProductBatch();
        batch1.setBatchNum("B-P001");
        batch1.setBatchStock(50L);
        ProductBatch batch2 = new ProductBatch();
        batch2.setBatchNum("B-P002");
        batch2.setBatchStock(30L);

        when(productBatchService.allocateBatches(eq("P001"), eq(50), eq(false)))
                .thenReturn(Collections.singletonList(batch1));
        when(productBatchService.allocateBatches(eq("P002"), eq(30), eq(false)))
                .thenReturn(Collections.singletonList(batch2));
        doNothing().when(productBatchService).deductBatchStock(anyString(), anyLong());

        outStockService.publish(1L);

        verify(productBatchService).allocateBatches("P001", 50, false);
        verify(productBatchService).allocateBatches("P002", 30, false);
        verify(productBatchService).deductBatchStock("B-P001", 50L);
        verify(productBatchService).deductBatchStock("B-P002", 30L);
    }

    private Date addDays(Date date, int days) {
        Calendar cal = Calendar.getInstance();
        cal.setTime(date);
        cal.add(Calendar.DAY_OF_MONTH, days);
        return cal.getTime();
    }
}
