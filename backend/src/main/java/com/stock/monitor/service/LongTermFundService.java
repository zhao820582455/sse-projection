package com.stock.monitor.service;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.stock.monitor.util.HttpClientUtil;
import com.stock.monitor.vo.LongTermFundVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * 长期投资基金分析服务
 * 从板块长期趋势角度，筛选顺势/逆势投资机会并匹配对应ETF
 *
 * 分析维度:
 *   1. 周期涨跌幅 (1M / 3M / 6M) — 判断板块强弱
 *   2. 均线排列 (MA20 vs MA60 vs 最新价) — 判断趋势方向
 *   3. 趋势强度评分 — 综合量化
 *   4. ETF匹配 — 为值得关注的板块找到对应基金
 */
@Service
public class LongTermFundService {

    private static final Logger log = LoggerFactory.getLogger(LongTermFundService.class);

    private static final String EM_CLIST_URL = "https://push2.eastmoney.com/api/qt/clist/get";
    private static final String EM_KLINE_URL = "https://push2his.eastmoney.com/api/qt/stock/kline/get";

    // 分析用K线天数: 覆盖6个月+MA60
    private static final int KLINES_DAYS = 150;

    @Autowired
    private HttpClientUtil httpClientUtil;

    /**
     * 完整分析流程
     */
    public Map<String, Object> analyze(int topN) {
        Map<String, Object> result = new LinkedHashMap<>();

        // 1. 获取板块列表(扩大样本量，从两个维度各取足够多的板块做趋势分析，不限于topN)
        int sampleSize = Math.max(topN * 5, 50); // 至少50个板块，确保覆盖到多头/空头
        List<LongTermFundVO> industrySectors = fetchSectorBasics("m:90+t:2", "行业", sampleSize);
        List<LongTermFundVO> conceptSectors = fetchSectorBasics("m:90+t:3", "概念", sampleSize);

        List<LongTermFundVO> allSectors = new ArrayList<>();
        allSectors.addAll(industrySectors);
        allSectors.addAll(conceptSectors);

        // 2. 批量拉K线并计算指标 (线程池并行，提高并发以应对更多板块)
        ExecutorService executor = Executors.newFixedThreadPool(10);
        List<Future<?>> futures = new ArrayList<>();
        for (LongTermFundVO vo : allSectors) {
            futures.add(executor.submit(() -> analyzeSectorTrend(vo)));
        }
        for (Future<?> f : futures) {
            try { f.get(15, TimeUnit.SECONDS); }
            catch (Exception e) { log.debug("板块分析超时: {}", e.getMessage()); }
        }
        executor.shutdown();

        // 3. 获取ETF列表用于匹配
        List<Map<String, Object>> etfList = fetchAllETFs();

        // 4. 为每个板块匹配ETF
        for (LongTermFundVO vo : allSectors) {
            matchETF(vo, etfList);
        }

        // 5. 按策略分组（不急于过滤无ETF，先看有多少板块被分类）
        List<LongTermFundVO> allMomentum = allSectors.stream()
                .filter(v -> "MOMENTUM".equals(v.getStrategy()))
                .sorted((a, b) -> Integer.compare(
                        b.getTrendScore() != null ? b.getTrendScore() : 0,
                        a.getTrendScore() != null ? a.getTrendScore() : 0))
                .collect(Collectors.toList());

        List<LongTermFundVO> allContrarian = allSectors.stream()
                .filter(v -> "CONTRARIAN".equals(v.getStrategy()))
                .sorted((a, b) -> Integer.compare(
                        b.getTrendScore() != null ? b.getTrendScore() : 0,
                        a.getTrendScore() != null ? a.getTrendScore() : 0))
                .collect(Collectors.toList());

        // 6. 过滤无ETF匹配 + 按fundCode去重（同一ETF只保留trendScore最高的板块） + 保留topN
        Set<String> momentumSeen = new HashSet<>();
        List<LongTermFundVO> momentum = allMomentum.stream()
                .filter(v -> v.getFundCode() != null)
                .filter(v -> momentumSeen.add(v.getFundCode()))
                .limit(topN)
                .collect(Collectors.toList());
        Set<String> contrarianSeen = new HashSet<>();
        List<LongTermFundVO> contrarian = allContrarian.stream()
                .filter(v -> v.getFundCode() != null)
                .filter(v -> contrarianSeen.add(v.getFundCode()))
                .limit(topN)
                .collect(Collectors.toList());

        result.put("momentum", momentum);
        result.put("contrarian", contrarian);
        result.put("analysisTime", System.currentTimeMillis());
        // 附加调试信息
        Map<String, Object> debug = new LinkedHashMap<>();
        debug.put("sectorTotal", allSectors.size());
        debug.put("etfTotal", etfList.size());
        debug.put("momentumRaw", allMomentum.size());
        debug.put("contrarianRaw", allContrarian.size());
        debug.put("momentumMatched", momentum.size());
        debug.put("contrarianMatched", contrarian.size());
        result.put("debug", debug);

        log.warn("长期基金分析: 板块{}个, ETF{}只, 顺势{}→{} 逆势{}→{}",
                allSectors.size(), etfList.size(),
                allMomentum.size(), momentum.size(),
                allContrarian.size(), contrarian.size());

        return result;
    }

    /**
     * 获取板块基本信息(代码+名称+最新价)
     */
    private List<LongTermFundVO> fetchSectorBasics(String fs, String type, int topN) {
        List<LongTermFundVO> list = new ArrayList<>();
        try {
            String url = EM_CLIST_URL
                    + "?pn=1&pz=" + topN + "&po=1&np=1"
                    + "&fields=f2,f3,f12,f14,f20,f62"
                    + "&fid=f20&fs=" + fs + "&fltt=2";
            String resp = httpClientUtil.doGetWithRetry(url, 2, 1000);
            JSONObject json = JSONObject.parseObject(resp);

            if (json != null) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    JSONArray diff = data.getJSONArray("diff");
                    if (diff != null) {
                        for (int i = 0; i < diff.size(); i++) {
                            JSONObject item = diff.getJSONObject(i);
                            LongTermFundVO vo = new LongTermFundVO();
                            vo.setSectorName(item.getString("f14"));
                            vo.setSectorCode(item.getString("f12"));
                            vo.setSectorType(type);
                            vo.setLatestPrice(safeDecimal(item, "f2", 2));
                            list.add(vo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取{}板块基础信息失败: {}", type, e.getMessage());
        }
        return list;
    }

    /**
     * 拉取板块日K线, 计算趋势指标
     */
    private void analyzeSectorTrend(LongTermFundVO vo) {
        try {
            List<BigDecimal> closes = fetchCloses(vo.getSectorCode());
            if (closes.size() < 60) {
                vo.setTrendLabel("数据不足");
                vo.setTrendScore(30);
                vo.setStrategy("NEUTRAL");
                return;
            }

            int last = closes.size() - 1;
            BigDecimal latestClose = closes.get(last);

            // 周期涨跌幅
            vo.setPct1M(periodReturn(closes, 22));   // 约1个月
            vo.setPct3M(periodReturn(closes, 66));   // 约3个月
            vo.setPct6M(periodReturn(closes, 132));  // 约6个月

            // 均线
            vo.setMa20(calcMA(closes, 20));
            vo.setMa60(calcMA(closes, 60));

            // 趋势分类
            classifyTrend(vo, latestClose);

        } catch (Exception e) {
            log.warn("分析板块{}趋势失败: {}", vo.getSectorCode(), e.getMessage());
            vo.setTrendLabel("分析异常");
            vo.setTrendScore(20);
            vo.setStrategy("NEUTRAL");
        }
    }

    /**
     * 拉取板块日K线收盘价序列
     */
    private List<BigDecimal> fetchCloses(String sectorCode) {
        List<BigDecimal> closes = new ArrayList<>();
        try {
            // 板块K线不支持fqt=1(前复权)，需用fqt=0并指定beg/end
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyyMMdd");
            String endDate = LocalDate.now().format(dtf);
            String begDate = LocalDate.now().minusDays(300).format(dtf);
            String url = EM_KLINE_URL
                    + "?secid=90." + sectorCode
                    + "&fields1=f1,f2,f3,f4,f5,f6"
                    + "&fields2=f51,f52,f53,f54,f55,f56,f57,f58,f59,f60,f61"
                    + "&klt=101&fqt=0"
                    + "&beg=" + begDate + "&end=" + endDate
                    + "&lmt=" + KLINES_DAYS;
            String resp = httpClientUtil.doGetWithRetry(url, 2, 1000);
            JSONObject json = JSONObject.parseObject(resp);

            if (json != null) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    JSONArray klines = data.getJSONArray("klines");
                    if (klines != null) {
                        for (int i = 0; i < klines.size(); i++) {
                            String kline = klines.getString(i);
                            String[] parts = kline.split(",");
                            // parts[0]=日期, parts[2]=收盘价
                            if (parts.length >= 3) {
                                closes.add(new BigDecimal(parts[2]));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("拉取板块{}K线失败: {}", sectorCode, e.getMessage());
        }
        return closes;
    }

    /**
     * 计算周期涨跌幅%
     * @param closes 收盘价序列
     * @param days 回看天数
     */
    private BigDecimal periodReturn(List<BigDecimal> closes, int days) {
        if (closes.size() <= days) return null;
        int idx = closes.size() - 1 - days;
        BigDecimal start = closes.get(idx);
        BigDecimal end = closes.get(closes.size() - 1);
        if (start == null || start.compareTo(BigDecimal.ZERO) == 0) return null;
        return end.subtract(start)
                .divide(start, 6, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * 计算移动平均
     */
    private BigDecimal calcMA(List<BigDecimal> closes, int period) {
        if (closes.size() < period) return null;
        BigDecimal sum = BigDecimal.ZERO;
        int start = closes.size() - period;
        for (int i = start; i < closes.size(); i++) {
            sum = sum.add(closes.get(i));
        }
        return sum.divide(BigDecimal.valueOf(period), 2, RoundingMode.HALF_UP);
    }

    /**
     * 分类趋势: 多头排列 / 空头排列 / 震荡 / 底部企稳
     *
     * 多头排列: 最新价 > MA20 > MA60 → 顺势持有(MOMENTUM)
     * 空头排列: 最新价 < MA20 < MA60 → 逆势定投(CONTRARIAN)
     * 底部企稳: 最新价突破MA20但仍在MA60下方 → 定投关注
     * 震荡: 其他情况 → 观望
     */
    private void classifyTrend(LongTermFundVO vo, BigDecimal close) {
        BigDecimal ma20 = vo.getMa20();
        BigDecimal ma60 = vo.getMa60();
        BigDecimal pct6M = vo.getPct6M();

        if (ma20 == null || ma60 == null) {
            vo.setTrendLabel("数据不足");
            vo.setTrendScore(30);
            vo.setStrategy("NEUTRAL");
            return;
        }

        boolean aboveMA20 = close.compareTo(ma20) > 0;
        boolean aboveMA60 = close.compareTo(ma60) > 0;
        boolean ma20AboveMA60 = ma20.compareTo(ma60) > 0;

        if (aboveMA20 && aboveMA60 && ma20AboveMA60) {
            // 多头排列
            if (pct6M != null && pct6M.compareTo(BigDecimal.valueOf(10)) > 0) {
                vo.setTrendLabel("多头排列-强势上涨");
                vo.setTrendScore(85);
                vo.setStrategy("MOMENTUM");
            } else {
                vo.setTrendLabel("多头排列-温和上涨");
                vo.setTrendScore(65);
                vo.setStrategy("MOMENTUM");
            }
        } else if (!aboveMA20 && !aboveMA60 && !ma20AboveMA60) {
            // 空头排列
            if (pct6M != null && pct6M.compareTo(BigDecimal.valueOf(-20)) < 0) {
                vo.setTrendLabel("空头排列-加速下跌");
                vo.setTrendScore(75);
                vo.setStrategy("CONTRARIAN");
            } else {
                vo.setTrendLabel("空头排列-持续下跌");
                vo.setTrendScore(60);
                vo.setStrategy("CONTRARIAN");
            }
        } else if (aboveMA20 && !aboveMA60) {
            // 底部企稳信号: 站上MA20但仍低于MA60
            BigDecimal pct1M = vo.getPct1M();
            if (pct1M != null && pct1M.compareTo(BigDecimal.valueOf(3)) > 0) {
                vo.setTrendLabel("底部企稳-反弹信号");
                vo.setTrendScore(70);
                vo.setStrategy("CONTRARIAN");
            } else {
                vo.setTrendLabel("底部企稳-筑底中");
                vo.setTrendScore(55);
                vo.setStrategy("CONTRARIAN");
            }
        } else {
            vo.setTrendLabel("震荡整理");
            vo.setTrendScore(40);
            vo.setStrategy("NEUTRAL");
        }
    }

    /**
     * 获取所有ETF列表(宽基+行业+主题)
     */
    private List<Map<String, Object>> fetchAllETFs() {
        List<Map<String, Object>> all = new ArrayList<>();
        String[] boards = {"b:MK0021", "b:MK0022", "b:MK0023"};
        for (String board : boards) {
            try {
                String url = EM_CLIST_URL
                        + "?pn=1&pz=100&po=1&np=1"
                        + "&fields=f2,f3,f12,f14,f20"
                        + "&fid=f3&fs=" + board + "&fltt=2";
                String resp = httpClientUtil.doGetWithRetry(url, 2, 1000);
                JSONObject json = JSONObject.parseObject(resp);

                if (json != null) {
                    JSONObject data = json.getJSONObject("data");
                    if (data != null) {
                        JSONArray diff = data.getJSONArray("diff");
                        if (diff != null) {
                            for (int i = 0; i < diff.size(); i++) {
                                JSONObject item = diff.getJSONObject(i);
                                Map<String, Object> etf = new HashMap<>();
                                etf.put("code", item.getString("f12"));
                                etf.put("name", item.getString("f14"));
                                etf.put("price", item.getBigDecimal("f2"));
                                etf.put("changePct", item.getBigDecimal("f3"));
                                etf.put("scale", item.getBigDecimal("f20"));
                                all.add(etf);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("获取ETF列表失败 board={}: {}", board, e.getMessage());
            }
        }
        log.warn("ETF列表获取完成: 共{}只", all.size());
        return all;
    }

    /**
     * 根据板块名称关键词匹配ETF
     * 智能关键词提取: 去掉行业后缀后尝试逐级缩短匹配
     * 例如 "煤炭开采" → 先试 "煤炭开采", 再试 "煤炭", 直到匹配到ETF
     */
    private void matchETF(LongTermFundVO vo, List<Map<String, Object>> etfList) {
        String sectorName = vo.getSectorName();
        if (sectorName == null) return;

        // 去掉常见行业后缀和罗马数字，提取核心关键词
        String[] stripSuffixes = {
            "行业$", "开采$", "制品$", "生产$", "服务$",
            "工程$", "材料$", "装备$", "设备$", "物流$",
            "商业$", "制造$", "加工$", "经营$", "销售$",
            "[ⅢⅡⅣⅠ]", "指数$"
        };
        String keyword = sectorName;
        for (String suffix : stripSuffixes) {
            keyword = keyword.replaceAll(suffix, "");
        }
        keyword = keyword.trim();
        if (keyword.length() < 2) return;

        Map<String, Object> bestMatch = null;
        int bestLen = 0;

        // 逐级缩短关键词尝试匹配（从长到短，优先精确匹配）
        String tryKw = keyword;
        while (tryKw.length() >= 2) {
            for (Map<String, Object> etf : etfList) {
                String etfName = (String) etf.get("name");
                if (etfName == null) continue;

                // 排除货币/快钱/现金类ETF
                if (etfName.contains("货币") || etfName.contains("快钱") || etfName.contains("现金")) {
                    continue;
                }

                if (etfName.contains(tryKw)) {
                    if (tryKw.length() > bestLen) {
                        bestLen = tryKw.length();
                        bestMatch = etf;
                    }
                }
            }
            if (bestMatch != null) break; // 已找到最长匹配
            // 缩短关键词继续尝试
            tryKw = tryKw.substring(0, tryKw.length() - 1);
        }

        if (bestMatch != null) {
            vo.setFundName((String) bestMatch.get("name"));
            vo.setFundCode((String) bestMatch.get("code"));
            BigDecimal price = (BigDecimal) bestMatch.get("price");
            vo.setFundPrice(price != null ? price.setScale(3, RoundingMode.HALF_UP) : null);
            BigDecimal chg = (BigDecimal) bestMatch.get("changePct");
            vo.setFundChangePct(chg != null ? chg.setScale(2, RoundingMode.HALF_UP) : null);
            BigDecimal scale = (BigDecimal) bestMatch.get("scale");
            if (scale != null) {
                vo.setFundScale(scale.divide(
                        BigDecimal.valueOf(100_000_000), 2, RoundingMode.HALF_UP));
            }
        }
    }

    private BigDecimal safeDecimal(JSONObject obj, String key, int scale) {
        BigDecimal val = obj.getBigDecimal(key);
        if (val != null) {
            return val.setScale(scale, RoundingMode.HALF_UP);
        }
        return null;
    }
}
