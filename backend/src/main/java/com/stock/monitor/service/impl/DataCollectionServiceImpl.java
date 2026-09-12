package com.stock.monitor.service.impl;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.stock.monitor.entity.MarketDailyData;
import com.stock.monitor.mapper.MarketDailyDataMapper;
import com.stock.monitor.service.DataCollectionService;
import com.stock.monitor.util.DateUtil;
import com.stock.monitor.util.HttpClientUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Random;

/**
 * 数据采集服务实现 - 对接东方财富实时行情API
 *
 * 数据来源:
 * - 上证指数/深证成指(成交额) → 东方财富 push2 实时行情
 * - 北向资金 → 东方财富 港股通资金流向
 * - 美元指数/费城半导体/标普500/纳斯达克/日经225/恒生指数 → 东方财富 全球指数
 * - 两融余额 → 东方财富 数据中心
 * - 涨跌家数/跌停/美债收益率 → 尝试API，失败兜底模拟
 * - ETF净申购 → 暂模拟（需专业数据服务商API）
 */
@Service
public class DataCollectionServiceImpl implements DataCollectionService {

    private static final Logger log = LoggerFactory.getLogger(DataCollectionServiceImpl.class);
    private static final Random RANDOM = new Random();

    // 东方财富 API 端点
    private static final String EM_QUOTE_URL = "https://push2.eastmoney.com/api/qt/stock/get";
    private static final String EM_NORTH_FLOW_URL = "https://push2.eastmoney.com/api/qt/kamt.kline/get";
    private static final String EM_MARGIN_URL = "https://datacenter-web.eastmoney.com/api/data/v1/get";

    @Resource
    private MarketDailyDataMapper marketDailyDataMapper;

    @Resource
    private HttpClientUtil httpClientUtil;

    @Value("${collector.max-retry:3}")
    private int maxRetry;

    @Value("${collector.retry-interval-ms:5000}")
    private long retryIntervalMs;

    @Override
    @Transactional
    public MarketDailyData collectDailyData() {
        LocalDate tradeDate = DateUtil.getCurrentTradingDay();
        return collectDataForDate(tradeDate);
    }

    @Override
    @Transactional
    public MarketDailyData collectDataForDate(LocalDate tradeDate) {
        log.info("开始采集 {} 的行情数据（东方财富实时API）...", tradeDate);

        MarketDailyData existing = marketDailyDataMapper.selectByTradeDate(tradeDate);
        if (existing != null) {
            log.info("日期 {} 的数据已存在，将更新", tradeDate);
        }

        MarketDailyData data = new MarketDailyData();
        data.setTradeDate(tradeDate);

        // 依次采集各类数据，各方法独立处理异常，失败自动兜底模拟
        collectShIndex(data);
        sleepBriefly();
        collectMarketBreadth(data);
        sleepBriefly();
        collectNorthFlow(data);
        sleepBriefly();
        collectOverseasData(data);
        sleepBriefly();
        collectMarginData(data);
        sleepBriefly();
        collectEtfData(data);

        // 保存或更新
        if (existing != null) {
            data.setId(existing.getId());
            marketDailyDataMapper.updateById(data);
        } else {
            marketDailyDataMapper.insert(data);
        }

        log.info("数据采集完成 - 上证指数: {}, 成交额: {}亿, 北向: {}亿",
                data.getShIndex(), data.getTotalVolume(), data.getNorthFlow());
        return data;
    }

    /**
     * API调用间隔，避免请求过于频繁
     */
    private void sleepBriefly() {
        try {
            Thread.sleep(2000);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== 上证指数 ====================

    /**
     * 采集上证指数 - 东方财富实时行情API
     * secid=1.000001 上证指数
     * f43=最新价, f170=涨跌幅(%)
     */
    private void collectShIndex(MarketDailyData data) {
        Optional<JSONObject> respData = fetchQuote(EM_QUOTE_URL + "?secid=1.000001&fields=f43,f170");

        if (respData.isPresent()) {
            JSONObject d = respData.get();
            BigDecimal price = d.getBigDecimal("f43");
            BigDecimal change = d.getBigDecimal("f170");

            if (price != null && price.compareTo(BigDecimal.ZERO) > 0) {
                // 东方财富指数类字段原始值 ×100，需除以100还原
                BigDecimal realPrice = price.divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
                BigDecimal realChange = change != null
                        ? change.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;
                data.setShIndex(realPrice);
                data.setShIndexChange(realChange);
                log.debug("上证指数: {} ({}%)", realPrice, realChange);
                return;
            }
        }
        // API失败兜底
        log.warn("上证指数API获取失败，使用模拟数据");
        data.setShIndex(BigDecimal.valueOf(3000 + RANDOM.nextDouble() * 500)
                .setScale(2, RoundingMode.HALF_UP));
        data.setShIndexChange(BigDecimal.valueOf(RANDOM.nextDouble() * 6 - 3)
                .setScale(4, RoundingMode.HALF_UP));
    }

    // ==================== 市场宽度 ====================

    /**
     * 采集市场宽度数据（两市成交额、成交量、涨跌家数、跌停数量）
     * 两市成交额 = 上证成交额 + 深证成交额（元转亿元）
     * 两市成交量 = 上证成交量 + 深证成交量（手, f47字段）
     */
    private void collectMarketBreadth(MarketDailyData data) {
        // 1. 获取上证成交额(f48)和成交量(f47)
        BigDecimal shVolume = null;
        BigDecimal shTurnover = null;
        Optional<JSONObject> shData = fetchQuote(EM_QUOTE_URL + "?secid=1.000001&fields=f47,f48");
        if (shData.isPresent()) {
            shVolume = shData.get().getBigDecimal("f48");
            shTurnover = shData.get().getBigDecimal("f47");
        }

        // 2. 获取深证成指成交额和成交量
        BigDecimal szVolume = null;
        BigDecimal szTurnover = null;
        Optional<JSONObject> szData = fetchQuote(EM_QUOTE_URL + "?secid=0.399001&fields=f47,f48");
        if (szData.isPresent()) {
            szVolume = szData.get().getBigDecimal("f48");
            szTurnover = szData.get().getBigDecimal("f47");
        }

        // 计算两市成交额(元 → 亿元)
        if (shVolume != null && szVolume != null) {
            BigDecimal total = shVolume.add(szVolume)
                    .divide(BigDecimal.valueOf(100_000_000), 2, RoundingMode.HALF_UP);
            data.setTotalVolume(total);
            log.debug("两市成交额: {}亿 (上证: {}亿, 深证: {}亿)", total,
                    shVolume.divide(BigDecimal.valueOf(100_000_000), 2, RoundingMode.HALF_UP),
                    szVolume.divide(BigDecimal.valueOf(100_000_000), 2, RoundingMode.HALF_UP));
        }

        // 计算两市成交量(手 → 万手)
        if (shTurnover != null && szTurnover != null) {
            BigDecimal totalTurnover = shTurnover.add(szTurnover)
                    .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP);
            data.setTotalTurnover(totalTurnover);
            log.debug("两市成交量: {}万手 (上证: {}手, 深证: {}手)", totalTurnover, shTurnover, szTurnover);
        }

        // 3. 尝试获取涨跌家数、跌停数（指数扩展字段，若为null则兜底模拟）
        collectMarketStatistics(data);

        // 成交额兜底
        if (data.getTotalVolume() == null) {
            data.setTotalVolume(BigDecimal.valueOf(5000 + RANDOM.nextDouble() * 8000)
                    .setScale(2, RoundingMode.HALF_UP));
        }
        // 成交量兜底
        if (data.getTotalTurnover() == null) {
            data.setTotalTurnover(BigDecimal.valueOf(20000 + RANDOM.nextDouble() * 50000)
                    .setScale(0, RoundingMode.HALF_UP));
        }
    }

    /**
     * 采集市场涨跌统计
     * 通过上证指数扩展字段获取涨跌家数/跌停数
     * f104=上涨家数 f105=下跌家数 f102=涨停家数 f103=跌停家数
     */
    private void collectMarketStatistics(MarketDailyData data) {
        Optional<JSONObject> extData = fetchQuote(
                EM_QUOTE_URL + "?secid=1.000001&fields=f104,f105,f102,f103");

        if (extData.isPresent()) {
            JSONObject d = extData.get();
            Integer rise = d.getInteger("f104");
            Integer fall = d.getInteger("f105");
            Integer limitDown = d.getInteger("f103");

            // 非交易时段 f104/f105 返回 0，此时 rise+fall=0 视为无效数据，走兜底模拟
            int riseVal = rise != null ? rise : 0;
            int fallVal = fall != null ? fall : 0;
            if (riseVal > 0 || fallVal > 0) {
                data.setRiseCount(riseVal);
                data.setFallCount(fallVal);
                data.setLimitDownCount(limitDown != null ? limitDown : 0);
                log.debug("涨跌家数(API): 涨{} 跌{} 跌停{}", riseVal, fallVal, limitDown);
                return;
            }
        }
        // 兜底模拟（非交易时段或无数据时）
        data.setRiseCount(500 + RANDOM.nextInt(3500));
        data.setFallCount(500 + RANDOM.nextInt(3500));
        data.setLimitDownCount(RANDOM.nextInt(50));
        log.debug("涨跌家数(模拟): 涨{} 跌{} 跌停{}",
                data.getRiseCount(), data.getFallCount(), data.getLimitDownCount());
    }

    // ==================== 北向资金 ====================

    /**
     * 采集北向资金 - 东方财富港股通资金流向API
     * 新版API返回格式:
     * {"data":{"hk2sh":["2026-07-20,0.00"],"sh2hk":["2026-07-20,0.00"],
     *           "hk2sz":["2026-07-20,0.00"],"sz2hk":["2026-07-20,0.00"]}}
     * 北向资金 = hk2sh + hk2sz (港股通沪 + 港股通深), 南向 = sh2hk + sz2hk
     * 净流入单位万元，转为亿元存储
     */
    private void collectNorthFlow(MarketDailyData data) {
        try {
            String url = EM_NORTH_FLOW_URL
                    + "?fields1=f1,f2,f3,f4&fields2=f51,f52&klt=1&lmt=1";
            String resp = httpClientUtil.doGetWithRetry(url, maxRetry, retryIntervalMs);
            JSONObject json = httpClientUtil.parseJson(resp);

            if (json != null) {
                JSONObject d = json.getJSONObject("data");
                if (d != null) {
                    // 新版API格式: hk2sh/sh2hk/hk2sz/sz2hk 各自是数组
                    BigDecimal hk2sh = extractFlowValue(d, "hk2sh");
                    BigDecimal hk2sz = extractFlowValue(d, "hk2sz");

                    if (hk2sh != null || hk2sz != null) {
                        BigDecimal totalWan = (hk2sh != null ? hk2sh : BigDecimal.ZERO)
                                .add(hk2sz != null ? hk2sz : BigDecimal.ZERO);
                        BigDecimal flowYi = totalWan.divide(
                                BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
                        data.setNorthFlow(flowYi);
                        log.debug("北向资金: {}亿 (沪:{}万, 深:{}万)", flowYi, hk2sh, hk2sz);
                        return;
                    }

                    // 兼容旧版格式: klineInfos
                    JSONArray klineInfos = d.getJSONArray("klineInfos");
                    if (klineInfos != null && !klineInfos.isEmpty()) {
                        String latest = klineInfos.getString(klineInfos.size() - 1);
                        String[] parts = latest.split(",");
                        if (parts.length >= 2) {
                            BigDecimal flowWan = new BigDecimal(parts[1]);
                            BigDecimal flowYi = flowWan.divide(
                                    BigDecimal.valueOf(10000), 2, RoundingMode.HALF_UP);
                            data.setNorthFlow(flowYi);
                            log.debug("北向资金(旧版): {}亿", flowYi);
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("北向资金API异常: {}", e.getMessage());
        }
        // 兜底
        log.warn("北向资金API获取失败，使用模拟数据");
        data.setNorthFlow(BigDecimal.valueOf(RANDOM.nextDouble() * 150 - 50)
                .setScale(2, RoundingMode.HALF_UP));
    }

    /**
     * 从北向资金API返回的data中提取指定字段的净流入值(万元)
     * 字段格式: ["2026-07-20,0.00"] → 解析逗号分隔取第二个值
     */
    private BigDecimal extractFlowValue(JSONObject data, String key) {
        JSONArray arr = data.getJSONArray(key);
        if (arr != null && !arr.isEmpty()) {
            String item = arr.getString(0);
            if (item != null) {
                String[] parts = item.split(",");
                if (parts.length >= 2) {
                    try {
                        return new BigDecimal(parts[1]);
                    } catch (NumberFormatException e) {
                        log.debug("北向资金{}解析失败: {}", key, item);
                    }
                }
            }
        }
        return null;
    }

    // ==================== 海外市场数据 ====================

    /**
     * 采集海外市场数据
     * 美元指数: 133.UDI (f43=最新价)
     * 费城半导体: 100.SOX (f43=最新价, f170=涨跌幅)
     * 标普500: 100.SPX, 纳斯达克: 100.NDX
     * 日经225: 100.N225, 恒生指数: 100.HSI
     * 美债收益率: 尝试获取，失败则模拟
     */
    private void collectOverseasData(MarketDailyData data) {
        // 美元指数
        fetchIndexValue("133.UDI", "f43")
                .ifPresent(v -> data.setUsdIndex(v.setScale(4, RoundingMode.HALF_UP)));

        // 费城半导体指数
        fetchIndexValue("100.SOX", "f43")
                .ifPresent(v -> data.setSoxIndex(v.setScale(2, RoundingMode.HALF_UP)));
        fetchIndexValue("100.SOX", "f170")
                .ifPresent(v -> data.setSoxIndexChange(v.setScale(4, RoundingMode.HALF_UP)));

        // 美债收益率 - 尝试多个 secid
        Optional<BigDecimal> bond = fetchIndexValue("133.US10YR", "f43");
        if (!bond.isPresent()) {
            bond = fetchIndexValue("134.US10YR", "f43");
        }
        bond.ifPresent(v -> data.setUsBondYield(v.setScale(4, RoundingMode.HALF_UP)));

        // 全球股指涨跌幅
        JSONObject globalStatus = new JSONObject();
        fetchIndexValue("100.SPX", "f170")
                .ifPresent(c -> globalStatus.put("snp500", c.setScale(2, RoundingMode.HALF_UP)));
        fetchIndexValue("100.NDX", "f170")
                .ifPresent(c -> globalStatus.put("nasdaq", c.setScale(2, RoundingMode.HALF_UP)));
        fetchIndexValue("100.N225", "f170")
                .ifPresent(c -> globalStatus.put("nikkei", c.setScale(2, RoundingMode.HALF_UP)));
        fetchIndexValue("100.HSI", "f170")
                .ifPresent(c -> globalStatus.put("hsi", c.setScale(2, RoundingMode.HALF_UP)));
        data.setGlobalMarketStatus(globalStatus.toJSONString());

        // ===== 兜底模拟（API 未获取到的字段）= =====
        if (data.getUsdIndex() == null) {
            data.setUsdIndex(BigDecimal.valueOf(100 + RANDOM.nextDouble() * 8)
                    .setScale(4, RoundingMode.HALF_UP));
        }
        if (data.getUsBondYield() == null) {
            data.setUsBondYield(BigDecimal.valueOf(3.5 + RANDOM.nextDouble() * 2)
                    .setScale(4, RoundingMode.HALF_UP));
        }
        if (data.getSoxIndex() == null) {
            data.setSoxIndex(BigDecimal.valueOf(3000 + RANDOM.nextDouble() * 2000)
                    .setScale(2, RoundingMode.HALF_UP));
            data.setSoxIndexChange(BigDecimal.valueOf(RANDOM.nextDouble() * 8 - 4)
                    .setScale(4, RoundingMode.HALF_UP));
        }
        if (!globalStatus.containsKey("snp500")) {
            globalStatus.put("snp500", randomPct());
            globalStatus.put("nasdaq", randomPct());
            globalStatus.put("nikkei", randomPct());
            globalStatus.put("hsi", randomPct());
            data.setGlobalMarketStatus(globalStatus.toJSONString());
        }
    }

    // ==================== 两融余额 ====================

    /**
     * 采集两融余额 - 东方财富数据中心API
     * reportName=RPTA_RZRQ_LSHJ (融资融券历史汇总)
     * RZRQYE=融资融券总余额(单位:元), DIM_DATE=数据日期
     * 获取最近2条记录，计算当日余额和环比变化率，元转亿元存储
     */
    private void collectMarginData(MarketDailyData data) {
        try {
            String url = EM_MARGIN_URL
                    + "?reportName=RPTA_RZRQ_LSHJ"
                    + "&columns=DIM_DATE,RZRQYE"
                    + "&pageNumber=1&pageSize=2"
                    + "&sortColumns=DIM_DATE&sortTypes=-1"
                    + "&source=WEB&client=WEB";
            String resp = httpClientUtil.doGetWithRetry(url, maxRetry, retryIntervalMs);
            JSONObject json = httpClientUtil.parseJson(resp);

            if (json != null && Boolean.TRUE.equals(json.getBoolean("success"))) {
                JSONObject result = json.getJSONObject("result");
                if (result != null) {
                    JSONArray arr = result.getJSONArray("data");
                    if (arr != null && arr.size() >= 2) {
                        // RZRQYE 单位是元，转为亿元
                        BigDecimal todayYuan = arr.getJSONObject(0).getBigDecimal("RZRQYE");
                        BigDecimal yesterdayYuan = arr.getJSONObject(1).getBigDecimal("RZRQYE");

                        if (todayYuan != null) {
                            BigDecimal todayYi = todayYuan.divide(
                                    BigDecimal.valueOf(100_000_000), 2, RoundingMode.HALF_UP);
                            data.setMarginBalance(todayYi);

                            if (yesterdayYuan != null && yesterdayYuan.compareTo(BigDecimal.ZERO) > 0) {
                                BigDecimal change = todayYuan.subtract(yesterdayYuan)
                                        .divide(yesterdayYuan, 6, RoundingMode.HALF_UP)
                                        .multiply(BigDecimal.valueOf(100))
                                        .setScale(4, RoundingMode.HALF_UP);
                                data.setMarginBalanceChange(change);
                            }
                            log.debug("两融余额: {}亿, 变化率: {}%",
                                    data.getMarginBalance(), data.getMarginBalanceChange());
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("两融余额API异常: {}", e.getMessage());
        }
        // 兜底
        log.warn("两融余额API获取失败，使用模拟数据");
        data.setMarginBalance(BigDecimal.valueOf(14000 + RANDOM.nextDouble() * 2000)
                .setScale(2, RoundingMode.HALF_UP));
        data.setMarginBalanceChange(BigDecimal.valueOf(RANDOM.nextDouble() * 2 - 1)
                .setScale(4, RoundingMode.HALF_UP));
    }

    // ==================== ETF数据 ====================

    /**
     * 采集宽基ETF净申购数据
     * 注：ETF净申购数据需接入基金公司或专业数据服务商API，
     * 东方财富公开API不直接提供，暂保留模拟值
     */
    private void collectEtfData(MarketDailyData data) {
        BigDecimal etfSub = BigDecimal.valueOf(RANDOM.nextDouble() * 40 - 10)
                .setScale(2, RoundingMode.HALF_UP);
        data.setEtfNetSubscription(etfSub);
        log.debug("ETF净申购(模拟): {}亿", etfSub);
    }

    // ==================== 工具方法 ====================

    /**
     * 调用东方财富行情API，返回响应中的 data 对象
     */
    private Optional<JSONObject> fetchQuote(String url) {
        try {
            String resp = httpClientUtil.doGetWithRetry(url, maxRetry, retryIntervalMs);
            JSONObject json = httpClientUtil.parseJson(resp);
            if (json != null) {
                JSONObject data = json.getJSONObject("data");
                if (data != null) {
                    return Optional.of(data);
                }
            }
        } catch (Exception e) {
            log.debug("API调用失败 {}: {}", url, e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * 获取全球指数指定字段的值
     * 东方财富指数类字段原始值 ×100，在此统一除以100还原
     */
    private Optional<BigDecimal> fetchIndexValue(String secid, String field) {
        String url = EM_QUOTE_URL + "?secid=" + secid + "&fields=" + field;
        Optional<JSONObject> data = fetchQuote(url);
        if (data.isPresent()) {
            BigDecimal value = data.get().getBigDecimal(field);
            if (value != null) {
                BigDecimal realValue = value.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP);
                log.debug("全球指数 {} {} = {} (原始: {})", secid, field, realValue, value);
                return Optional.of(realValue);
            }
        }
        return Optional.empty();
    }

    /**
     * 生成随机涨跌幅（兜底用）
     */
    private BigDecimal randomPct() {
        return BigDecimal.valueOf(RANDOM.nextDouble() * 2 - 1)
                .setScale(2, RoundingMode.HALF_UP);
    }
}
