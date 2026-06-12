package com.coderman.business.mapper;

import com.coderman.common.model.business.SuggestionAudit;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * 建议审计Mapper
 */
public interface SuggestionAuditMapper extends Mapper<SuggestionAudit> {

    /**
     * 按建议ID查询审计记录
     */
    List<SuggestionAudit> findBySuggestionId(@Param("suggestionId") Long suggestionId);

    /**
     * 按建议单号查询审计记录
     */
    List<SuggestionAudit> findBySuggestionNum(@Param("suggestionNum") String suggestionNum);
}
