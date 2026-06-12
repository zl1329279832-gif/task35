package com.coderman.business.service.imp;

import com.coderman.business.mapper.ProductMapper;
import com.coderman.business.mapper.SupplierDeliveryStatsMapper;
import com.coderman.business.mapper.SupplierMapper;
import com.coderman.business.service.SupplierDeliveryStatsService;
import com.coderman.common.model.business.Product;
import com.coderman.common.model.business.Supplier;
import com.coderman.common.model.business.SupplierDeliveryStats;
import com.coderman.common.vo.business.SupplierDeliveryStatsVO;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
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
public class SupplierDeliveryStatsServiceImpl implements SupplierDeliveryStatsService {

    @Autowired
    private SupplierDeliveryStatsMapper supplierDeliveryStatsMapper;

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private ProductMapper productMapper;

    // ==================== 更新统计 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatsOnInStock(Long supplierId, String pNum, int plannedDeliveryDays, int actualDeliveryDays) {
        if (supplierId == null || pNum == null) {
            return;
        }

        SupplierDeliveryStats existing = supplierDeliveryStatsMapper
                .findBySupplierAndProduct(supplierId, pNum);

        boolean isOnTime = actualDeliveryDays <= plannedDeliveryDays || plannedDeliveryDays <= 0;

        if (existing != null) {
            // 更新已有统计
            existing.setTotalOrders(existing.getTotalOrders() + 1);
            if (isOnTime) {
                existing.setOnTimeOrders(existing.getOnTimeOrders() + 1);
            } else {
                existing.setLateOrders(existing.getLateOrders() + 1);
            }

            // 重新计算平均交付天数
            BigDecimal totalDays = existing.getAvgDeliveryDays()
                    .multiply(BigDecimal.valueOf(existing.getTotalOrders() - 1))
                    .add(BigDecimal.valueOf(actualDeliveryDays));
            existing.setAvgDeliveryDays(totalDays
                    .divide(BigDecimal.valueOf(existing.getTotalOrders()), 2, RoundingMode.HALF_UP));

            // 重新计算平均延迟天数
            if (!isOnTime) {
                int delayDays = actualDeliveryDays - plannedDeliveryDays;
                BigDecimal totalDelay = existing.getAvgDelayDays()
                        .multiply(BigDecimal.valueOf(existing.getLateOrders() - 1))
                        .add(BigDecimal.valueOf(delayDays));
                existing.setAvgDelayDays(totalDelay
                        .divide(BigDecimal.valueOf(existing.getLateOrders()), 2, RoundingMode.HALF_UP));
            }

            existing.setLastDeliveryDate(new Date());
            supplierDeliveryStatsMapper.upsertStats(existing);
        } else {
            // 创建新统计
            SupplierDeliveryStats stats = new SupplierDeliveryStats();
            stats.setSupplierId(supplierId);
            stats.setPNum(pNum);
            stats.setTotalOrders(1);
            stats.setOnTimeOrders(isOnTime ? 1 : 0);
            stats.setLateOrders(isOnTime ? 0 : 1);
            stats.setAvgDeliveryDays(BigDecimal.valueOf(actualDeliveryDays));
            stats.setAvgDelayDays(isOnTime ? BigDecimal.ZERO : BigDecimal.valueOf(actualDeliveryDays - plannedDeliveryDays));
            stats.setLastDeliveryDate(new Date());
            supplierDeliveryStatsMapper.upsertStats(stats);
        }
    }

    // ==================== 查询统计 ====================

    @Override
    public List<SupplierDeliveryStatsVO> getStatsBySupplier(Long supplierId) {
        Example example = new Example(SupplierDeliveryStats.class);
        example.createCriteria().andEqualTo("supplierId", supplierId);
        List<SupplierDeliveryStats> statsList = supplierDeliveryStatsMapper.selectByExample(example);
        return convertToVOList(statsList);
    }

    @Override
    public List<SupplierDeliveryStatsVO> getStatsByProduct(String pNum) {
        Example example = new Example(SupplierDeliveryStats.class);
        example.createCriteria().andEqualTo("pNum", pNum);
        List<SupplierDeliveryStats> statsList = supplierDeliveryStatsMapper.selectByExample(example);
        return convertToVOList(statsList);
    }

    @Override
    public List<SupplierDeliveryStatsVO> getDelayedSuppliers(double threshold) {
        List<SupplierDeliveryStats> statsList = supplierDeliveryStatsMapper.findDelayedSuppliers(threshold);
        return convertToVOList(statsList);
    }

    // ==================== 转换方法 ====================

    private SupplierDeliveryStatsVO convertToVO(SupplierDeliveryStats stats) {
        SupplierDeliveryStatsVO vo = new SupplierDeliveryStatsVO();
        BeanUtils.copyProperties(stats, vo);

        // 计算准时率
        if (stats.getTotalOrders() != null && stats.getTotalOrders() > 0) {
            vo.setOnTimeRate(BigDecimal.valueOf(stats.getOnTimeOrders())
                    .divide(BigDecimal.valueOf(stats.getTotalOrders()), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100)));
        } else {
            vo.setOnTimeRate(BigDecimal.ZERO);
        }

        // 关联供应商名称
        if (stats.getSupplierId() != null) {
            Supplier supplier = supplierMapper.selectByPrimaryKey(stats.getSupplierId());
            if (supplier != null) {
                vo.setSupplierName(supplier.getName());
            }
        }

        // 关联物资名称
        if (stats.getPNum() != null) {
            Example productExample = new Example(Product.class);
            productExample.createCriteria().andEqualTo("pNum", stats.getPNum());
            List<Product> products = productMapper.selectByExample(productExample);
            if (!CollectionUtils.isEmpty(products)) {
                vo.setProductName(products.get(0).getName());
            }
        }

        return vo;
    }

    private List<SupplierDeliveryStatsVO> convertToVOList(List<SupplierDeliveryStats> statsList) {
        List<SupplierDeliveryStatsVO> voList = new ArrayList<>();
        if (CollectionUtils.isEmpty(statsList)) {
            return voList;
        }
        for (SupplierDeliveryStats stats : statsList) {
            voList.add(convertToVO(stats));
        }
        return voList;
    }
}
