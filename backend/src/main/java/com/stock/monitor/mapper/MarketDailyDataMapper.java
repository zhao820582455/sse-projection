package com.stock.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stock.monitor.entity.MarketDailyData;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;

public interface MarketDailyDataMapper extends BaseMapper<MarketDailyData> {

    @Select("SELECT * FROM market_daily_data WHERE trade_date = #{tradeDate}")
    MarketDailyData selectByTradeDate(@Param("tradeDate") LocalDate tradeDate);

    @Select("SELECT * FROM market_daily_data WHERE trade_date <= #{endDate} AND trade_date >= #{startDate} ORDER BY trade_date DESC")
    List<MarketDailyData> selectByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    @Select("SELECT * FROM market_daily_data ORDER BY trade_date DESC LIMIT #{limit}")
    List<MarketDailyData> selectRecent(@Param("limit") int limit);

    @Select("SELECT trade_date FROM market_daily_data ORDER BY trade_date DESC LIMIT 1")
    LocalDate selectLatestTradeDate();
}
