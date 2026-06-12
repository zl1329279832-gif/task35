package com.coderman.business.service.imp;

import com.coderman.business.mapper.ReplenishRuleVersionMapper;
import com.coderman.business.mapper.SupplierDeliveryStatMapper;
import com.coderman.common.model.business.ReplenishRuleVersion;
import com.coderman.common.model.business.SupplierDeliveryStat;
import com.coderman.common.vo.business.SupplierDeliveryStatVO;
import com.coderman.business.service.SupplierDeliveryStatService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 供应商交付统计服务实现
 */
@Service
public class SupplierDeliveryStatServiceImpl implements SupplierDeliveryStatService {

    @Autowired
    private SupplierDeliveryStatMapper statMapper;

    @Autowired
    private ReplenishRuleVersionMapper ruleVersionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recordDelivery(Long supplierId, String pNum, int actualLeadDays, Integer promisedLeadDays) {
        // 更新特定物资的统计
        updateStat(supplierId, pNum, actualLeadDays, promisedLeadDays);
        // 更新供应商整体统计
        updateStat(supplierId, null, actualLeadDays, promisedLeadDays);
    }

    private void updateStat(Long supplierId, String pNum, int actualLeadDays, Integer promisedLeadDays) {
        SupplierDeliveryStat stat;
        if (pNum != null) {
            stat = statMapper.findBySupplierAndPNum(supplierId, pNum);
        } else {
            stat = statMapper.findBySupplierOverall(supplierId);
        }

        if (stat == null) {
            // 创建新记录
            stat = new SupplierDeliveryStat();
            stat.setSupplierId(supplierId);
            stat.setPNum(pNum);
            stat.setTotalDeliveries(1);
            stat.setAvgLeadDays(BigDecimal.valueOf(actualLeadDays));
            stat.setMaxLeadDays(actualLeadDays);
            stat.setMinLeadDays(actualLeadDays);
            stat.setLastDeliveryTime(new Date());
            stat.setPromisedLeadDays(promisedLeadDays);
            stat.setCreateTime(new Date());
            stat.setModifiedTime(new Date());

            // 判断是否准时
            boolean onTime = promisedLeadDays == null || actualLeadDays <= promisedLeadDays;
            stat.setOnTimeDeliveries(onTime ? 1 : 0);
            stat.setOnTimeRate(onTime ? BigDecimal.ONE : BigDecimal.ZERO);

            statMapper.insertSelective(stat);
        } else {
            // 增量更新
            int newTotal = stat.getTotalDeliveries() + 1;
            BigDecimal newAvg = stat.getAvgLeadDays()
                    .multiply(BigDecimal.valueOf(stat.getTotalDeliveries()))
                    .add(BigDecimal.valueOf(actualLeadDays))
                    .divide(BigDecimal.valueOf(newTotal), 2, RoundingMode.HALF_UP);

            stat.setTotalDeliveries(newTotal);
            stat.setAvgLeadDays(newAvg);
            stat.setMaxLeadDays(Math.max(stat.getMaxLeadDays(), actualLeadDays));
            stat.setMinLeadDays(Math.min(stat.getMinLeadDays(), actualLeadDays));
            stat.setLastDeliveryTime(new Date());
            stat.setModifiedTime(new Date());

            if (promisedLeadDays != null) {
                stat.setPromisedLeadDays(promisedLeadDays);
            }

            boolean onTime = (stat.getPromisedLeadDays() == null) || actualLeadDays <= stat.getPromisedLeadDays();
            if (onTime) {
                stat.setOnTimeDeliveries(stat.getOnTimeDeliveries() + 1);
            }
            stat.setOnTimeRate(BigDecimal.valueOf(stat.getOnTimeDeliveries())
                    .divide(BigDecimal.valueOf(newTotal), 4, RoundingMode.HALF_UP));

            statMapper.updateByPrimaryKeySelective(stat);
        }
    }

    @Override
    public List<SupplierDeliveryStatVO> findBySupplier(Long supplierId) {
        Example example = new Example(SupplierDeliveryStat.class);
        example.createCriteria().andEqualTo("supplierId", supplierId);
        example.setOrderByClause("p_num ASC");
        List<SupplierDeliveryStat> stats = statMapper.selectByExample(example);

        List<SupplierDeliveryStatVO> voList = new ArrayList<>();
        for (SupplierDeliveryStat stat : stats) {
            voList.add(convertToVO(stat));
        }
        return voList;
    }

    @Override
    public SupplierDeliveryStatVO getStatForProduct(Long supplierId, String pNum) {
        SupplierDeliveryStat stat = statMapper.findBySupplierAndPNum(supplierId, pNum);
        return stat != null ? convertToVO(stat) : null;
    }

    @Override
    public int getEstimatedLeadDays(Long supplierId, String pNum) {
        // 先尝试特定物资的统计
        if (supplierId != null && pNum != null) {
            SupplierDeliveryStat stat = statMapper.findBySupplierAndPNum(supplierId, pNum);
            if (stat != null && stat.getTotalDeliveries() >= 3) {
                return stat.getAvgLeadDays().setScale(0, RoundingMode.CEILING).intValue();
            }
        }
        // 再尝试供应商整体统计
        if (supplierId != null) {
            SupplierDeliveryStat overall = statMapper.findBySupplierOverall(supplierId);
            if (overall != null && overall.getTotalDeliveries() >= 3) {
                return overall.getAvgLeadDays().setScale(0, RoundingMode.CEILING).intValue();
            }
        }
        // 使用规则默认值
        ReplenishRuleVersion rule = ruleVersionMapper.findActiveRule();
        return rule != null ? rule.getSupplierLeadTimeDefault() : 7;
    }

    private SupplierDeliveryStatVO convertToVO(SupplierDeliveryStat stat) {
        SupplierDeliveryStatVO vo = new SupplierDeliveryStatVO();
        BeanUtils.copyProperties(stat, vo);
        return vo;
    }
}
