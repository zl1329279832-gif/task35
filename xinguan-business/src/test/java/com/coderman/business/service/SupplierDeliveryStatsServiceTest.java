package com.coderman.business.service;

import com.coderman.business.mapper.ProductMapper;
import com.coderman.business.mapper.SupplierDeliveryStatsMapper;
import com.coderman.business.mapper.SupplierMapper;
import com.coderman.business.service.imp.SupplierDeliveryStatsServiceImpl;
import com.coderman.common.model.business.Product;
import com.coderman.common.model.business.Supplier;
import com.coderman.common.model.business.SupplierDeliveryStats;
import com.coderman.common.vo.business.SupplierDeliveryStatsVO;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 供应商交付统计服务测试
 * 覆盖：准时交付/延迟交付/低准时率供应商筛选
 */
@RunWith(MockitoJUnitRunner.class)
public class SupplierDeliveryStatsServiceTest {

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(SupplierDeliveryStats.class, config);
        EntityHelper.initEntityNameMap(Supplier.class, config);
        EntityHelper.initEntityNameMap(Product.class, config);
    }

    @InjectMocks
    private SupplierDeliveryStatsServiceImpl deliveryStatsService;

    @Mock
    private SupplierDeliveryStatsMapper supplierDeliveryStatsMapper;
    @Mock
    private SupplierMapper supplierMapper;
    @Mock
    private ProductMapper productMapper;

    @Captor
    private ArgumentCaptor<SupplierDeliveryStats> statsCaptor;

    private Supplier supplier;
    private Product product;

    @Before
    public void setUp() {
        supplier = new Supplier();
        supplier.setId(1L);
        supplier.setName("供应商A");

        product = new Product();
        product.setId(1L);
        product.setPNum("P001");
        product.setName("N95口罩");
    }

    // ==================== 1. 准时交付统计 ====================

    @Test
    public void testUpdateStats_OnTimeDelivery() {
        when(supplierDeliveryStatsMapper.findBySupplierAndProduct(1L, "P001")).thenReturn(null);
        when(supplierDeliveryStatsMapper.upsertStats(any(SupplierDeliveryStats.class))).thenReturn(1);

        deliveryStatsService.updateStatsOnInStock(1L, "P001", 7, 5);

        verify(supplierDeliveryStatsMapper).upsertStats(statsCaptor.capture());
        SupplierDeliveryStats stats = statsCaptor.getValue();
        assertEquals(Long.valueOf(1L), stats.getSupplierId());
        assertEquals("P001", stats.getPNum());
        assertEquals(Integer.valueOf(1), stats.getTotalOrders());
        assertEquals(Integer.valueOf(1), stats.getOnTimeOrders()); // 准时（5 <= 7）
        assertEquals(Integer.valueOf(0), stats.getLateOrders());
        assertEquals(0, stats.getAvgDelayDays().compareTo(BigDecimal.ZERO));
    }

    // ==================== 2. 延迟交付统计 ====================

    @Test
    public void testUpdateStats_LateDelivery() {
        when(supplierDeliveryStatsMapper.findBySupplierAndProduct(1L, "P001")).thenReturn(null);
        when(supplierDeliveryStatsMapper.upsertStats(any(SupplierDeliveryStats.class))).thenReturn(1);

        deliveryStatsService.updateStatsOnInStock(1L, "P001", 7, 12);

        verify(supplierDeliveryStatsMapper).upsertStats(statsCaptor.capture());
        SupplierDeliveryStats stats = statsCaptor.getValue();
        assertEquals(Integer.valueOf(1), stats.getTotalOrders());
        assertEquals(Integer.valueOf(0), stats.getOnTimeOrders()); // 不准时（12 > 7）
        assertEquals(Integer.valueOf(1), stats.getLateOrders());
        assertEquals(0, stats.getAvgDelayDays().compareTo(new BigDecimal("5"))); // 12 - 7 = 5
    }

    // ==================== 3. 低准时率供应商筛选 ====================

    @Test
    public void testGetDelayedSuppliers_BelowThreshold() {
        SupplierDeliveryStats badSupplier = new SupplierDeliveryStats();
        badSupplier.setId(1L);
        badSupplier.setSupplierId(1L);
        badSupplier.setPNum("P001");
        badSupplier.setTotalOrders(10);
        badSupplier.setOnTimeOrders(3); // 30% 准时率
        badSupplier.setLateOrders(7);
        badSupplier.setAvgDeliveryDays(new BigDecimal("15"));
        badSupplier.setAvgDelayDays(new BigDecimal("8"));

        when(supplierDeliveryStatsMapper.findDelayedSuppliers(0.5))
                .thenReturn(Collections.singletonList(badSupplier));
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(product));

        List<SupplierDeliveryStatsVO> results = deliveryStatsService.getDelayedSuppliers(0.5);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("供应商A", results.get(0).getSupplierName());
        assertEquals("N95口罩", results.get(0).getProductName());
        assertNotNull(results.get(0).getOnTimeRate());
        // 准时率 = 3/10 * 100 = 30%
        assertEquals(0, results.get(0).getOnTimeRate().compareTo(new BigDecimal("30.0000")));
    }

    // ==================== 4. 更新已有统计记录 ====================

    @Test
    public void testUpdateStats_ExistingRecord() {
        SupplierDeliveryStats existing = new SupplierDeliveryStats();
        existing.setId(1L);
        existing.setSupplierId(1L);
        existing.setPNum("P001");
        existing.setTotalOrders(5);
        existing.setOnTimeOrders(4);
        existing.setLateOrders(1);
        existing.setAvgDeliveryDays(new BigDecimal("6.00"));
        existing.setAvgDelayDays(new BigDecimal("3.00"));

        when(supplierDeliveryStatsMapper.findBySupplierAndProduct(1L, "P001")).thenReturn(existing);
        when(supplierDeliveryStatsMapper.upsertStats(any(SupplierDeliveryStats.class))).thenReturn(1);

        deliveryStatsService.updateStatsOnInStock(1L, "P001", 7, 5);

        verify(supplierDeliveryStatsMapper).upsertStats(statsCaptor.capture());
        SupplierDeliveryStats updated = statsCaptor.getValue();
        assertEquals(Integer.valueOf(6), updated.getTotalOrders());
        assertEquals(Integer.valueOf(5), updated.getOnTimeOrders()); // 4 + 1(准时)
        assertEquals(Integer.valueOf(1), updated.getLateOrders());   // 1 + 0
    }

    // ==================== 5. 按供应商查询统计 ====================

    @Test
    public void testGetStatsBySupplier() {
        SupplierDeliveryStats stats1 = new SupplierDeliveryStats();
        stats1.setSupplierId(1L);
        stats1.setPNum("P001");
        stats1.setTotalOrders(10);
        stats1.setOnTimeOrders(8);
        stats1.setLateOrders(2);
        stats1.setAvgDeliveryDays(new BigDecimal("5.00"));
        stats1.setAvgDelayDays(new BigDecimal("2.00"));

        when(supplierDeliveryStatsMapper.selectByExample(any(Example.class)))
                .thenReturn(Collections.singletonList(stats1));
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(product));

        List<SupplierDeliveryStatsVO> results = deliveryStatsService.getStatsBySupplier(1L);

        assertNotNull(results);
        assertEquals(1, results.size());
        assertEquals("供应商A", results.get(0).getSupplierName());
    }

    // ==================== 6. 按物资查询统计 ====================

    @Test
    public void testGetStatsByProduct() {
        SupplierDeliveryStats stats1 = new SupplierDeliveryStats();
        stats1.setSupplierId(1L);
        stats1.setPNum("P001");
        stats1.setTotalOrders(10);
        stats1.setOnTimeOrders(8);
        stats1.setLateOrders(2);
        stats1.setAvgDeliveryDays(new BigDecimal("5.00"));
        stats1.setAvgDelayDays(new BigDecimal("2.00"));

        SupplierDeliveryStats stats2 = new SupplierDeliveryStats();
        stats2.setSupplierId(2L);
        stats2.setPNum("P001");
        stats2.setTotalOrders(5);
        stats2.setOnTimeOrders(2);
        stats2.setLateOrders(3);
        stats2.setAvgDeliveryDays(new BigDecimal("12.00"));
        stats2.setAvgDelayDays(new BigDecimal("6.00"));

        when(supplierDeliveryStatsMapper.selectByExample(any(Example.class)))
                .thenReturn(Arrays.asList(stats1, stats2));
        when(supplierMapper.selectByPrimaryKey(1L)).thenReturn(supplier);

        Supplier supplierB = new Supplier();
        supplierB.setId(2L);
        supplierB.setName("供应商B");
        when(supplierMapper.selectByPrimaryKey(2L)).thenReturn(supplierB);
        when(productMapper.selectByExample(any(Example.class))).thenReturn(Collections.singletonList(product));

        List<SupplierDeliveryStatsVO> results = deliveryStatsService.getStatsByProduct("P001");

        assertNotNull(results);
        assertEquals(2, results.size());
    }
}
