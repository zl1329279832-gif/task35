package com.coderman.business.service;

import com.coderman.business.mapper.ReplenishRuleVersionMapper;
import com.coderman.business.service.imp.ReplenishRuleServiceImpl;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.ReplenishRuleVersion;
import com.coderman.common.vo.business.ReplenishRuleVersionVO;
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

import java.util.Date;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class ReplenishRuleServiceTest {

    @InjectMocks
    private ReplenishRuleServiceImpl ruleService;

    @Mock
    private ReplenishRuleVersionMapper ruleVersionMapper;

    @Captor
    private ArgumentCaptor<ReplenishRuleVersion> ruleCaptor;

    private ReplenishRuleVersion activeRule;

    @BeforeClass
    public static void initMapper() {
        Config config = new Config();
        EntityHelper.initEntityNameMap(ReplenishRuleVersion.class, config);
    }

    @Before
    public void setUp() {
        activeRule = new ReplenishRuleVersion();
        activeRule.setId(1L);
        activeRule.setVersionNum("V1.0");
        activeRule.setLookbackDays(30);
        activeRule.setNearExpiryDays(30);
        activeRule.setSafeDays(30);
        activeRule.setLowDays(20);
        activeRule.setMediumDays(10);
        activeRule.setHighDays(5);
        activeRule.setSafetyStockDays(14);
        activeRule.setSupplierLeadTimeDefault(7);
        activeRule.setSuggestionDedupHours(24);
        activeRule.setIsActive(1);
        activeRule.setCreatedBy("SYSTEM");
        activeRule.setCreateTime(new Date());
    }

    @Test
    public void testGetActiveRule_Success() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(activeRule);

        ReplenishRuleVersionVO result = ruleService.getActiveRule();

        assertNotNull(result);
        assertEquals("V1.0", result.getVersionNum());
        assertEquals(Integer.valueOf(30), result.getLookbackDays());
        assertEquals(Integer.valueOf(1), result.getIsActive());
    }

    @Test(expected = ServiceException.class)
    public void testGetActiveRule_NotFound_ThrowsException() {
        when(ruleVersionMapper.findActiveRule()).thenReturn(null);
        ruleService.getActiveRule();
    }

    @Test
    public void testActivateRule_DeactivatesOthers() {
        ReplenishRuleVersion newRule = new ReplenishRuleVersion();
        newRule.setId(2L);
        newRule.setVersionNum("V2.0");
        newRule.setIsActive(0);

        when(ruleVersionMapper.selectByPrimaryKey(2L)).thenReturn(newRule);
        when(ruleVersionMapper.updateByExampleSelective(any(), any())).thenReturn(1);
        when(ruleVersionMapper.updateByPrimaryKeySelective(any())).thenReturn(1);

        ruleService.activateRule(2L);

        // 验证先停用所有
        verify(ruleVersionMapper).updateByExampleSelective(any(ReplenishRuleVersion.class), any());
        // 验证激活指定版本
        verify(ruleVersionMapper).updateByPrimaryKeySelective(ruleCaptor.capture());
        assertEquals(Integer.valueOf(1), ruleCaptor.getValue().getIsActive());
    }
}
