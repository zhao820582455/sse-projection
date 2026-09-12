package com.stock.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stock.monitor.entity.ReboundSignalRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface ReboundSignalRecordMapper extends BaseMapper<ReboundSignalRecord> {

    @Select("SELECT * FROM rebound_signal_record WHERE trade_date = #{tradeDate} ORDER BY id ASC")
    List<ReboundSignalRecord> selectByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    @Select("SELECT trade_date, SUM(CASE WHEN is_triggered = 1 THEN score ELSE 0 END) AS total_score " +
            "FROM rebound_signal_record " +
            "WHERE trade_date <= #{endDate} AND trade_date >= #{startDate} " +
            "GROUP BY trade_date ORDER BY trade_date DESC")
    List<java.util.Map<String, Object>> selectScoreHistory(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Select("DELETE FROM rebound_signal_record WHERE trade_date = #{tradeDate}")
    void deleteByTradeDate(@Param("tradeDate") LocalDate tradeDate);
}
