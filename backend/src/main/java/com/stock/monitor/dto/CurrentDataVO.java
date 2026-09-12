package com.stock.monitor.dto;

import com.stock.monitor.entity.MarketDailyData;
import com.stock.monitor.entity.ReboundSignalRecord;
import com.stock.monitor.entity.TopWarningRecord;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CurrentDataVO {

    /** 市场行情数据 */
    private MarketDailyData marketData;

    /** 12项反弹信号状态 */
    private List<ReboundSignalRecord> signals;

    /** 当前总得分 */
    private Integer totalScore;

    /** 得分等级 */
    private String scoreLevel;

    /** 仓位操作建议 */
    private Map<String, Object> suggestion;

    /** 当前触发的预警 */
    private List<TopWarningRecord> warnings;
}
