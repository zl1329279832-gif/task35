package com.coderman.business.service;

import com.coderman.common.vo.business.ReplenishmentSuggestionVO;
import com.coderman.common.vo.system.PageVO;

import java.util.List;

/**
 * 补货建议服务接口
 */
public interface ReplenishmentSuggestionService {

    /**
     * 基于快照生成补货/调拨建议
     * @param snapshotId 风险快照ID
     * @return 生成的建议列表
     */
    List<ReplenishmentSuggestionVO> generateSuggestions(Long snapshotId);

    /**
     * 采纳建议
     * @param id 建议单ID
     * @param operator 操作人
     * @param reason 原因
     */
    void adoptSuggestion(Long id, String operator, String reason);

    /**
     * 驳回建议
     * @param id 建议单ID
     * @param operator 操作人
     * @param reason 原因
     */
    void rejectSuggestion(Long id, String operator, String reason);

    /**
     * 分页查询建议列表
     * @param status 状态（可选）
     * @param suggestionType 类型（可选）
     * @param pageNum 页码
     * @param pageSize 每页大小
     * @return 建议列表
     */
    PageVO<ReplenishmentSuggestionVO> findSuggestions(Integer status, Integer suggestionType,
                                                       Integer pageNum, Integer pageSize);

    /**
     * 建议详情（含审计链和关联快照）
     * @param id 建议单ID
     * @return 建议详情
     */
    ReplenishmentSuggestionVO getSuggestionDetail(Long id);

    /**
     * 过期清理：超过N天的待处理建议自动过期
     * @param days 天数
     * @return 过期的建议数量
     */
    int expireOldSuggestions(int days);
}
