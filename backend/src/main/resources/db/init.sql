-- =====================================================
-- A股实时行情信号跟踪系统 - 数据库初始化脚本
-- 数据库名称: stock_monitor
-- 字符集: utf8mb4
-- =====================================================

CREATE DATABASE IF NOT EXISTS stock_monitor DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE stock_monitor;

-- ------------------------------------------------
-- 1. 每日市场行情数据表
-- ------------------------------------------------
CREATE TABLE IF NOT EXISTS market_daily_data (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    trade_date DATE NOT NULL COMMENT '交易日期',
    sh_index DECIMAL(10,2) COMMENT '上证指数',
    sh_index_change DECIMAL(10,4) COMMENT '上证指数涨跌幅(%)',
    total_volume DECIMAL(16,2) COMMENT '两市成交额(亿元)',
    total_turnover DECIMAL(16,0) COMMENT '两市成交量(万手)',
    north_flow DECIMAL(12,2) COMMENT '北向资金净流入(亿元)',
    rise_count INT COMMENT '上涨家数',
    fall_count INT COMMENT '下跌家数',
    limit_down_count INT COMMENT '跌停家数',
    margin_balance DECIMAL(12,2) COMMENT '两融余额(亿元)',
    margin_balance_change DECIMAL(10,4) COMMENT '两融余额变化率(%)',
    etf_net_subscription DECIMAL(12,2) COMMENT '宽基ETF净申购(亿元)',
    usd_index DECIMAL(8,4) COMMENT '美元指数',
    us_bond_yield DECIMAL(8,4) COMMENT '10年期美债收益率(%)',
    sox_index DECIMAL(10,2) COMMENT '费城半导体指数',
    sox_index_change DECIMAL(10,4) COMMENT '半导体指数涨跌幅(%)',
    global_market_status TEXT COMMENT '全球股指状态(JSON)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    UNIQUE KEY uk_trade_date (trade_date)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日市场行情数据表';

-- ------------------------------------------------
-- 2. 反弹信号记录表
-- ------------------------------------------------
CREATE TABLE IF NOT EXISTS rebound_signal_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    trade_date DATE NOT NULL COMMENT '交易日期',
    signal_code VARCHAR(50) NOT NULL COMMENT '信号编码',
    signal_name VARCHAR(100) NOT NULL COMMENT '信号名称',
    is_triggered TINYINT DEFAULT 0 COMMENT '是否达标: 0-否, 1-是',
    score INT DEFAULT 0 COMMENT '信号分值',
    data_source TEXT COMMENT '数据来源描述',
    remark VARCHAR(500) COMMENT '备注',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_trade_date (trade_date),
    KEY idx_signal_code (signal_code),
    UNIQUE KEY uk_date_signal (trade_date, signal_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='反弹信号记录表';

-- ------------------------------------------------
-- 3. 高位见顶预警记录表
-- ------------------------------------------------
CREATE TABLE IF NOT EXISTS top_warning_record (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    trade_date DATE NOT NULL COMMENT '交易日期',
    warning_code VARCHAR(50) NOT NULL COMMENT '预警编码',
    warning_name VARCHAR(100) NOT NULL COMMENT '预警名称',
    warning_level VARCHAR(20) NOT NULL COMMENT '预警等级: 一级预警/二级预警/三级预警',
    target_name VARCHAR(100) COMMENT '标的名称',
    is_triggered TINYINT DEFAULT 0 COMMENT '是否触发: 0-否, 1-是',
    trigger_basis TEXT COMMENT '触发依据',
    status VARCHAR(20) DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/RESOLVED',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_trade_date (trade_date),
    KEY idx_warning_level (warning_level),
    KEY idx_status (status),
    UNIQUE KEY uk_date_warning (trade_date, warning_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='高位见顶预警记录表';

-- ------------------------------------------------
-- 4. 系统配置表
-- ------------------------------------------------
CREATE TABLE IF NOT EXISTS system_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    config_key VARCHAR(100) NOT NULL COMMENT '配置键',
    config_value TEXT COMMENT '配置值',
    description VARCHAR(255) COMMENT '配置描述',
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ------------------------------------------------
-- 初始化系统配置数据
-- ------------------------------------------------
INSERT IGNORE INTO system_config (config_key, config_value, description) VALUES
('score_threshold_active', '80', '积极做多得分阈值'),
('score_threshold_positive', '60', '谨慎乐观得分阈值'),
('score_threshold_neutral', '40', '观望为主得分阈值'),
('north_flow_threshold', '30', '北向资金阈值(亿)'),
('etf_sub_threshold', '20', 'ETF净申购阈值(亿)'),
('volume_surge_pct', '10', '成交量放量阈值(%)'),
('limit_down_threshold', '10', '跌停数量阈值'),
('backup_retention_days', '30', '备份保留天数'),
('data_retention_years', '3', '数据保留年限');
