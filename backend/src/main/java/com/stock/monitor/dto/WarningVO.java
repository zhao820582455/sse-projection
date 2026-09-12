package com.stock.monitor.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarningVO {

    /** 预警ID */
    private Long id;

    /** 交易日期 */
    private LocalDate tradeDate;

    /** 预警编码 */
    private String warningCode;

    /** 预警名称 */
    private String warningName;

    /** 预警等级 */
    private String warningLevel;

    /** 标的名称 */
    private String targetName;

    /** 是否触发 */
    private Integer isTriggered;

    /** 触发依据 */
    private String triggerBasis;

    /** 状态 */
    private String status;

    /** 触发时间 */
    private LocalDateTime createTime;
}
