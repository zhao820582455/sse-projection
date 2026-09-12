# sse-projection

> 股票监控与智能推荐系统：Spring Boot 后端（行情采集、长期基金/个股/板块推荐、信号与预警）+ 前端实时看板（SSE 推送），含 MySQL 初始化脚本。

A 股实时行情信号跟踪系统 —— 基于 **12 项反弹信号** 与 **7 类高位预警** 的量化监控平台，配套前端 SSE 实时看板。

## 技术栈

| 组件 | 版本 / 说明 |
|---|---|
| JDK | 8 |
| Spring Boot | 2.7.18 |
| 持久层 | MyBatis-Plus 3.5.3.1 |
| 连接池 | Druid 1.2.18 |
| 数据库 | MySQL（库表初始化见 `backend/src/main/resources/db/init.sql`） |
| JSON | FastJSON 1.2.83 |
| HTTP 客户端 | Apache HttpClient 4.5.14（行情/接口采集） |
| 前端 | 原生 HTML/CSS/JS + SSE（Server-Sent Events）实时推送，无构建步骤 |

## 功能模块

### 后端（Spring Boot，`com.stock.monitor`）
- **行情采集** `DataCollectionService`：定时拉取 A 股行情，落库 `market_daily_data`
- **反弹信号** `SignalService`：基于 12 项反弹信号生成 `rebound_signal_record`
- **高位预警** `WarningService`：7 类高位预警，落库 `top_warning_record`
- **智能推荐** `RecommendationService`：行业板块 / 概念板块 / 个股 / ETF 基金推荐
- **长期基金** `LongTermFundService`：长期持有基金分析与推荐
- **数据备份** `BackupService`：每日行情数据导出备份（见 `backup/`）
- **系统配置** `SystemConfig`：阈值、开关等可配置项

### 前端（静态页面，SSE 实时看板）
- 实时行情看板 `dashboard.js`
- 高位预警面板 `warnings.js`
- 智能推荐 `recommend.js`
- 长期基金 `longtermFund.js`
- 历史数据 `history.js`
- 通过 `api.js` 调用后端接口，`app.js` 负责路由与 SSE 连接，`utils.js` 公共工具

## 目录结构

```
sse-projection/
├── backend/                      # Spring Boot 后端
│   ├── pom.xml
│   ├── src/main/java/com/stock/monitor/
│   │   ├── StockMonitorApplication.java
│   │   ├── config/               # RestTemplate / Scheduler / Web 配置
│   │   ├── controller/           # Market / Recommendation / LongTermFund
│   │   ├── service/ (+ impl)     # 采集/信号/预警/推荐/长期基金/备份
│   │   ├── entity/               # MarketDailyData / ReboundSignalRecord / SystemConfig / TopWarningRecord
│   │   ├── mapper/               # MyBatis-Plus Mapper
│   │   ├── dto/ vo/ util/        # 响应对象 / 视图对象 / 工具
│   │   └── resources/
│   │       ├── application.yml    # 数据源等配置（不入仓库）
│   │       ├── db/init.sql        # 建表 + 初始化
│   │       └── mapper/            # MyBatis XML
├── frontend/                     # 静态前端（SSE 实时看板）
│   ├── index.html
│   ├── web.config
│   ├── css/style.css
│   └── js/                       # api / app / dashboard / warnings / recommend / longtermFund / history / utils
├── backup/                       # 每日行情数据备份 CSV
└── logs/                         # 运行日志（不入仓库）
```

## 后端主要接口

| 端点 | 说明 |
|---|---|
| `GET /api/recommend/all?topN=12` | 获取全部推荐（行业板块 / 概念板块 / 个股 / ETF 基金） |
| `GET /api/market/*` | 行情查询（详见 `MarketController`） |
| `GET /api/longTermFund/*` | 长期基金分析与推荐（详见 `LongTermFundController`） |

> 更多端点请直接查看 `backend/src/main/java/com/stock/monitor/controller/` 下各控制器源码。

## 数据库

初始化脚本：`backend/src/main/resources/db/init.sql`（建表 + 基础数据）。

```bash
mysql -h<host> -u<user> -p<password> <dbname> < backend/src/main/resources/db/init.sql
```

## 如何运行

### 后端
```bash
cd backend
# 修改 src/main/resources/application.yml 配置数据库连接
mvn clean package
java -jar target/stock-monitor-1.0.0.jar
```

### 前端
无需构建，将 `frontend/` 作为静态资源托管（如 Nginx、IIS，已含 `web.config`）或直接用任意静态服务器打开 `index.html`，前端通过 SSE 接收后端实时推送。

## 说明
- 后端定时任务依赖 `SchedulerConfig`，需保持服务常驻运行以完成行情采集与信号/预警生成。
- `logs/`、`target/`、`build.log`、`.idea/` 已通过 `.gitignore` 排除，不进版本库。
