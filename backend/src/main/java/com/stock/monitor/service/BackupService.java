package com.stock.monitor.service;

/**
 * 数据备份服务
 */
public interface BackupService {

    /**
     * 执行全量数据备份
     */
    void performBackup();

    /**
     * 清理过期备份文件
     */
    void cleanOldBackups(int retentionDays);

    /**
     * 清理过期非核心数据
     */
    void cleanOldData(int retentionYears);
}
