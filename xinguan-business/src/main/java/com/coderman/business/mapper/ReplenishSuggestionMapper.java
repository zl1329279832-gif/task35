package com.coderman.business.mapper;

import com.coderman.common.model.business.ReplenishSuggestion;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * 补货建议Mapper
 */
public interface ReplenishSuggestionMapper extends Mapper<ReplenishSuggestion> {

    /**
     * 查询去重窗口内未处理的建议
     */
    List<ReplenishSuggestion> findActiveByPNumAndType(
            @Param("pNum") String pNum,
            @Param("suggestionType") String suggestionType,
            @Param("dedupHours") int dedupHours);

    /**
     * 按关联调拨单号查询建议
     */
    ReplenishSuggestion findByRelatedTransferNum(@Param("transferNum") String transferNum);

    /**
     * 统计待处理建议数量
     */
    int countPending();
}
