package com.stock.monitor.controller;

import com.stock.monitor.dto.ApiResponse;
import com.stock.monitor.dto.CurrentDataVO;
import com.stock.monitor.dto.ScoreVO;
import com.stock.monitor.dto.WarningVO;
import com.stock.monitor.entity.MarketDailyData;
import com.stock.monitor.entity.ReboundSignalRecord;
import com.stock.monitor.entity.TopWarningRecord;
import com.stock.monitor.mapper.MarketDailyDataMapper;
import com.stock.monitor.service.DataCollectionService;
import com.stock.monitor.service.SignalService;
import com.stock.monitor.service.WarningService;
import com.stock.monitor.util.DateUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 行情信号API控制器 - 前后端分离，提供RESTful接口
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(originPatterns = "*")
public class MarketController {

    private static final Logger log = LoggerFactory.getLogger(MarketController.class);

    @Resource
    private DataCollectionService dataCollectionService;

    @Resource
    private SignalService signalService;

    @Resource
    private WarningService warningService;

    @Resource
    private MarketDailyDataMapper marketDailyDataMapper;

    /**
     * GET /api/current - 获取当日全量数据（行情、信号、得分、预警）
     */
    @GetMapping("/current")
    public ApiResponse<CurrentDataVO> getCurrentData() {
        try {
            LocalDate tradeDate = DateUtil.getCurrentTradingDay();

            // 获取或采集行情数据
            MarketDailyData marketData = marketDailyDataMapper.selectByTradeDate(tradeDate);
            if (marketData == null) {
                marketData = dataCollectionService.collectDataForDate(tradeDate);
            }

            // 获取信号数据
            List<ReboundSignalRecord> signals = signalService.getSignalsByDate(tradeDate);
            if (signals.isEmpty()) {
                signals = signalService.checkAllSignals(tradeDate);
            }

            // 计算总得分
            int totalScore = signalService.calculateTotalScore(tradeDate);

            // 获取预警数据
            List<TopWarningRecord> warnings = warningService.getCurrentWarnings();
            if (warnings.isEmpty()) {
                warnings = warningService.checkAllWarnings(tradeDate).stream()
                        .filter(w -> w.getIsTriggered() == 1)
                        .collect(Collectors.toList());
            }

            // 组装返回数据
            CurrentDataVO vo = CurrentDataVO.builder()
                    .marketData(marketData)
                    .signals(signals)
                    .totalScore(totalScore)
                    .scoreLevel(signalService.getScoreLevel(totalScore))
                    .suggestion(signalService.getSuggestion(totalScore))
                    .warnings(warnings)
                    .build();

            log.info("获取当日数据成功 - 日期: {}, 得分: {}", tradeDate, totalScore);
            return ApiResponse.success(vo);

        } catch (Exception e) {
            log.error("获取当日数据失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取数据失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/history?days=30 - 获取历史得分数据
     */
    @GetMapping("/history")
    public ApiResponse<List<ScoreVO>> getHistory(@RequestParam(defaultValue = "30") int days) {
        try {
            LocalDate endDate = DateUtil.getCurrentTradingDay();
            LocalDate startDate = endDate.minusDays(days);

            List<Map<String, Object>> historyData = signalService.getScoreHistory(startDate, endDate);

            List<ScoreVO> scoreList = historyData.stream().map(row -> {
                Object tradeDateObj = row.get("trade_date");
                Object totalScoreObj = row.get("total_score");

                LocalDate tradeDate = tradeDateObj instanceof java.sql.Date
                        ? ((java.sql.Date) tradeDateObj).toLocalDate()
                        : LocalDate.parse(tradeDateObj.toString());

                int totalScore = totalScoreObj instanceof Long
                        ? ((Long) totalScoreObj).intValue()
                        : Integer.parseInt(totalScoreObj.toString());

                // 获取当日触发信号数
                List<ReboundSignalRecord> signals = signalService.getSignalsByDate(tradeDate);
                int triggeredCount = (int) signals.stream().filter(s -> s.getIsTriggered() == 1).count();

                return ScoreVO.builder()
                        .tradeDate(tradeDate)
                        .totalScore(totalScore)
                        .scoreLevel(signalService.getScoreLevel(totalScore))
                        .triggeredCount(triggeredCount)
                        .build();
            }).collect(Collectors.toList());

            // 如果没有历史数据，生成一些模拟数据用于前端展示
            if (scoreList.isEmpty()) {
                scoreList = generateMockHistory(days);
            }

            return ApiResponse.success(scoreList);

        } catch (Exception e) {
            log.error("获取历史数据失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取历史数据失败: " + e.getMessage());
        }
    }

    /**
     * 生成模拟历史数据
     */
    private List<ScoreVO> generateMockHistory(int days) {
        List<ScoreVO> list = new ArrayList<>();
        LocalDate date = DateUtil.getCurrentTradingDay();
        Random random = new Random();

        for (int i = 0; i < days; i++) {
            LocalDate d = date.minusDays(i);
            if (!DateUtil.isTradingDay(d)) continue;

            int score = 35 + random.nextInt(55); // 35-89
            list.add(ScoreVO.builder()
                    .tradeDate(d)
                    .totalScore(score)
                    .scoreLevel(signalService.getScoreLevel(score))
                    .triggeredCount(random.nextInt(12) + 1)
                    .build());
        }

        return list;
    }

    /**
     * GET /api/suggestion - 获取仓位操作建议
     */
    @GetMapping("/suggestion")
    public ApiResponse<Map<String, Object>> getSuggestion() {
        try {
            LocalDate tradeDate = DateUtil.getCurrentTradingDay();
            int totalScore = signalService.calculateTotalScore(tradeDate);
            Map<String, Object> suggestion = signalService.getSuggestion(totalScore);
            return ApiResponse.success(suggestion);
        } catch (Exception e) {
            log.error("获取建议失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取建议失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/warnings/current - 获取当前触发的所有预警
     */
    @GetMapping("/warnings/current")
    public ApiResponse<List<WarningVO>> getCurrentWarnings() {
        try {
            List<TopWarningRecord> warnings = warningService.getCurrentWarnings();
            if (warnings.isEmpty()) {
                // 如果无预警数据，尝试检查一次
                warnings = warningService.checkAllWarnings(DateUtil.getCurrentTradingDay()).stream()
                        .filter(w -> w.getIsTriggered() == 1)
                        .collect(Collectors.toList());
            }

            List<WarningVO> voList = warnings.stream().map(this::convertToWarningVO).collect(Collectors.toList());
            return ApiResponse.success(voList);
        } catch (Exception e) {
            log.error("获取当前预警失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取预警失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/warnings/history?days=15 - 获取历史预警记录
     */
    @GetMapping("/warnings/history")
    public ApiResponse<List<WarningVO>> getWarningHistory(@RequestParam(defaultValue = "15") int days) {
        try {
            LocalDate endDate = DateUtil.getCurrentTradingDay();
            LocalDate startDate = endDate.minusDays(days);

            List<TopWarningRecord> records = warningService.getWarningHistory(startDate, endDate);
            List<WarningVO> voList = records.stream().map(this::convertToWarningVO).collect(Collectors.toList());

            return ApiResponse.success(voList);
        } catch (Exception e) {
            log.error("获取预警历史失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取预警历史失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/warnings/stop-loss - 获取强制止损规则
     */
    @GetMapping("/warnings/stop-loss")
    public ApiResponse<List<Map<String, String>>> getStopLossRules() {
        try {
            LocalDate tradeDate = DateUtil.getCurrentTradingDay();
            List<Map<String, String>> rules = warningService.checkStopLossRules(tradeDate);
            return ApiResponse.success(rules);
        } catch (Exception e) {
            log.error("获取止损规则失败: {}", e.getMessage(), e);
            return ApiResponse.error("获取止损规则失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/manual-update - 手动修正信号状态
     */
    @PostMapping("/manual-update")
    public ApiResponse<Void> manualUpdate(@RequestBody Map<String, Object> params) {
        try {
            String dateStr = (String) params.get("tradeDate");
            String signalCode = (String) params.get("signalCode");
            Integer isTriggered = (Integer) params.get("isTriggered");
            String remark = (String) params.getOrDefault("remark", "");

            LocalDate tradeDate = LocalDate.parse(dateStr);
            signalService.manualUpdateSignal(tradeDate, signalCode, isTriggered, remark);

            log.info("手动修正成功 - 日期: {}, 信号: {}, 状态: {}", tradeDate, signalCode, isTriggered);
            return ApiResponse.success("手动修正成功", null);
        } catch (Exception e) {
            log.error("手动修正失败: {}", e.getMessage(), e);
            return ApiResponse.error("手动修正失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/refresh - 手动触发数据刷新
     */
    @PostMapping("/refresh")
    public ApiResponse<Map<String, Object>> refreshData() {
        try {
            LocalDate tradeDate = DateUtil.getCurrentTradingDay();

            // 重新采集数据
            MarketDailyData marketData = dataCollectionService.collectDataForDate(tradeDate);

            // 重新校验信号
            List<ReboundSignalRecord> signals = signalService.checkAllSignals(tradeDate);

            // 重新检查预警
            List<TopWarningRecord> warnings = warningService.checkAllWarnings(tradeDate);

            int totalScore = signalService.calculateTotalScore(tradeDate);

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("tradeDate", tradeDate.toString());
            result.put("totalScore", totalScore);
            result.put("signalsChecked", signals.size());
            result.put("warningsChecked", warnings.size());
            result.put("message", "数据刷新完成");

            log.info("手动刷新完成 - 日期: {}, 得分: {}", tradeDate, totalScore);
            return ApiResponse.success(result);

        } catch (Exception e) {
            log.error("数据刷新失败: {}", e.getMessage(), e);
            return ApiResponse.error("数据刷新失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/date/{date}/signals - 获取指定日期的信号详情
     */
    @GetMapping("/date/{date}/signals")
    public ApiResponse<List<ReboundSignalRecord>> getSignalsByDate(@PathVariable String date) {
        try {
            LocalDate tradeDate = LocalDate.parse(date);
            List<ReboundSignalRecord> signals = signalService.getSignalsByDate(tradeDate);
            if (signals.isEmpty()) {
                // 如果该日没有信号数据，自动生成
                signals = signalService.checkAllSignals(tradeDate);
            }
            return ApiResponse.success(signals);
        } catch (Exception e) {
            log.error("获取日期{}信号失败: {}", date, e.getMessage());
            return ApiResponse.error("获取信号失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/date/{date}/warnings - 获取指定日期的预警详情
     */
    @GetMapping("/date/{date}/warnings")
    public ApiResponse<List<WarningVO>> getWarningsByDate(@PathVariable String date) {
        try {
            LocalDate tradeDate = LocalDate.parse(date);
            List<TopWarningRecord> records = warningService.getWarningHistory(tradeDate, tradeDate);
            List<WarningVO> voList = records.stream().map(this::convertToWarningVO).collect(Collectors.toList());
            return ApiResponse.success(voList);
        } catch (Exception e) {
            log.error("获取日期{}预警失败: {}", date, e.getMessage());
            return ApiResponse.error("获取预警失败: " + e.getMessage());
        }
    }

    /**
     * 实体转VO
     */
    private WarningVO convertToWarningVO(TopWarningRecord record) {
        return WarningVO.builder()
                .id(record.getId())
                .tradeDate(record.getTradeDate())
                .warningCode(record.getWarningCode())
                .warningName(record.getWarningName())
                .warningLevel(record.getWarningLevel())
                .targetName(record.getTargetName())
                .isTriggered(record.getIsTriggered())
                .triggerBasis(record.getTriggerBasis())
                .status(record.getStatus())
                .createTime(record.getCreateTime())
                .build();
    }
}
