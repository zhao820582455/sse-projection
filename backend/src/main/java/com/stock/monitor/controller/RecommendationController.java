package com.stock.monitor.controller;

import com.stock.monitor.dto.ApiResponse;
import com.stock.monitor.service.RecommendationService;
import com.stock.monitor.vo.FundVO;
import com.stock.monitor.vo.SectorVO;
import com.stock.monitor.vo.StockVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 智能推荐控制器
 */
@RestController
@RequestMapping("/api/recommend")
public class RecommendationController {

    @Autowired
    private RecommendationService recommendationService;

    /**
     * 获取全部推荐数据（板块、个股、基金）
     */
    @GetMapping("/all")
    public ApiResponse<Map<String, Object>> getAllRecommendations(
            @RequestParam(defaultValue = "12") int topN) {
        Map<String, Object> result = new HashMap<>();

        // 行业板块
        List<SectorVO> industrySectors = recommendationService.getIndustrySectors(topN);
        result.put("industrySectors", industrySectors);

        // 概念板块
        List<SectorVO> conceptSectors = recommendationService.getConceptSectors(topN);
        result.put("conceptSectors", conceptSectors);

        // 个股推荐
        List<StockVO> stocks = recommendationService.getStockRecommendations(topN);
        result.put("stocks", stocks);

        // ETF基金推荐
        List<FundVO> funds = recommendationService.getFundRecommendations(topN);
        result.put("funds", funds);

        return ApiResponse.success(result);
    }
}
