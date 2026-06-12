package com.coderman.business.service.imp;

import com.coderman.business.mapper.*;
import com.coderman.business.service.InventoryRiskService;
import com.coderman.business.service.ReplenishSuggestionService;
import com.coderman.business.service.SupplierDeliveryStatService;
import com.coderman.business.service.TransferService;
import com.coderman.common.enums.buisiness.*;
import com.coderman.common.exception.ErrorCodeEnum;
import com.coderman.common.exception.ServiceException;
import com.coderman.common.model.business.*;
import com.coderman.common.vo.business.*;
import com.coderman.common.vo.system.PageVO;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Example;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 补货建议服务实现
 */
@Service
public class ReplenishSuggestionServiceImpl implements ReplenishSuggestionService {

    @Autowired
    private ReplenishSuggestionMapper suggestionMapper;

    @Autowired
    private SuggestionAuditMapper auditMapper;

    @Autowired
    private InventoryRiskSnapshotMapper snapshotMapper;

    @Autowired
    private ReplenishRuleVersionMapper ruleVersionMapper;

    @Autowired
    private ProductBatchMapper productBatchMapper;

    @Autowired
    private ProductMapper productMapper;

    @Autowired
    private SupplierMapper supplierMapper;

    @Autowired
    private TransferRequestMapper transferRequestMapper;

    @Autowired
    private SupplierDeliveryStatService deliveryStatService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReplenishSuggestionVO> generateSuggestions() {
        ReplenishRuleVersion rule = ruleVersionMapper.findActiveRule();
        if (rule == null) {
            throw new ServiceException(ErrorCodeEnum.RISK_RULE_NOT_FOUND);
        }

        // 查询所有最新快照中非SAFE的物资
        List<InventoryRiskSnapshot> riskSnapshots = snapshotMapper.findLatestAll();
        List<ReplenishSuggestionVO> results = new ArrayList<>();

        for (InventoryRiskSnapshot snapshot : riskSnapshots) {
            if (RiskLevel.SAFE.equals(snapshot.getRiskLevel())) {
                continue;
            }
            List<ReplenishSuggestionVO> suggestions = doGenerateForSnapshot(snapshot, rule);
            results.addAll(suggestions);
        }
        return results;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<ReplenishSuggestionVO> generateSuggestionsForProduct(String pNum) {
        ReplenishRuleVersion rule = ruleVersionMapper.findActiveRule();
        if (rule == null) {
            throw new ServiceException(ErrorCodeEnum.RISK_RULE_NOT_FOUND);
        }

        InventoryRiskSnapshot snapshot = snapshotMapper.findLatestByPNum(pNum);
        if (snapshot == null) {
            throw new ServiceException(ErrorCodeEnum.RISK_SNAPSHOT_NOT_FOUND);
        }

        return doGenerateForSnapshot(snapshot, rule);
    }

    @Override
    public PageVO<ReplenishSuggestionVO> findSuggestions(Integer pageNum, Integer pageSize,
                                                          String pNum, String suggestionType, Integer status) {
        PageHelper.startPage(pageNum, pageSize);
        Example example = new Example(ReplenishSuggestion.class);
        Example.Criteria criteria = example.createCriteria();
        if (pNum != null && !pNum.isEmpty()) {
            criteria.andLike("pNum", "%" + pNum + "%");
        }
        if (suggestionType != null && !suggestionType.isEmpty()) {
            criteria.andEqualTo("suggestionType", suggestionType);
        }
        if (status != null) {
            criteria.andEqualTo("status", status);
        }
        example.setOrderByClause("create_time desc");

        List<ReplenishSuggestion> suggestions = suggestionMapper.selectByExample(example);
        PageInfo<ReplenishSuggestion> pageInfo = new PageInfo<>(suggestions);

        List<ReplenishSuggestionVO> voList = enrichSuggestionVOs(suggestions);
        return new PageVO<>(pageInfo.getTotal(), voList);
    }

    @Override
    public ReplenishSuggestionVO getDetail(Long id) {
        ReplenishSuggestion suggestion = suggestionMapper.selectByPrimaryKey(id);
        if (suggestion == null) {
            return null;
        }
        ReplenishSuggestionVO vo = convertToVO(suggestion);
        enrichSingleVO(vo, suggestion);

        // 加载审计轨迹
        List<SuggestionAudit> audits = auditMapper.findBySuggestionId(id);
        List<SuggestionAuditVO> auditVOs = new ArrayList<>();
        for (SuggestionAudit audit : audits) {
            SuggestionAuditVO avo = new SuggestionAuditVO();
            BeanUtils.copyProperties(audit, avo);
            auditVOs.add(avo);
        }
        vo.setAuditTrail(auditVOs);

        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void adopt(Long id) {
        ReplenishSuggestion suggestion = suggestionMapper.selectByPrimaryKey(id);
        if (suggestion == null) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_NOT_FOUND);
        }
        if (suggestion.getStatus() == SuggestionStatus.ADOPTED) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_ALREADY_ADOPTED);
        }
        if (suggestion.getStatus() != SuggestionStatus.PENDING) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_STATUS_ERROR);
        }

        int beforeStatus = suggestion.getStatus();
        suggestion.setStatus(SuggestionStatus.ADOPTED);
        suggestion.setAdoptedTime(new Date());
        suggestion.setModifiedTime(new Date());

        // 对于TRANSFER类型,创建调拨申请
        if (SuggestionType.TRANSFER.equals(suggestion.getSuggestionType())) {
            TransferRequest transfer = new TransferRequest();
            String transferNum = UUID.randomUUID().toString().substring(0, 32).replace("-", "");
            transfer.setTransferNum(transferNum);
            transfer.setPNum(suggestion.getPNum());
            transfer.setTransferQuantity(suggestion.getSuggestedQuantity());
            transfer.setFromDepartment(suggestion.getFromDepartment());
            transfer.setToDepartment(suggestion.getToDepartment());
            transfer.setReason("补货建议自动生成: " + suggestion.getReason());
            transfer.setEmergencyLevel(RiskLevel.CRITICAL.equals(suggestion.getRiskLevel()) ? 3 :
                    RiskLevel.HIGH.equals(suggestion.getRiskLevel()) ? 2 : 1);
            transfer.setOperator("SYSTEM");
            transfer.setStatus(TransferStatus.PENDING);
            transfer.setCreateTime(new Date());
            transfer.setModifiedTime(new Date());
            transferRequestMapper.insertSelective(transfer);

            suggestion.setRelatedTransferNum(transferNum);
        }

        suggestionMapper.updateByPrimaryKeySelective(suggestion);

        // 记录审计
        recordAudit(suggestion, SuggestionAuditAction.ADOPTED, beforeStatus, SuggestionStatus.ADOPTED,
                suggestion.getRelatedTransferNum());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void reject(Long id, String reason) {
        ReplenishSuggestion suggestion = suggestionMapper.selectByPrimaryKey(id);
        if (suggestion == null) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_NOT_FOUND);
        }
        if (suggestion.getStatus() == SuggestionStatus.REJECTED) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_ALREADY_REJECTED);
        }
        if (suggestion.getStatus() != SuggestionStatus.PENDING
                && suggestion.getStatus() != SuggestionStatus.ADOPTED) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_STATUS_ERROR);
        }

        int beforeStatus = suggestion.getStatus();
        suggestion.setStatus(SuggestionStatus.REJECTED);
        suggestion.setModifiedTime(new Date());
        suggestionMapper.updateByPrimaryKeySelective(suggestion);

        recordAudit(suggestion, SuggestionAuditAction.REJECTED, beforeStatus, SuggestionStatus.REJECTED,
                reason);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void onTransferRejected(String transferNum) {
        ReplenishSuggestion suggestion = suggestionMapper.findByRelatedTransferNum(transferNum);
        if (suggestion == null) {
            return; // 非建议生成的调拨,忽略
        }

        int beforeStatus = suggestion.getStatus();
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setRelatedTransferNum(null);
        suggestion.setAdoptedBy(null);
        suggestion.setAdoptedTime(null);
        suggestion.setModifiedTime(new Date());
        suggestionMapper.updateByPrimaryKeySelective(suggestion);

        recordAudit(suggestion, SuggestionAuditAction.TRANSFER_REJECTED, beforeStatus, SuggestionStatus.PENDING,
                "调拨申请被拒绝: " + transferNum);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markExecuted(Long id, String relatedNum) {
        ReplenishSuggestion suggestion = suggestionMapper.selectByPrimaryKey(id);
        if (suggestion == null) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_NOT_FOUND);
        }
        if (suggestion.getStatus() != SuggestionStatus.ADOPTED) {
            throw new ServiceException(ErrorCodeEnum.SUGGESTION_STATUS_ERROR);
        }

        int beforeStatus = suggestion.getStatus();
        suggestion.setStatus(SuggestionStatus.EXECUTED);
        suggestion.setModifiedTime(new Date());
        if (relatedNum != null) {
            if (SuggestionType.PURCHASE.equals(suggestion.getSuggestionType())) {
                suggestion.setRelatedInNum(relatedNum);
            } else {
                suggestion.setRelatedTransferNum(relatedNum);
            }
        }
        suggestionMapper.updateByPrimaryKeySelective(suggestion);

        recordAudit(suggestion, SuggestionAuditAction.EXECUTED, beforeStatus, SuggestionStatus.EXECUTED,
                relatedNum);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireStaleSuggestions() {
        ReplenishRuleVersion rule = ruleVersionMapper.findActiveRule();
        int expireHours = rule != null ? rule.getSuggestionDedupHours() * 7 : 168; // 默认7天

        Example example = new Example(ReplenishSuggestion.class);
        example.createCriteria()
                .andEqualTo("status", SuggestionStatus.PENDING);

        List<ReplenishSuggestion> pendings = suggestionMapper.selectByExample(example);
        Date now = new Date();
        for (ReplenishSuggestion s : pendings) {
            long ageHours = (now.getTime() - s.getCreateTime().getTime()) / (1000 * 60 * 60);
            if (ageHours > expireHours) {
                int before = s.getStatus();
                s.setStatus(SuggestionStatus.EXPIRED);
                s.setModifiedTime(now);
                suggestionMapper.updateByPrimaryKeySelective(s);
                recordAudit(s, SuggestionAuditAction.EXPIRED, before, SuggestionStatus.EXPIRED, null);
            }
        }
    }

    @Override
    public List<SuggestionAuditVO> getTrace(String suggestionNum) {
        List<SuggestionAudit> audits = auditMapper.findBySuggestionNum(suggestionNum);
        List<SuggestionAuditVO> voList = new ArrayList<>();
        for (SuggestionAudit audit : audits) {
            SuggestionAuditVO vo = new SuggestionAuditVO();
            BeanUtils.copyProperties(audit, vo);
            voList.add(vo);
        }
        return voList;
    }

    // ===== 内部方法 =====

    private List<ReplenishSuggestionVO> doGenerateForSnapshot(InventoryRiskSnapshot snapshot, ReplenishRuleVersion rule) {
        List<ReplenishSuggestionVO> results = new ArrayList<>();
        String pNum = snapshot.getPNum();

        // 收集优先批次(近效期 + FIFO)
        String priorityBatches = buildPriorityBatches(pNum, rule);

        // 尝试生成调拨建议
        ReplenishSuggestionVO transferSuggestion = tryGenerateTransferSuggestion(pNum, snapshot, rule, priorityBatches);
        if (transferSuggestion != null) {
            results.add(transferSuggestion);
        }

        // 生成采购建议(高风险或无调拨来源时)
        if (transferSuggestion == null || RiskLevel.HIGH.equals(snapshot.getRiskLevel())
                || RiskLevel.CRITICAL.equals(snapshot.getRiskLevel())) {
            ReplenishSuggestionVO purchaseSuggestion = tryGeneratePurchaseSuggestion(pNum, snapshot, rule, priorityBatches);
            if (purchaseSuggestion != null) {
                results.add(purchaseSuggestion);
            }
        }

        return results;
    }

    private ReplenishSuggestionVO tryGenerateTransferSuggestion(String pNum, InventoryRiskSnapshot snapshot,
                                                                  ReplenishRuleVersion rule, String priorityBatches) {
        // 幂等检查
        List<ReplenishSuggestion> existing = suggestionMapper.findActiveByPNumAndType(
                pNum, SuggestionType.TRANSFER, rule.getSuggestionDedupHours());
        if (existing != null && !existing.isEmpty()) {
            return null;
        }

        // 查找有富余的部门:检查其他调拨申请中的部门,找到该物资库存充裕的来源
        // 查看已完成调拨的来源部门,这些部门可能有该物资的富余
        Example transferExample = new Example(TransferRequest.class);
        transferExample.createCriteria()
                .andEqualTo("pNum", pNum)
                .andEqualTo("status", TransferStatus.COMPLETED);
        transferExample.setOrderByClause("create_time desc");
        List<TransferRequest> pastTransfers = transferRequestMapper.selectByExample(transferExample);

        // 收集潜在的来源部门(过去的发出方)
        Set<String> potentialSources = new LinkedHashSet<>();
        for (TransferRequest t : pastTransfers) {
            potentialSources.add(t.getFromDepartment());
        }

        if (potentialSources.isEmpty()) {
            return null;
        }

        // 选择第一个可用的来源部门
        String fromDepartment = potentialSources.iterator().next();
        // 目标部门:过去的接收方或默认
        String toDepartment = null;
        for (TransferRequest t : pastTransfers) {
            toDepartment = t.getToDepartment();
            break;
        }
        if (toDepartment == null) {
            toDepartment = "默认仓库";
        }

        // 计算建议数量
        long suggestedQty = calculateSuggestedQuantity(snapshot, rule, 0);
        if (suggestedQty <= 0) {
            suggestedQty = 1;
        }

        ReplenishSuggestion suggestion = new ReplenishSuggestion();
        suggestion.setSuggestionNum("SUG-" + UUID.randomUUID().toString().substring(0, 28).replace("-", ""));
        suggestion.setPNum(pNum);
        suggestion.setSuggestionType(SuggestionType.TRANSFER);
        suggestion.setRiskSnapshotId(snapshot.getId());
        suggestion.setRiskLevel(snapshot.getRiskLevel());
        suggestion.setSuggestedQuantity(suggestedQty);
        suggestion.setFromDepartment(fromDepartment);
        suggestion.setToDepartment(toDepartment);
        suggestion.setPriorityBatches(priorityBatches);
        suggestion.setReason(String.format("物资%s可用天数%.1f天,风险等级%s,建议从%s调拨",
                pNum, snapshot.getAvailableDays() != null ? snapshot.getAvailableDays().doubleValue() : 0,
                snapshot.getRiskLevel(), fromDepartment));
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setCreateTime(new Date());
        suggestion.setModifiedTime(new Date());

        suggestionMapper.insertSelective(suggestion);
        recordAudit(suggestion, SuggestionAuditAction.CREATED, null, SuggestionStatus.PENDING, null);

        return convertToVO(suggestion);
    }

    private ReplenishSuggestionVO tryGeneratePurchaseSuggestion(String pNum, InventoryRiskSnapshot snapshot,
                                                                 ReplenishRuleVersion rule, String priorityBatches) {
        // 幂等检查
        List<ReplenishSuggestion> existing = suggestionMapper.findActiveByPNumAndType(
                pNum, SuggestionType.PURCHASE, rule.getSuggestionDedupHours());
        if (existing != null && !existing.isEmpty()) {
            return null;
        }

        // 查找最近的供应商
        Long supplierId = findRecentSupplier(pNum);
        int estimatedLeadDays = deliveryStatService.getEstimatedLeadDays(supplierId, pNum);

        // 计算建议数量
        long suggestedQty = calculateSuggestedQuantity(snapshot, rule, estimatedLeadDays);
        if (suggestedQty <= 0) {
            suggestedQty = 1;
        }

        ReplenishSuggestion suggestion = new ReplenishSuggestion();
        suggestion.setSuggestionNum("SUG-" + UUID.randomUUID().toString().substring(0, 28).replace("-", ""));
        suggestion.setPNum(pNum);
        suggestion.setSuggestionType(SuggestionType.PURCHASE);
        suggestion.setRiskSnapshotId(snapshot.getId());
        suggestion.setRiskLevel(snapshot.getRiskLevel());
        suggestion.setSuggestedQuantity(suggestedQty);
        suggestion.setSupplierId(supplierId);
        suggestion.setEstimatedLeadDays(estimatedLeadDays);
        suggestion.setPriorityBatches(priorityBatches);
        suggestion.setReason(String.format("物资%s可用天数%.1f天,风险等级%s,建议采购补货,预计交货%d天",
                pNum, snapshot.getAvailableDays() != null ? snapshot.getAvailableDays().doubleValue() : 0,
                snapshot.getRiskLevel(), estimatedLeadDays));
        suggestion.setStatus(SuggestionStatus.PENDING);
        suggestion.setCreateTime(new Date());
        suggestion.setModifiedTime(new Date());

        suggestionMapper.insertSelective(suggestion);
        recordAudit(suggestion, SuggestionAuditAction.CREATED, null, SuggestionStatus.PENDING, null);

        return convertToVO(suggestion);
    }

    private long calculateSuggestedQuantity(InventoryRiskSnapshot snapshot, ReplenishRuleVersion rule, int leadDays) {
        BigDecimal rate = snapshot.getDailyConsumptionRate();
        if (rate == null || rate.compareTo(BigDecimal.ZERO) == 0) {
            return rule.getSafetyStockDays(); // 无消耗数据时,按安全天数给默认值
        }
        // 建议量 = (安全天数 × 日消耗) - 可用库存 + (交货天数 × 日消耗)
        BigDecimal safetyNeed = rate.multiply(BigDecimal.valueOf(rule.getSafetyStockDays()));
        BigDecimal leadNeed = rate.multiply(BigDecimal.valueOf(leadDays));
        BigDecimal available = BigDecimal.valueOf(snapshot.getAvailableStock());
        BigDecimal suggested = safetyNeed.subtract(available).add(leadNeed);
        return Math.max(suggested.setScale(0, RoundingMode.CEILING).longValue(), 1);
    }

    private String buildPriorityBatches(String pNum, ReplenishRuleVersion rule) {
        List<String> batchNumbers = new ArrayList<>();

        // 近效期优先
        List<ProductBatch> nearExpiry = productBatchMapper.findNearExpiryBatchesByPNum(pNum, rule.getNearExpiryDays());
        if (nearExpiry != null) {
            for (ProductBatch b : nearExpiry) {
                batchNumbers.add(b.getBatchNumber());
            }
        }

        // FIFO: 按生产日期排序的其他批次
        List<ProductBatch> fifoBatches = productBatchMapper.findAvailableBatches(pNum, "production_date ASC");
        if (fifoBatches != null) {
            for (ProductBatch b : fifoBatches) {
                if (!batchNumbers.contains(b.getBatchNumber())) {
                    batchNumbers.add(b.getBatchNumber());
                }
            }
        }

        return batchNumbers.isEmpty() ? null : String.join(",", batchNumbers);
    }

    private Long findRecentSupplier(String pNum) {
        // 查找该物资最近入库批次的供应商
        Example batchExample = new Example(ProductBatch.class);
        batchExample.createCriteria()
                .andEqualTo("pNum", pNum)
                .andIsNotNull("supplierId");
        batchExample.setOrderByClause("create_time desc");
        List<ProductBatch> batches = productBatchMapper.selectByExample(batchExample);
        if (batches != null && !batches.isEmpty()) {
            return batches.get(0).getSupplierId();
        }
        return null;
    }

    private void recordAudit(ReplenishSuggestion suggestion, String action,
                             Integer beforeStatus, int afterStatus, String remark) {
        SuggestionAudit audit = new SuggestionAudit();
        audit.setSuggestionId(suggestion.getId());
        audit.setSuggestionNum(suggestion.getSuggestionNum());
        audit.setAction(action);
        audit.setBeforeStatus(beforeStatus);
        audit.setAfterStatus(afterStatus);
        audit.setOperator("SYSTEM");
        audit.setRemark(remark);
        audit.setEventTime(new Date());
        auditMapper.insertSelective(audit);
    }

    private List<ReplenishSuggestionVO> enrichSuggestionVOs(List<ReplenishSuggestion> suggestions) {
        // 批量查产品名和供应商名
        Set<String> pNums = new HashSet<>();
        Set<Long> supplierIds = new HashSet<>();
        for (ReplenishSuggestion s : suggestions) {
            pNums.add(s.getPNum());
            if (s.getSupplierId() != null) {
                supplierIds.add(s.getSupplierId());
            }
        }

        Map<String, String> productNameMap = new HashMap<>();
        if (!pNums.isEmpty()) {
            Example pe = new Example(Product.class);
            pe.createCriteria().andIn("pNum", pNums);
            List<Product> products = productMapper.selectByExample(pe);
            for (Product p : products) {
                productNameMap.put(p.getPNum(), p.getName());
            }
        }

        Map<Long, String> supplierNameMap = new HashMap<>();
        if (!supplierIds.isEmpty()) {
            for (Long sid : supplierIds) {
                Supplier sup = supplierMapper.selectByPrimaryKey(sid);
                if (sup != null) {
                    supplierNameMap.put(sid, sup.getName());
                }
            }
        }

        List<ReplenishSuggestionVO> voList = new ArrayList<>();
        for (ReplenishSuggestion s : suggestions) {
            ReplenishSuggestionVO vo = convertToVO(s);
            vo.setProductName(productNameMap.get(s.getPNum()));
            if (s.getSupplierId() != null) {
                vo.setSupplierName(supplierNameMap.get(s.getSupplierId()));
            }
            voList.add(vo);
        }
        return voList;
    }

    private void enrichSingleVO(ReplenishSuggestionVO vo, ReplenishSuggestion suggestion) {
        // 产品名称
        Example pe = new Example(Product.class);
        pe.createCriteria().andEqualTo("pNum", suggestion.getPNum());
        Product product = productMapper.selectOneByExample(pe);
        if (product != null) {
            vo.setProductName(product.getName());
        }
        // 供应商名称
        if (suggestion.getSupplierId() != null) {
            Supplier supplier = supplierMapper.selectByPrimaryKey(suggestion.getSupplierId());
            if (supplier != null) {
                vo.setSupplierName(supplier.getName());
            }
        }
    }

    private ReplenishSuggestionVO convertToVO(ReplenishSuggestion suggestion) {
        ReplenishSuggestionVO vo = new ReplenishSuggestionVO();
        BeanUtils.copyProperties(suggestion, vo);
        return vo;
    }
}
