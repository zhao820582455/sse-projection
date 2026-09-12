package com.stock.monitor.service;

import com.stock.monitor.entity.ReboundSignalRecord;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 信号校验与打分服务
 */
public interface SignalService {

    /**
     * 校验所有12项反弹信号并计算总分
     */
    List<ReboundSignalRecord> checkAllSignals(LocalDate tradeDate);

    /**
     * 计算当日总得分
     */
    int calculateTotalScore(LocalDate tradeDate);

    /**
     * 根据得分获取仓位操作建议
     */
    Map<String, Object> getSuggestion(int score);

    /**
     * 根据得分获取等级
     */
    String getScoreLevel(int score);

    /**
     * 获取历史得分数据
     */
    List<Map<String, Object>> getScoreHistory(LocalDate startDate, LocalDate endDate);

    /**
     * 手动修正信号状态
     */
    void manualUpdateSignal(LocalDate tradeDate, String signalCode, Integer isTriggered, String remark);

    /**
     * 获取当天所有信号详情
     */
    List<ReboundSignalRecord> getSignalsByDate(LocalDate tradeDate);
}
