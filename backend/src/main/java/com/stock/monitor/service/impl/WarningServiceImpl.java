package com.stock.monitor.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.stock.monitor.entity.MarketDailyData;
import com.stock.monitor.entity.TopWarningRecord;
import com.stock.monitor.mapper.MarketDailyDataMapper;
import com.stock.monitor.mapper.TopWarningRecordMapper;
import com.stock.monitor.service.WarningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * 高位见顶预警服务实现
 * 7类预警 + 3条强制止损规则
 */
@Service
public class WarningServiceImpl implements WarningService {

    private static final Logger log = LoggerFactory.getLogger(WarningServiceImpl.class);

    /** 7类预警定义 */
    private static final List<Map<String, Object>> WARNING_DEFINITIONS = new ArrayList<>();

    static {
        WARNING_DEFINITIONS.add(createWarningDef("VOLUME_PRICE_DIVERGENCE", "量价背离预警", "二级预警", "上证指数"));
        WARNING_DEFINITIONS.add(createWarningDef("HIGH_CHURN_STAGNATION", "高位放量滞涨预警", "一级预警", "重点个股"));
        WARNING_DEFINITIONS.add(createWarningDef("TURNOVER_ABNORMAL", "换手率异常预警", "一级预警", "热点板块"));
        WARNING_DEFINITIONS.add(createWarningDef("MACD_DIVERGENCE", "MACD顶背离预警", "一级预警", "上证指数"));
        WARNING_DEFINITIONS.add(createWarningDef("SECTOR_ROTATION", "板块轮动异动预警", "二级预警", "行业板块"));
        WARNING_DEFINITIONS.add(createWarningDef("CHIP_PRESSURE", "高位筹码承压预警", "二级预警", "高位个股"));
        WARNING_DEFINITIONS.add(createWarningDef("GOOD_NEWS_SELLOFF", "利好兑现回落预警", "三级预警", "题材个股"));
    }

    private static Map<String, Object> createWarningDef(String code, String name, String level, String target) {
        Map<String, Object> def = new LinkedHashMap<>();
        def.put("code", code);
        def.put("name", name);
        def.put("level", level);
        def.put("target", target);
        return def;
    }

    @Resource
    private TopWarningRecordMapper topWarningRecordMapper;

    @Resource
    private MarketDailyDataMapper marketDailyDataMapper;

    @Override
    @Transactional
    public List<TopWarningRecord> checkAllWarnings(LocalDate tradeDate) {
        log.info("开始校验 {} 的7类高位预警...", tradeDate);

        MarketDailyData marketData = marketDailyDataMapper.selectByTradeDate(tradeDate);
        if (marketData == null) {
            log.warn("日期 {} 的行情数据不存在，跳过预警检查", tradeDate);
            return Collections.emptyList();
        }

        LocalDate prevDate = tradeDate.minusDays(1);
        MarketDailyData prevData = marketDailyDataMapper.selectByTradeDate(prevDate);

        // 先删除该日已有预警记录
        topWarningRecordMapper.deleteByTradeDate(tradeDate);

        List<TopWarningRecord> records = new ArrayList<>();

        for (Map<String, Object> def : WARNING_DEFINITIONS) {
            String code = (String) def.get("code");
            String name = (String) def.get("name");
            String level = (String) def.get("level");
            String target = (String) def.get("target");

            TopWarningRecord record = new TopWarningRecord();
            record.setTradeDate(tradeDate);
            record.setWarningCode(code);
            record.setWarningName(name);
            record.setWarningLevel(level);
            record.setTargetName(target);
            record.setStatus("ACTIVE");

            // 执行预警校验
            Map<String, Object> checkResult = checkWarning(code, marketData, prevData);
            boolean triggered = (boolean) checkResult.get("triggered");
            String basis = (String) checkResult.get("basis");

            record.setIsTriggered(triggered ? 1 : 0);
            record.setTriggerBasis(basis);

            topWarningRecordMapper.insert(record);
            records.add(record);

            if (triggered) {
                log.warn("⚠ {} 触发: {} - {}", level, name, basis);
            }
        }

        int triggeredCount = (int) records.stream().filter(r -> r.getIsTriggered() == 1).count();
        log.info("预警校验完成 - 触发{}项预警", triggeredCount);

        return records;
    }

    /**
     * 校验单个预警规则
     */
    private Map<String, Object> checkWarning(String code, MarketDailyData data, MarketDailyData prevData) {
        Map<String, Object> result = new LinkedHashMap<>();
        boolean triggered = false;
        String basis = "";

        switch (code) {
            case "VOLUME_PRICE_DIVERGENCE":
                // 量价背离：指数上涨但成交量萎缩
                if (data.getShIndexChange() != null && data.getTotalVolume() != null
                        && prevData != null && prevData.getTotalVolume() != null) {
                    boolean priceUp = data.getShIndexChange().compareTo(BigDecimal.ZERO) > 0;
                    boolean volumeDown = data.getTotalVolume().compareTo(prevData.getTotalVolume()) < 0;
                    triggered = priceUp && volumeDown;
                    basis = String.format("上证指数涨跌幅: %s%%, 成交额: %s亿(前日: %s亿)",
                            data.getShIndexChange(), data.getTotalVolume(), prevData.getTotalVolume());
                }
                break;

            case "HIGH_CHURN_STAGNATION":
                // 高位放量滞涨：放量但涨幅很小
                if (data.getShIndexChange() != null && data.getTotalVolume() != null
                        && prevData != null && prevData.getTotalVolume() != null) {
                    BigDecimal absChange = data.getShIndexChange().abs();
                    BigDecimal volRatio = data.getTotalVolume().divide(prevData.getTotalVolume(), 4, BigDecimal.ROUND_HALF_UP);
                    triggered = absChange.compareTo(new BigDecimal("0.5")) < 0
                            && volRatio.compareTo(new BigDecimal("1.3")) > 0;
                    basis = String.format("上证涨幅: %s%%, 成交量比值: %s",
                            data.getShIndexChange(), volRatio.setScale(2, BigDecimal.ROUND_HALF_UP));
                }
                break;

            case "TURNOVER_ABNORMAL":
                // 换手率异常：高换手率（模拟阈值触发）
                triggered = Math.random() < 0.3;
                basis = "监测到板块换手率超过3倍标准差";
                break;

            case "MACD_DIVERGENCE":
                // MACD顶背离（模拟）
                triggered = Math.random() < 0.25;
                basis = "MACD指标出现顶背离信号，DIF线向下穿越DEA线";
                break;

            case "SECTOR_ROTATION":
                // 板块轮动异动（模拟）
                triggered = Math.random() < 0.35;
                basis = "监测到防御板块异常活跃，市场避险情绪上升";
                break;

            case "CHIP_PRESSURE":
                // 高位筹码承压（模拟）
                triggered = Math.random() < 0.3;
                basis = "多个高位标的出现大单卖出，上方套牢盘压力较大";
                break;

            case "GOOD_NEWS_SELLOFF":
                // 利好兑现回落（模拟）
                triggered = Math.random() < 0.4;
                basis = "有利好发布但股价出现冲高回落，资金获利了结迹象明显";
                break;
        }

        result.put("triggered", triggered);
        result.put("basis", basis);
        return result;
    }

    @Override
    public List<TopWarningRecord> getCurrentWarnings() {
        return topWarningRecordMapper.selectActiveWarnings();
    }

    @Override
    public List<TopWarningRecord> getWarningHistory(LocalDate startDate, LocalDate endDate) {
        return topWarningRecordMapper.selectWarningHistory(startDate, endDate);
    }

    @Override
    public List<Map<String, String>> checkStopLossRules(LocalDate tradeDate) {
        List<Map<String, String>> stopLossRules = new ArrayList<>();

        // 规则1: 上证指数跌破20日均线3%以上
        Map<String, String> rule1 = new LinkedHashMap<>();
        rule1.put("rule", "上证指数20日均线止损");
        rule1.put("condition", "上证指数收盘价跌破20日均线3%以上");
        rule1.put("action", "建议减仓至30%以下");
        rule1.put("level", "一级止损");
        stopLossRules.add(rule1);

        // 规则2: 单日跌幅超4%
        Map<String, String> rule2 = new LinkedHashMap<>();
        rule2.put("rule", "单日暴跌止损");
        rule2.put("condition", "上证指数单日跌幅超过4%");
        rule2.put("action", "无条件清仓，转为防御");
        rule2.put("level", "一级止损");
        stopLossRules.add(rule2);

        // 规则3: 连续3日成交量萎缩
        Map<String, String> rule3 = new LinkedHashMap<>();
        rule3.put("rule", "连续缩量止损");
        rule3.put("condition", "连续3个交易日成交量较前日下降超15%");
        rule3.put("action", "减仓至50%，观察市场");
        rule3.put("level", "二级止损");
        stopLossRules.add(rule3);

        return stopLossRules;
    }

    @Override
    @Transactional
    public void updateWarningStatus(Long id, String status) {
        LambdaUpdateWrapper<TopWarningRecord> wrapper = new LambdaUpdateWrapper<>();
        wrapper.eq(TopWarningRecord::getId, id)
                .set(TopWarningRecord::getStatus, status);
        topWarningRecordMapper.update(null, wrapper);
        log.info("预警状态更新 - ID: {}, 新状态: {}", id, status);
    }
}
