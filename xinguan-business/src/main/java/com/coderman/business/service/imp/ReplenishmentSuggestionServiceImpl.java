package com.coderman.business.service.imp;

import com.coderman.business.mapper.*;
import com.coderman.business.service.ReplenishmentSuggestionService;
import com.coderman.common.enums.buisiness.RiskLevel;
import com.coderman.common.enums.buisiness.SuggestionStatus;
import com.coderman.common.enums.buisiness.SuggestionType;
import com.coderman.common.exception.ErrorCodeEnum;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.*;
import com.coderman.common.vo.business.ReplenishmentSuggestionVO;
import com.coderman.common.vo.business.StockRiskSnapshotVO;
import com.coderman.common.vo.business.SuggestionAuditVO;
import com.coderman.common.vo.system.PageVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Example;

import java.math.BigDecimal;
import java.util.*;

/**
 * 补货建议服务实现
 */
@Service
public class ReplenishmentSuggestionServiceImpl implements ReplenishmentSuggestionService {

    @Autowired
    private StockRiskSnapshotMapper stockRiskSnapshotMapper;

    @Autowired
    private ReplenishmentSuggestionMapper replenishmentSuggestionMapper;

    @Autowired
    private SuggestionAuditMapper suggestionAuditMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private ProductStockMapper productStockMapper;

    @Autowired
    private SupplierDeliveryStatsMapper supplierDeliveryStatsMapper;

    @Autowired
    private TransferRequestMapper transferRequestMapper;

    // ==================== 生成建议 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReplenishmentSuggestionVO> generateSuggestions(Long snapshotId) {
        StockRiskSnapshot snapshot = stockRiskSnapshotMapper.selectByPrimaryKey(snapshotId);
        if (snapshot == null) {
            throw new ServiceException(ErrorCodeEnum.SNAPSHOT_NOT_FOUND);
        }

        // 只有存在缺口时才生成建议
        if (snapshot.getGapQuantity() <= 0) {
            return Collections.emptyList();
        }

        List<ReplenishmentSuggestionVO> results = new ArrayList<>();

        // 尝试生成跨仓调拨建议
        ReplenishmentSuggestionVO transferSuggestion = tryGenerateTransferSuggestion(snapshot);
        if (transferSuggestion != null) {
            results.add(transferSuggestion);
        }

        // 生成采购补货建议
        ReplenishmentSuggestionVO purchaseSuggestion = tryGeneratePurchaseSuggestion(snapshot);
        if (purchaseSuggestion != null) {
            results.add(purchaseSuggestion);
        }

        return results;
    }

    // ==================== 采纳建议 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adoptSuggestion(Long id, String operator, String reason) {
        ReplenishmentSuggestion suggestion = replenishmentSuggestionMapper.selectByPrimaryKey(id);
        if (suggestion == null) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_NOT_FOUND);
        }
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_ALREADY_PROCESSED);
        }

        // 更新状态为已采纳
        suggestion.setStatus(SuggestionStatus.ADOPTED);
        suggestion.setOperator(operator);
        suggestion.setModifiedTime(new Date());
        replenishmentSuggestionMapper.updateByPrimaryKeySelective(suggestion);

        // 记录审计
        recordAudit(suggestion.getId(), "ADOPT", operator, reason);
    }

    // ==================== 驳回建议 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void rejectSuggestion(Long id, String operator, String reason) {
        ReplenishmentSuggestion suggestion = replenishmentSuggestionMapper.selectByPrimaryKey(id);
        if (suggestion == null) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_NOT_FOUND);
        }
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_ALREADY_PROCESSED);
        }

        // 更新状态为已驳回
        suggestion.setStatus(SuggestionStatus.REJECTED);
        suggestion.setOperator(operator);
        suggestion.setModifiedTime(new Date());
        replenishmentSuggestionMapper.updateByPrimaryKeySelective(suggestion);

        // 记录审计
        recordAudit(suggestion.getId(), "REJECT", operator, reason);
    }

    // ==================== 查询建议 ====================

    @Override
    public PageVO<ReplenishmentSuggestionVO> findSuggestions(Integer status, Integer suggestionType,
                                                              Integer pageNum, Integer pageSize) {
        PageHelper.startPage(pageNum, pageSize);

        Example example = new Example(ReplenishmentSuggestion.class);
        Example.Criteria criteria = example.createCriteria();
        example.setOrderByClause("priority ASC, create_time DESC");

        if (status != null) {
            criteria.andEqualTo("status", status);
        }
        if (suggestionType != null) {
            criteria.andEqualTo("suggestionType", suggestionType);
        }

        List<ReplenishmentSuggestion> suggestions = replenishmentSuggestionMapper.selectByExample(example);
        List<ReplenishmentSuggestionVO> voList = convertToVOList(suggestions);
        PageInfo<ReplenishmentSuggestion> pageInfo = new PageInfo<>(suggestions);
        return new PageVO<>(pageInfo.getTotal(), voList);
    }

    @Override
    public ReplenishmentSuggestionVO getSuggestionDetail(Long id) {
        ReplenishmentSuggestion suggestion = replenishmentSuggestionMapper.selectByPrimaryKey(id);
        if (suggestion == null) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_NOT_FOUND);
        }

        ReplenishmentSuggestionVO vo = convertToVO(suggestion);

        // 加载审计记录
        Example auditExample = new Example(SuggestionAudit.class);
        auditExample.createCriteria().andEqualTo("suggestionId", id);
        auditExample.setOrderByClause("create_time ASC");
        List<SuggestionAudit> audits = suggestionAuditMapper.selectByExample(auditExample);
        List<SuggestionAuditVO> auditVOs = new ArrayList<>();
        if (!CollectionUtils.isEmpty(audits)) {
            for (SuggestionAudit audit : audits) {
                SuggestionAuditVO auditVO = new SuggestionAuditVO();
                BeanUtils.copyProperties(audit, auditVO);
                auditVOs.add(auditVO);
            }
        }
        vo.setAuditRecords(auditVOs);

        // 加载关联快照
        if (suggestion.getSnapshotId() != null) {
            StockRiskSnapshot snapshot = stockRiskSnapshotMapper.selectByPrimaryKey(suggestion.getSnapshotId());
            if (snapshot != null) {
                StockRiskSnapshotVO snapshotVO = new StockRiskSnapshotVO();
                BeanUtils.copyProperties(snapshot, snapshotVO);
                vo.setSnapshot(snapshotVO);
            }
        }

        return vo;
    }

    // ==================== 过期清理 ====================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int expireOldSuggestions(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        Date expiryDate = cal.getTime();

        Example example = new Example(ReplenishmentSuggestion.class);
        example.createCriteria()
                .andEqualTo("status", SuggestionStatus.PENDING)
                .andLessThan("createTime", expiryDate);

        List<ReplenishmentSuggestion> pendingSuggestions = replenishmentSuggestionMapper.selectByExample(example);

        int expiredCount = 0;
        for (ReplenishmentSuggestion suggestion : pendingSuggestions) {
            suggestion.setStatus(SuggestionStatus.EXPIRED);
            suggestion.setModifiedTime(new Date());
            replenishmentSuggestionMapper.updateByPrimaryKeySelective(suggestion);

            recordAudit(suggestion.getId(), "EXPIRE", "SYSTEM", "超过" + days + "天未处理,自动过期");
            expiredCount++;
        }
        return expiredCount;
    }

    // ==================== 内部方法 ====================

    /**
     * 尝试生成跨仓调拨建议
     * 条件：有其他仓库存在富余库存（可用库存 > 安全库存 * 2）
     */
    private ReplenishmentSuggestionVO tryGenerateTransferSuggestion(StockRiskSnapshot snapshot) {
        // 查找其他仓库的库存情况
        // 简化实现：查找调拨记录中的其他仓库
        Example transferExample = new Example(TransferRequest.class);
        transferExample.createCriteria().andEqualTo("pNum", snapshot.getPNum());
        List<TransferRequest> existingTransfers = transferRequestMapper.selectByExample(transferExample);

        // 如果没有历史调拨记录，无法确定调拨来源
        if (CollectionUtils.isEmpty(existingTransfers)) {
            return null;
        }

        // 构建幂等键
        String idempotentKey = snapshot.getPNum() + "_" + snapshot.getSnapshotNum() + "_" + SuggestionType.TRANSFER;
        ReplenishmentSuggestion existing = replenishmentSuggestionMapper.findByIdempotentKey(idempotentKey);
        if (existing != null) {
            return convertToVO(existing);
        }

        // 取最近的调拨来源作为建议来源
        TransferRequest lastTransfer = existingTransfers.get(0);

        ReplenishmentSuggestion suggestion = new ReplenishmentSuggestion();
        suggestion.setSuggestionNum(UUID.randomUUID().toString().replace("-", ""));
        suggestion.setSnapshotId(snapshot.getId());
        suggestion.setPNum(snapshot.getPNum());
        suggestion.setSuggestionType(SuggestionType.TRANSFER);
        suggestion.setSuggestedQuantity(snapshot.getGapQuantity());
        suggestion.setSourceDepartment(lastTransfer.getFromDepartment());
        suggestion.setTargetDepartment(lastTransfer.getToDepartment());
        suggestion.setPriority(mapRiskToPriority(snapshot.getRiskLevel()));
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setRuleVersion(snapshot.getRuleVersion());
        suggestion.setIdempotentKey(idempotentKey);
        suggestion.setCreateTime(new Date());
        suggestion.setModifiedTime(new Date());

        replenishmentSuggestionMapper.insertSelective(suggestion);
        return convertToVO(suggestion);
    }

    /**
     * 尝试生成采购补货建议
     */
    private ReplenishmentSuggestionVO tryGeneratePurchaseSuggestion(StockRiskSnapshot snapshot) {
        // 构建幂等键
        String idempotentKey = snapshot.getPNum() + "_" + snapshot.getSnapshotNum() + "_" + SuggestionType.PURCHASE;
        ReplenishmentSuggestion existing = replenishmentSuggestionMapper.findByIdempotentKey(idempotentKey);
        if (existing != null) {
            return convertToVO(existing);
        }

        // 选择最佳供应商（按交付统计排序）
        Long bestSupplierId = selectBestSupplier(snapshot.getPNum());

        ReplenishmentSuggestion suggestion = new ReplenishmentSuggestion();
        suggestion.setSuggestionNum(UUID.randomUUID().toString().replace("-", ""));
        suggestion.setSnapshotId(snapshot.getId());
        suggestion.setPNum(snapshot.getPNum());
        suggestion.setSuggestionType(SuggestionType.PURCHASE);
        suggestion.setSuggestedQuantity(snapshot.getGapQuantity());
        suggestion.setSupplierId(bestSupplierId);
        suggestion.setPriority(mapRiskToPriority(snapshot.getRiskLevel()));
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setRuleVersion(snapshot.getRuleVersion());
        suggestion.setIdempotentKey(idempotentKey);
        suggestion.setCreateTime(new Date());
        suggestion.setModifiedTime(new Date());

        // 预计交付天数
        if (bestSupplierId != null) {
            SupplierDeliveryStats stats = supplierDeliveryStatsMapper
                    .findBySupplierAndProduct(bestSupplierId, snapshot.getPNum());
            if (stats != null && stats.getAvgDeliveryDays() != null) {
                suggestion.setExpectedDeliveryDays(stats.getAvgDeliveryDays().intValue());
            }
        }

        replenishmentSuggestionMapper.insertSelective(suggestion);
        return convertToVO(suggestion);
    }

    /**
     * 选择最佳供应商
     * 排序依据：准时率降序，平均交付天数升序
     */
    private Long selectBestSupplier(String pNum) {
        Example statsExample = new Example(SupplierDeliveryStats.class);
        statsExample.createCriteria()
                .andEqualTo("pNum", pNum)
                .andGreaterThan("totalOrders", 0);
        List<SupplierDeliveryStats> statsList = supplierDeliveryStatsMapper.selectByExample(statsExample);

        if (CollectionUtils.isEmpty(statsList)) {
            // 没有交付记录，返回第一个供应商
            Example supplierExample = new Example(Supplier.class);
            supplierExample.setOrderByClause("sort ASC");
            List<Supplier> suppliers = supplierMapper.selectByExample(supplierExample);
            return CollectionUtils.isEmpty(suppliers) ? null : suppliers.get(0).getId();
        }

        // 按准时率降序、平均交付天数升序排序
        statsList.sort((a, b) -> {
            double rateA = a.getTotalOrders() > 0
                    ? (double) a.getOnTimeOrders() / a.getTotalOrders() : 0;
            double rateB = b.getTotalOrders() > 0
                    ? (double) b.getOnTimeOrders() / b.getTotalOrders() : 0;
            if (rateA != rateB) {
                return Double.compare(rateB, rateA); // 降序
            }
            return a.getAvgDeliveryDays().compareTo(b.getAvgDeliveryDays()); // 升序
        });

        return statsList.get(0).getSupplierId();
    }

    /**
     * 风险等级映射为建议优先级
     */
    private int mapRiskToPriority(int riskLevel) {
        switch (riskLevel) {
            case RiskLevel.CRITICAL: return 1; // 紧急
            case RiskLevel.HIGH: return 2;     // 高
            case RiskLevel.MEDIUM: return 3;   // 中
            default: return 4;                  // 低
        }
    }

    /**
     * 记录审计
     */
    private void recordAudit(Long suggestionId, String action, String operator, String reason) {
        SuggestionAudit audit = new SuggestionAudit();
        audit.setSuggestionId(suggestionId);
        audit.setAction(action);
        audit.setOperator(operator);
        audit.setReason(reason);
        audit.setCreateTime(new Date());
        suggestionAuditMapper.insertSelective(audit);
    }

    // ==================== 转换方法 ====================

    private ReplenishmentSuggestionVO convertToVO(ReplenishmentSuggestion suggestion) {
        ReplenishmentSuggestionVO vo = new ReplenishmentSuggestionVO();
        BeanUtils.copyProperties(suggestion, vo);

        vo.setSuggestionTypeDesc(suggestion.getSuggestionType() == SuggestionType.PURCHASE
                ? "采购补货" : "跨仓调拨");
        vo.setStatusDesc(getStatusDesc(suggestion.getStatus()));
        vo.setPriorityDesc(getPriorityDesc(suggestion.getPriority()));

        // 关联物资名称
        Example productExample = new Example(Product.class);
        productExample.createCriteria().andEqualTo("pNum", suggestion.getPNum());
        List<Product> products = productMapper.selectByExample(productExample);
        if (!CollectionUtils.isEmpty(products)) {
            vo.setProductName(products.get(0).getName());
        }

        // 关联供应商名称
        if (suggestion.getSupplierId() != null) {
            Supplier supplier = supplierMapper.selectByPrimaryKey(suggestion.getSupplierId());
            if (supplier != null) {
                vo.setSupplierName(supplier.getName());
            }
        }

        return vo;
    }

    private List<ReplenishmentSuggestionVO> convertToVOList(List<ReplenishmentSuggestion> suggestions) {
        List<ReplenishmentSuggestionVO> voList = new ArrayList<>();
        if (CollectionUtils.isEmpty(suggestions)) {
            return voList;
        }
        for (ReplenishmentSuggestion suggestion : suggestions) {
            voList.add(convertToVO(suggestion));
        }
        return voList;
    }

    private String getStatusDesc(int status) {
        switch (status) {
            case SuggestionStatus.PENDING: return "待处理";
            case SuggestionStatus.ADOPTED: return "已采纳";
            case SuggestionStatus.REJECTED: return "已驳回";
            case SuggestionStatus.EXPIRED: return "已过期";
            default: return "未知";
        }
    }

    private String getPriorityDesc(int priority) {
        switch (priority) {
            case 1: return "紧急";
            case 2: return "高";
            case 3: return "中";
            case 4: return "低";
            default: return "未知";
        }
    }
}
