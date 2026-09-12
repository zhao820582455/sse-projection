package com.stock.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScoreVO {

    /** 交易日期 */
    private LocalDate tradeDate;

    /** 总得分 */
    private Integer totalScore;

    /** 得分等级 */
    private String scoreLevel;

    /** 达标信号数 */
    private Integer triggeredCount;
}
