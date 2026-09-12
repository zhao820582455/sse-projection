package com.stock.monitor.vo;

import java.math.BigDecimal;

/**
 * 基金/ETF推荐VO
 */
public class FundVO {
    /** 基金名称 */
    private String name;
    /** 基金代码 */
    private String code;
    /** 最新价 */
    private BigDecimal price;
    /** 涨跌幅(%) */
    private BigDecimal changePct;
    /** 总市值(亿元) */
    private BigDecimal totalCap;
    /** 基金类型: broad(宽基) / sector(行业) / theme(主题) */
    private String fundType;
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

    public BigDecimal getTotalCap() { return totalCap; }
    public void setTotalCap(BigDecimal totalCap) { this.totalCap = totalCap; }

    public String getFundType() { return fundType; }
    public void setFundType(String fundType) { this.fundType = fundType; }

    public Integer getRanking() { return ranking; }
    public void setRanking(Integer ranking) { this.ranking = ranking; }
}
