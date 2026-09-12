package com.stock.monitor.vo;

import java.math.BigDecimal;

/**
 * 长期投资基金分析 VO
 * 从板块长期趋势维度分析基金投资机会
 */
public class LongTermFundVO {

    // ===== 板块信息 =====
    private String sectorName;       // 板块名称
    private String sectorCode;       // 板块代码 (BKxxxx)
    private String sectorType;       // 行业 / 概念

    // ===== 趋势指标 =====
    private BigDecimal latestPrice;  // 最新价
    private BigDecimal pct1M;        // 近1月涨跌幅%
    private BigDecimal pct3M;        // 近3月涨跌幅%
    private BigDecimal pct6M;        // 近6月涨跌幅%
    private BigDecimal ma20;         // MA20
    private BigDecimal ma60;         // MA60

    // ===== 趋势分类 =====
    /** 趋势标签: "多头排列-强势上涨" / "多头排列-温和上涨" /
     *           "空头排列-持续下跌" / "空头排列-加速下跌" /
     *           "震荡整理" / "底部企稳" */
    private String trendLabel;
    /** 投资策略: MOMENTUM(顺势持有) / CONTRARIAN(逆势定投) / NEUTRAL(观望) */
    private String strategy;
    /** 趋势强度 0-100，越高越值得关注 */
    private Integer trendScore;

    // ===== 匹配基金 =====
    private String fundName;         // 推荐ETF名称
    private String fundCode;         // ETF代码
    private BigDecimal fundPrice;    // ETF价格
    private BigDecimal fundChangePct;// ETF涨跌幅%
    private BigDecimal fundScale;    // ETF规模(亿)

    // ===== Getters & Setters =====
    public String getSectorName() { return sectorName; }
    public void setSectorName(String sectorName) { this.sectorName = sectorName; }

    public String getSectorCode() { return sectorCode; }
    public void setSectorCode(String sectorCode) { this.sectorCode = sectorCode; }

    public String getSectorType() { return sectorType; }
    public void setSectorType(String sectorType) { this.sectorType = sectorType; }

    public BigDecimal getLatestPrice() { return latestPrice; }
    public void setLatestPrice(BigDecimal latestPrice) { this.latestPrice = latestPrice; }

    public BigDecimal getPct1M() { return pct1M; }
    public void setPct1M(BigDecimal pct1M) { this.pct1M = pct1M; }

    public BigDecimal getPct3M() { return pct3M; }
    public void setPct3M(BigDecimal pct3M) { this.pct3M = pct3M; }

    public BigDecimal getPct6M() { return pct6M; }
    public void setPct6M(BigDecimal pct6M) { this.pct6M = pct6M; }

    public BigDecimal getMa20() { return ma20; }
    public void setMa20(BigDecimal ma20) { this.ma20 = ma20; }

    public BigDecimal getMa60() { return ma60; }
    public void setMa60(BigDecimal ma60) { this.ma60 = ma60; }

    public String getTrendLabel() { return trendLabel; }
    public void setTrendLabel(String trendLabel) { this.trendLabel = trendLabel; }

    public String getStrategy() { return strategy; }
    public void setStrategy(String strategy) { this.strategy = strategy; }

    public Integer getTrendScore() { return trendScore; }
    public void setTrendScore(Integer trendScore) { this.trendScore = trendScore; }

    public String getFundName() { return fundName; }
    public void setFundName(String fundName) { this.fundName = fundName; }

    public String getFundCode() { return fundCode; }
    public void setFundCode(String fundCode) { this.fundCode = fundCode; }

    public BigDecimal getFundPrice() { return fundPrice; }
    public void setFundPrice(BigDecimal fundPrice) { this.fundPrice = fundPrice; }

    public BigDecimal getFundChangePct() { return fundChangePct; }
    public void setFundChangePct(BigDecimal fundChangePct) { this.fundChangePct = fundChangePct; }

    public BigDecimal getFundScale() { return fundScale; }
    public void setFundScale(BigDecimal fundScale) { this.fundScale = fundScale; }
}
