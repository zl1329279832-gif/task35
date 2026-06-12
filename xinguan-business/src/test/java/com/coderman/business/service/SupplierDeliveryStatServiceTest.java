package com.coderman.business.service;

import com.coderman.business.mapper.ReplenishRuleVersionMapper;
import com.coderman.business.mapper.SupplierDeliveryStatMapper;
import com.coderman.business.service.imp.SupplierDeliveryStatServiceImpl;
import com.coderman.common.model.business.ReplenishRuleVersion;
import com.coderman.common.model.business.SupplierDeliveryStat;
import com.coderman.common.vo.business.SupplierDeliveryStatVO;
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
import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class SupplierDeliveryStatServiceTest {

    @InjectMocks
    private SupplierDeliveryStatServiceImpl statService;

    @Mock
    private SupplierDeliveryStatMapper statMapper;

    @Mock
    private ReplenishRuleVersionMapper ruleVersionMapper;

    @Captor
    private ArgumentCaptor<SupplierDeliveryStat> statCaptor;

    private SupplierDeliveryStat existingStat;

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(SupplierDeliveryStat.class, config);
    }

    @Before
    public void setUp() {
        existingStat = new SupplierDeliveryStat();
        existingStat.setId(1L);
        existingStat.setSupplierId(100L);
        existingStat.setPNum("P001");
        existingStat.setTotalDeliveries(5);
        existingStat.setOnTimeDeliveries(4);
        existingStat.setAvgLeadDays(new BigDecimal("6.00"));
        existingStat.setMaxLeadDays(8);
        existingStat.setMinLeadDays(4);
        existingStat.setOnTimeRate(new BigDecimal("0.8000"));
        existingStat.setLastDeliveryTime(new Date());
        existingStat.setPromisedLeadDays(7);
    }

    @Test
    public void testRecordDelivery_NewStat_CreatesRecord() {
        when(statMapper.findBySupplierAndPNum(100L, "P001")).thenReturn(null);
        when(statMapper.findBySupplierOverall(100L)).thenReturn(null);

        statService.recordDelivery(100L, "P001", 5, 7);

        // 应该创建2条记录(物资级 + 整体级)
        verify(statMapper, times(2)).insertSelective(statCaptor.capture());
        SupplierDeliveryStat captured = statCaptor.getAllValues().get(0);
        assertEquals(Integer.valueOf(1), captured.getTotalDeliveries());
        assertEquals(new BigDecimal("5"), captured.getAvgLeadDays());
        assertEquals(Integer.valueOf(1), captured.getOnTimeDeliveries()); // 5 <= 7
    }

    @Test
    public void testRecordDelivery_UpdateExisting_CalculatesRunningAvg() {
        when(statMapper.findBySupplierAndPNum(100L, "P001")).thenReturn(existingStat);
        // 整体级也有记录
        SupplierDeliveryStat overallStat = new SupplierDeliveryStat();
        overallStat.setId(2L);
        overallStat.setSupplierId(100L);
        overallStat.setPNum(null);
        overallStat.setTotalDeliveries(5);
        overallStat.setOnTimeDeliveries(4);
        overallStat.setAvgLeadDays(new BigDecimal("6.00"));
        overallStat.setMaxLeadDays(8);
        overallStat.setMinLeadDays(4);
        overallStat.setOnTimeRate(new BigDecimal("0.8000"));
        overallStat.setPromisedLeadDays(7);
        when(statMapper.findBySupplierOverall(100L)).thenReturn(overallStat);

        // 记录一次10天的延迟交付(超过承诺的7天)
        statService.recordDelivery(100L, "P001", 10, 7);

        verify(statMapper, times(2)).updateByPrimaryKeySelective(statCaptor.capture());
        SupplierDeliveryStat updated = statCaptor.getAllValues().get(0);
        assertEquals(Integer.valueOf(6), updated.getTotalDeliveries());
        // 新平均 = (6.00*5 + 10) / 6 = 40/6 = 6.67
        assertEquals(new BigDecimal("6.67"), updated.getAvgLeadDays());
        assertEquals(Integer.valueOf(10), updated.getMaxLeadDays());
        // onTimeDeliveries不变(10 > 7)
        assertEquals(Integer.valueOf(4), updated.getOnTimeDeliveries());
    }

    @Test
    public void testGetEstimatedLeadDays_UsesActualAvg() {
        when(statMapper.findBySupplierAndPNum(100L, "P001")).thenReturn(existingStat);

        int result = statService.getEstimatedLeadDays(100L, "P001");

        assertEquals(6, result); // avgLeadDays=6.00, ceil=6
    }

    @Test
    public void testGetEstimatedLeadDays_FallsBackToDefault() {
        when(statMapper.findBySupplierAndPNum(100L, "P001")).thenReturn(null);
        when(statMapper.findBySupplierOverall(100L)).thenReturn(null);
        ReplenishRuleVersion rule = new ReplenishRuleVersion();
        rule.setSupplierLeadTimeDefault(7);
        when(ruleVersionMapper.findActiveRule()).thenReturn(rule);

        int result = statService.getEstimatedLeadDays(100L, "P001");

        assertEquals(7, result);
    }
}
