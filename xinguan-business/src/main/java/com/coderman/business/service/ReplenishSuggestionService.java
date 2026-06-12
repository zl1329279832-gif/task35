package com.coderman.business.service;

import com.coderman.common.vo.system.PageVO;
import com.coderman.common.vo.business.ReplenishSuggestionVO;
import com.coderman.common.vo.business.SuggestionAuditVO;

import java.util.List;

/**
 * 补货建议服务
 */
public interface ReplenishSuggestionService {

    /**
     * 生成所有物资的补货建议
     */
    List<ReplenishSuggestionVO> generateSuggestions();

    /**
     * 生成单个物资的补货建议
     */
    List<ReplenishSuggestionVO> generateSuggestionsForProduct(String pNum);

    /**
     * 分页查询建议列表
     */
    PageVO<ReplenishSuggestionVO> findSuggestions(Integer pageNum, Integer pageSize,
                                                   String pNum, String suggestionType, Integer status);

    /**
     * 获取建议详情(含审计轨迹)
     */
    ReplenishSuggestionVO getDetail(Long id);

    /**
     * 采纳建议
     */
    void adopt(Long id);

    /**
     * 拒绝建议
     */
    void reject(Long id, String reason);

    /**
     * 调拨被拒绝时的回调
     */
    void onTransferRejected(String transferNum);

    /**
     * 标记已执行
     */
    void markExecuted(Long id, String relatedNum);

    /**
     * 过期处理
     */
    void expireStaleSuggestions();

    /**
     * 查询建议追溯
     */
    List<SuggestionAuditVO> getTrace(String suggestionNum);
}
