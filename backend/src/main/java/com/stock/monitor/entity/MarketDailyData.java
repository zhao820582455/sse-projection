package com.stock.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("market_daily_data")
public class MarketDailyData {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    /** 上证指数 */
    private BigDecimal shIndex;

    /** 上证指数涨跌幅 */
    private BigDecimal shIndexChange;

    /** 两市成交额(亿) */
    private BigDecimal totalVolume;

    /** 两市成交量(手) */
    private BigDecimal totalTurnover;

    /** 北向资金净流入(亿) */
    private BigDecimal northFlow;

    /** 上涨家数 */
    private Integer riseCount;

    /** 下跌家数 */
    private Integer fallCount;

    /** 跌停家数 */
    private Integer limitDownCount;

    /** 两融余额(亿) */
    private BigDecimal marginBalance;

    /** 两融余额变化率 */
    private BigDecimal marginBalanceChange;

    /** 宽基ETF净申购(亿) */
    private BigDecimal etfNetSubscription;

    /** 美元指数 */
    private BigDecimal usdIndex;

    /** 10年期美债收益率 */
    private BigDecimal usBondYield;

    /** 费城半导体指数 */
    private BigDecimal soxIndex;

    /** 半导体指数涨跌幅 */
    private BigDecimal soxIndexChange;

    /** 全球股指状态(JSON) */
    private String globalMarketStatus;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
