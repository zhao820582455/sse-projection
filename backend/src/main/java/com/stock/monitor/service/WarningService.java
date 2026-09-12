package com.stock.monitor.service;

import com.stock.monitor.entity.TopWarningRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 高位见顶预警服务
 */
public interface WarningService {

    /**
     * 检查所有7类预警
     */
    List<TopWarningRecord> checkAllWarnings(LocalDate tradeDate);

    /**
     * 获取当前触发的所有预警
     */
    List<TopWarningRecord> getCurrentWarnings();

    /**
     * 获取历史预警记录
     */
    List<TopWarningRecord> getWarningHistory(LocalDate startDate, LocalDate endDate);

    /**
     * 检查强制止损规则
     */
    List<Map<String, String>> checkStopLossRules(LocalDate tradeDate);

    /**
     * 更新预警状态
     */
    void updateWarningStatus(Long id, String status);
}
