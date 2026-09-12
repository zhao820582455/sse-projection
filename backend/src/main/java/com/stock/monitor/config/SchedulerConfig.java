package com.stock.monitor.config;

import com.stock.monitor.service.BackupService;
import com.stock.monitor.service.DataCollectionService;
import com.stock.monitor.service.SignalService;
import com.stock.monitor.service.WarningService;
import com.stock.monitor.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 定时任务调度配置
 * 使用Spring @Scheduled注解实现定时数据采集和备份
 */
@Configuration
@ConditionalOnProperty(name = "collector.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);

    @Resource
    private DataCollectionService dataCollectionService;

    @Resource
    private SignalService signalService;

    @Resource
    private WarningService warningService;

    @Resource
    private BackupService backupService;

    @Value("${backup.retention-days:30}")
    private int backupRetentionDays;

    /**
     * 系统启动时初始化 - 如果当天是交易日，立即采集一次数据
     */
    @PostConstruct
    public void init() {
        try {
            LocalDate today = DateUtil.getLatestTradingDay();
            log.info("系统初始化 - 当前交易日: {}", today);

            // 启动时采集一次数据
            if (DateUtil.isInTradingHours() || DateUtil.isAfterMarketClose()) {
                log.info("交易时段内启动，开始初始数据采集...");
                dataCollectionService.collectDataForDate(today);
                signalService.checkAllSignals(today);
                warningService.checkAllWarnings(today);
            }
        } catch (Exception e) {
            log.error("系统初始化数据采集失败: {}", e.getMessage());
        }
    }

    /**
     * 交易时段内每30分钟采集一次数据
     * 周一到周五 9:30-15:00 每30分钟执行
     */
    @Scheduled(cron = "0 0,30 9,10,11,13,14 * * MON-FRI")
    public void collectDataDuringTrading() {
        LocalTime now = LocalTime.now();
        LocalTime open = LocalTime.of(9, 30);
        LocalTime close = LocalTime.of(15, 0);

        if (now.isBefore(open) || now.isAfter(close)) {
            return;
        }

        try {
            log.info("定时数据采集触发 - 时间: {}", now);
            LocalDate today = DateUtil.getCurrentTradingDay();

            dataCollectionService.collectDataForDate(today);
            signalService.checkAllSignals(today);
            warningService.checkAllWarnings(today);

            log.info("定时数据采集完成");
        } catch (Exception e) {
            log.error("定时数据采集失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 15:00 收盘后立即采集最终数据
     */
    @Scheduled(cron = "0 0 15 * * MON-FRI")
    public void finalMarketCloseCollection() {
        try {
            log.info("收盘数据采集触发");
            LocalDate today = DateUtil.getCurrentTradingDay();

            dataCollectionService.collectDataForDate(today);
            signalService.checkAllSignals(today);
            warningService.checkAllWarnings(today);

            log.info("收盘数据采集完成");
        } catch (Exception e) {
            log.error("收盘数据采集失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 15:10 最终校准和备份
     */
    @Scheduled(cron = "0 10 15 * * MON-FRI")
    public void finalCalibrationAndBackup() {
        try {
            log.info("收盘校准和备份触发");
            LocalDate today = DateUtil.getCurrentTradingDay();

            // 最后一次校准
            dataCollectionService.collectDataForDate(today);
            signalService.checkAllSignals(today);
            warningService.checkAllWarnings(today);

            // 执行备份
            backupService.performBackup();

            log.info("收盘校准和备份完成");
        } catch (Exception e) {
            log.error("收盘校准和备份失败: {}", e.getMessage(), e);
        }
    }

    /**
     * 每周日02:00清理过期备份和数据
     */
    @Scheduled(cron = "0 0 2 * * SUN")
    public void weeklyCleanup() {
        try {
            log.info("周度数据清理触发");
            backupService.cleanOldBackups(backupRetentionDays);
            backupService.cleanOldData(3); // 保留3年核心数据
            log.info("周度数据清理完成");
        } catch (Exception e) {
            log.error("周度数据清理失败: {}", e.getMessage(), e);
        }
    }
}
