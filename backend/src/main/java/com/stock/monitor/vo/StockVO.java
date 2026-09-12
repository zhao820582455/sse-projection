package com.stock.monitor.vo;

import java.math.BigDecimal;

/**
 * 个股推荐VO
 */
public class StockVO {
    /** 股票名称 */
    private String name;
    /** 股票代码 */
    private String code;
    /** 最新价 */
    private BigDecimal price;
    /** 涨跌幅(%) */
    private BigDecimal changePct;
    /** 换手率(%) */
    private BigDecimal turnoverRate;
    /** 量比 */
    private BigDecimal volumeRatio;
    /** 推荐原因 */
    private String reason;
    /** 推荐策略: north_flow(北向增持) / vol_breakout(量价突破) */
    private String strategy;
    /** 排名(1=N) */
    private Integer ranking;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public BigDecimal getChangePct() { return changePct; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }

    public BigDecimal getTurnoverRate() { return turnoverRate; }
    public void setTurnoverRate(BigDecimal turnoverRate) { this.turnoverRate = turnoverRate; }

    public BigDecimal getVolumeRatio() { return volumeRatio; }
    public void setVolumeRatio(BigDecimal volumeRatio) { this.volumeRatio = volumeRatio; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public Integer getRanking() { return ranking; }
    public void setRanking(Integer ranking) { this.ranking = ranking; }
}
