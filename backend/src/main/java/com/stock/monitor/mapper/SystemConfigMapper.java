package com.stock.monitor.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.stock.monitor.entity.SystemConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SystemConfigMapper extends BaseMapper<SystemConfig> {

    @Select("SELECT config_value FROM system_config WHERE config_key = #{configKey}")
    String selectValueByKey(@Param("configKey") String configKey);
}
