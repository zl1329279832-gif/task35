package com.coderman.business.mapper;

import com.coderman.common.model.business.ReplenishmentSuggestion;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

/**
 * 补货/调拨建议单Mapper
 */
public interface ReplenishmentSuggestionMapper extends Mapper<ReplenishmentSuggestion> {

    /**
     * 通过幂等键查找建议单
     */
    ReplenishmentSuggestion findByIdempotentKey(@Param("idempotentKey") String idempotentKey);
}
