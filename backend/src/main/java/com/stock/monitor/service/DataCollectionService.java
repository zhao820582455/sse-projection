package com.stock.monitor.service;

import com.stock.monitor.entity.MarketDailyData;

import java.time.LocalDate;

/**
 * 数据采集服务 - 从公开API拉取行情数据
 */
public interface DataCollectionService {

    /**
     * 采集当日全量行情数据
     */
    MarketDailyData collectDailyData();

    /**
     * 采集指定日期的行情数据
     */
    MarketDailyData collectDataForDate(LocalDate date);
}
