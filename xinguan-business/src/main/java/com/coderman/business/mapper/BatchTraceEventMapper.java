package com.coderman.business.mapper;

import com.coderman.common.model.business.BatchTraceEvent;
import org.apache.ibatis.annotations.Param;
import tk.mybatis.mapper.common.Mapper;

import java.util.List;

/**
 * 批次追溯事件Mapper
 */
public interface BatchTraceEventMapper extends Mapper<BatchTraceEvent> {

    /**
     * 按批次号查询全链路追溯事件（按时间升序）
     */
    List<BatchTraceEvent> findByBatchNumber(@Param("batchNumber") String batchNumber);

    /**
     * 按关联业务单号查询事件
     */
    List<BatchTraceEvent> findByRelatedNum(@Param("relatedNum") String relatedNum);

    /**
     * 按事件类型查询
     */
    List<BatchTraceEvent> findByEventType(@Param("eventType") String eventType);
}
