package com.stock.monitor.controller;

import com.stock.monitor.dto.ApiResponse;
import com.stock.monitor.service.LongTermFundService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 长期投资基金分析接口
 */
@RestController
@RequestMapping("/api/longterm")
public class LongTermFundController {

    @Autowired
    private LongTermFundService longTermFundService;

    /**
     * 获取长期基金分析结果
     * GET /api/longterm/analysis?topN=10
     *
     * 返回:
     *   momentum:  顺势持有列表 (多头排列板块+匹配ETF)
     *   contrarian: 逆势定投列表 (空头排列/底部企稳板块+匹配ETF)
     */
    @GetMapping("/analysis")
    public ApiResponse<Map<String, Object>> getAnalysis(@RequestParam(defaultValue = "10") int topN) {
        try {
            Map<String, Object> result = longTermFundService.analyze(Math.min(topN, 20));
            return ApiResponse.success(result);
        } catch (Exception e) {
            return ApiResponse.error(500, "长期基金分析失败: " + e.getMessage());
        }
    }
}
