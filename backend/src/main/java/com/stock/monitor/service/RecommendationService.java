package com.stock.monitor.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.stock.monitor.util.HttpClientUtil;
import com.stock.monitor.vo.FundVO;
import com.stock.monitor.vo.SectorVO;
import com.stock.monitor.vo.StockVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * 智能推荐服务 - 基于东方财富API的板块/个股/基金推荐
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);

    private static final String EM_CLIST_URL = "https://push2.eastmoney.com/api/qt/clist/get";

    @Autowired
    private HttpClientUtil httpClientUtil;

    /**
     * 获取行业板块推荐（按主力净流入排名）
     */
    public List<SectorVO> getIndustrySectors(int topN) {
        return fetchSectors("m:90+t:2", "行业", "f62", topN);
    }

    /**
     * 获取概念板块推荐（按涨跌幅排名）
     */
    public List<SectorVO> getConceptSectors(int topN) {
        return fetchSectors("m:90+t:3", "概念", "f3", topN);
    }

    private List<SectorVO> fetchSectors(String fs, String type, String sortField, int topN) {
        List<SectorVO> result = new ArrayList<>();
        try {
            String url = EM_CLIST_URL
                    + "?pn=1&pz=" + topN + "&po=1&np=1"
                    + "&fields=f2,f3,f12,f14,f62"
                    + "&fid=" + sortField + "&fs=" + fs + "&fltt=2";
            String resp = httpClientUtil.doGetWithRetry(url, 2, 1000);
            JSONObject json = JSONObject.parseObject(resp);

            if (json != null) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    JSONArray diff = data.getJSONArray("diff");
                    if (diff != null) {
                        for (int i = 0; i < diff.size(); i++) {
                            JSONObject item = diff.getJSONObject(i);
                            SectorVO vo = new SectorVO();
                            vo.setName(item.getString("f14"));
                            vo.setCode(item.getString("f12"));
                            vo.setType(type);
                            vo.setChangePct(safeDecimal(item, "f3", 2));
                            // 主力净流入(元) → 亿元
                            BigDecimal flow = item.getBigDecimal("f62");
                            if (flow != null) {
                                vo.setMainFlow(flow.divide(
                                        BigDecimal.valueOf(100_000_000), 2, RoundingMode.HALF_UP));
                            }
                            vo.setRanking(i + 1);
                            result.add(vo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取{}板块推荐失败: {}", type, e.getMessage());
        }
        return result;
    }

    /**
     * 获取个股推荐（北向增持 + 量价突破）
     */
    public List<StockVO> getStockRecommendations(int topN) {
        List<StockVO> result = new ArrayList<>();

        // 策略1: 北向资金增持股（沪股通 + 深股通，按涨跌幅筛选）
        fetchNorthFlowStocks(result, topN);

        // 策略2: 量价突破股（高换手率 + 高涨幅）
        fetchVolBreakoutStocks(result, topN);

        return result;
    }

    /**
     * 北向资金增持股
     */
    private void fetchNorthFlowStocks(List<StockVO> result, int topN) {
        try {
            // 沪股通 + 深股通标的，按涨跌幅降序
            String url = EM_CLIST_URL
                    + "?pn=1&pz=" + (topN / 2)
                    + "&po=1&np=1"
                    + "&fields=f2,f3,f8,f10,f12,f14"
                    + "&fid=f3&fs=b:BK0707&fltt=2";
            String resp = httpClientUtil.doGetWithRetry(url, 2, 1000);
            JSONObject json = JSONObject.parseObject(resp);

            if (json != null) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    JSONArray diff = data.getJSONArray("diff");
                    if (diff != null) {
                        for (int i = 0; i < diff.size(); i++) {
                            JSONObject item = diff.getJSONObject(i);
                            StockVO vo = new StockVO();
                            vo.setName(item.getString("f14"));
                            vo.setCode(item.getString("f12"));
                            vo.setPrice(safeDecimal(item, "f2", 2));
                            vo.setChangePct(safeDecimal(item, "f3", 2));
                            vo.setTurnoverRate(safeDecimal(item, "f8", 2));
                            vo.setVolumeRatio(safeDecimal(item, "f10", 2));
                            vo.setStrategy("north_flow");
                            vo.setReason("北向持仓标的，强势上涨");
                            vo.setRanking(result.size() + 1);
                            result.add(vo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取北向增持股推荐失败: {}", e.getMessage());
        }
    }

    /**
     * 量价突破股（全A股，高换手+高涨幅）
     */
    private void fetchVolBreakoutStocks(List<StockVO> result, int topN) {
        try {
            // 全A股，按量比降序
            String url = EM_CLIST_URL
                    + "?pn=1&pz=" + (topN / 2)
                    + "&po=1&np=1"
                    + "&fields=f2,f3,f7,f8,f10,f12,f14"
                    + "&fid=f10&fs=m:0+t6,m:0+t80,m:1+t2&fltt=2";
            String resp = httpClientUtil.doGetWithRetry(url, 2, 1000);
            JSONObject json = JSONObject.parseObject(resp);

            if (json != null) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    JSONArray diff = data.getJSONArray("diff");
                    if (diff != null) {
                        for (int i = 0; i < diff.size(); i++) {
                            JSONObject item = diff.getJSONObject(i);
                            StockVO vo = new StockVO();
                            vo.setName(item.getString("f14"));
                            vo.setCode(item.getString("f12"));
                            vo.setPrice(safeDecimal(item, "f2", 2));
                            vo.setChangePct(safeDecimal(item, "f3", 2));
                            vo.setTurnoverRate(safeDecimal(item, "f8", 2));
                            vo.setVolumeRatio(safeDecimal(item, "f10", 2));
                            vo.setStrategy("vol_breakout");
                            vo.setReason("量比放大，资金涌入");
                            vo.setRanking(result.size() + 1);

                            // 只保留涨跌幅 > 0 的
                            if (vo.getChangePct() != null
                                    && vo.getChangePct().compareTo(BigDecimal.ZERO) > 0) {
                                result.add(vo);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取量价突破股推荐失败: {}", e.getMessage());
        }
    }

    /**
     * 获取ETF基金推荐（按涨跌幅排名，含宽基/行业/主题）
     */
    public List<FundVO> getFundRecommendations(int topN) {
        List<FundVO> result = new ArrayList<>();

        // 宽基ETF
        fetchETFs(result, "b:MK0021", "宽基", topN / 3);
        // 行业ETF
        fetchETFs(result, "b:MK0022", "行业", topN / 3);
        // 主题ETF
        fetchETFs(result, "b:MK0023", "主题", topN / 3);

        return result;
    }

    private void fetchETFs(List<FundVO> result, String fs, String fundType, int count) {
        try {
            String url = EM_CLIST_URL
                    + "?pn=1&pz=" + count
                    + "&po=1&np=1"
                    + "&fields=f2,f3,f12,f14,f20"
                    + "&fid=f3&fs=" + fs + "&fltt=2";
            String resp = httpClientUtil.doGetWithRetry(url, 2, 1000);
            JSONObject json = JSONObject.parseObject(resp);

            if (json != null) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    JSONArray diff = data.getJSONArray("diff");
                    if (diff != null) {
                        for (int i = 0; i < diff.size(); i++) {
                            JSONObject item = diff.getJSONObject(i);
                            FundVO vo = new FundVO();
                            vo.setName(item.getString("f14"));
                            vo.setCode(item.getString("f12"));
                            vo.setPrice(safeDecimal(item, "f2", 3));
                            vo.setChangePct(safeDecimal(item, "f3", 2));
                            // 总市值(元) → 亿元
                            BigDecimal cap = item.getBigDecimal("f20");
                            if (cap != null) {
                                vo.setTotalCap(cap.divide(
                                        BigDecimal.valueOf(100_000_000), 2, RoundingMode.HALF_UP));
                            }
                            vo.setFundType(fundType);
                            vo.setRanking(result.size() + 1);
                            result.add(vo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取{}ETF推荐失败: {}", fundType, e.getMessage());
        }
    }

    /**
     * 安全获取BigDecimal，可指定小数位数，null时返回null
     */
    private BigDecimal safeDecimal(JSONObject obj, String key, int scale) {
        BigDecimal val = obj.getBigDecimal(key);
        if (val != null) {
            return val.setScale(scale, RoundingMode.HALF_UP);
        }
        return null;
    }
}
