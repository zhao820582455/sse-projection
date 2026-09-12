package com.stock.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stock.monitor.entity.TopWarningRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface TopWarningRecordMapper extends BaseMapper<TopWarningRecord> {

    @Select("SELECT * FROM top_warning_record WHERE trade_date = #{tradeDate} AND is_triggered = 1 ORDER BY warning_level ASC")
    List<TopWarningRecord> selectTriggeredByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    @Select("SELECT * FROM top_warning_record WHERE is_triggered = 1 AND status = 'ACTIVE' ORDER BY warning_level ASC")
    List<TopWarningRecord> selectActiveWarnings();

    @Select("SELECT * FROM top_warning_record WHERE trade_date <= #{endDate} AND trade_date >= #{startDate} AND is_triggered = 1 ORDER BY trade_date DESC, warning_level ASC")
    List<TopWarningRecord> selectWarningHistory(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Select("SELECT * FROM top_warning_record WHERE trade_date = #{tradeDate} ORDER BY warning_level ASC")
    List<TopWarningRecord> selectByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    @Select("DELETE FROM top_warning_record WHERE trade_date = #{tradeDate}")
    void deleteByTradeDate(@Param("tradeDate") LocalDate tradeDate);
}
