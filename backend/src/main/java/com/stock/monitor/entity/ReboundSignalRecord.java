package com.stock.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("rebound_signal_record")
public class ReboundSignalRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    /** 信号编码 */
    private String signalCode;

    /** 信号名称 */
    private String signalName;

    /** 是否达标 0-否 1-是 */
    private Integer isTriggered;

    /** 分值 */
    private Integer score;

    /** 数据来源 */
    private String dataSource;

    /** 备注 */
    private String remark;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
