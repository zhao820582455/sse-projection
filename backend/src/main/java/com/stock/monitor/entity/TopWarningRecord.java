package com.stock.monitor.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("top_warning_record")
public class TopWarningRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private LocalDate tradeDate;

    /** 预警编码 */
    private String warningCode;

    /** 预警名称 */
    private String warningName;

    /** 预警等级: 一级预警/二级预警/三级预警 */
    private String warningLevel;

    /** 标的名称 */
    private String targetName;

    /** 是否触发 0-否 1-是 */
    private Integer isTriggered;

    /** 触发依据 */
    private String triggerBasis;

    /** 状态: ACTIVE/RESOLVED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
