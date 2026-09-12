package com.stock.monitor.vo;

import java.math.BigDecimal;

/**
 * 板块推荐VO
 */
public class SectorVO {
    /** 板块名称 */
    private String name;
    /** 板块代码 */
    private String code;
    /** 板块类型: industry(行业) / concept(概念) */
    private String type;
    /** 涨跌幅(%) */
    private BigDecimal changePct;
    /** 主力净流入(亿元) */
    private BigDecimal mainFlow;
    /** 排名(1=N) */
    private Integer ranking;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public BigDecimal getChangePct() { return changePct; }
    public void setChangePct(BigDecimal changePct) { this.changePct = changePct; }

    public BigDecimal getMainFlow() { return mainFlow; }
    public void setMainFlow(BigDecimal mainFlow) { this.mainFlow = mainFlow; }

    public Integer getRanking() { return ranking; }
    public void setRanking(Integer ranking) { this.ranking = ranking; }
}
