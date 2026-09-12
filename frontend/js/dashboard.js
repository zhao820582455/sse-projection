/* =====================================================
   A股实时行情信号跟踪系统 - 实时看板模块
   ===================================================== */

const dashboard = {
    refreshTimer: null,
    countdownTimer: null,
    countdownValue: 30,

    /**
     * 初始化看板
     */
    async init() {
        await this.loadData();
        this.startAutoRefresh();
        this.startClock();
    },

    /**
     * 加载看板数据
     */
    async loadData() {
        const container = document.getElementById('dashboard-content');
        container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

        try {
            const data = await api.getCurrentData();
            this.render(data);
        } catch (err) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="icon">📡</div>
                    <div class="text">数据加载失败：${err.message}</div>
                    <div class="text" style="margin-top:8px">请确保后端服务已启动 (http://localhost:1919)</div>
                    <button class="refresh-btn mt-16" onclick="dashboard.loadData()">重新加载</button>
                </div>
            `;
        }
    },

    /**
     * 渲染看板
     */
    render(data) {
        const container = document.getElementById('dashboard-content');
        if (!data) return;

        let html = '';

        // 核心数据卡片
        html += this.renderCoreData(data);

        // 12项信号
        html += '<div class="section-title mt-24">📊 反弹信号检测</div>';
        html += this.renderSignals(data.signals || []);

        // 仓位建议
        html += '<div class="section-title mt-24">💡 仓位操作建议</div>';
        html += this.renderSuggestion(data.totalScore, data.suggestion);

        // 当前预警摘要
        if (data.warnings && data.warnings.length > 0) {
            html += '<div class="section-title mt-24">⚠ 当前预警</div>';
            html += this.renderWarningSummary(data.warnings);
        }

        container.innerHTML = html;
        this.updateCountdown();
    },

    /**
     * 渲染核心数据卡片
     */
    renderCoreData(data) {
        const md = data.marketData || {};
        const score = data.totalScore || 0;
        const scoreColorClass = utils.getScoreColorClass(score);

        const shChange = md.shIndexChange;
        const changeClass = shChange >= 0 ? 'up' : 'down';
        const changePrefix = shChange >= 0 ? '+' : '';

        return `
        <div class="dashboard-grid">
            <div class="card text-center">
                <div class="card-title">上证指数</div>
                <div class="card-value ${changeClass}">${md.shIndex || '--'}</div>
                <div class="card-sub ${changeClass}">${changePrefix}${(shChange || 0).toFixed(2)}%</div>
            </div>

            <div class="card text-center">
                <div class="card-title">当前总得分</div>
                <div class="score-circle ${utils.getScoreBgClass(score)}">${score}</div>
                <div class="card-sub ${scoreColorClass}">${data.scoreLevel || '--'}</div>
            </div>

            <div class="card">
                <div class="card-title">北向资金</div>
                <div class="card-value ${(md.northFlow || 0) >= 0 ? 'up' : 'down'}">
                    ${utils.formatNumber(md.northFlow)}
                </div>
                <div class="card-sub">${(md.northFlow || 0) >= 0 ? '净流入' : '净流出'}</div>
            </div>

            <div class="card">
                <div class="card-title">两市成交额</div>
                <div class="card-value">${utils.formatNumber(md.totalVolume)}</div>
                <div class="card-sub">亿元</div>
            </div>

            <div class="card">
                <div class="card-title">两市成交量</div>
                <div class="card-value">${utils.formatVolume(md.totalTurnover)}</div>
                <div class="card-sub">万手</div>
            </div>

            <div class="card">
                <div class="card-title">涨跌家数</div>
                <div class="card-value">
                    <span class="up">${md.riseCount || 0}涨</span>
                    <span style="margin:0 4px;color:var(--text-muted)">/</span>
                    <span class="down">${md.fallCount || 0}跌</span>
                </div>
                <div class="card-sub">
                    <div style="display:flex;height:4px;background:var(--bg-secondary);border-radius:2px;margin-top:8px;overflow:hidden">
                        ${this._renderRatioBar(md.riseCount || 0, md.fallCount || 0)}
                    </div>
                </div>
            </div>

            <div class="card">
                <div class="card-title">跌停数量</div>
                <div class="card-value ${(md.limitDownCount || 0) >= 10 ? 'score-red' : 'score-green'}">
                    ${md.limitDownCount || 0}
                </div>
                <div class="card-sub">只</div>
            </div>

            <div class="card">
                <div class="card-title">美元指数</div>
                <div class="card-value">${md.usdIndex || '--'}</div>
                <div class="card-sub">美债: ${md.usBondYield ? md.usBondYield.toFixed(2) + '%' : '--'}</div>
            </div>

            <div class="card">
                <div class="card-title">费城半导体</div>
                <div class="card-value ${(md.soxIndexChange || 0) >= 0 ? 'up' : 'down'}">
                    ${md.soxIndex || '--'}
                </div>
                <div class="card-sub">${utils.formatChange(md.soxIndexChange)}</div>
            </div>
        </div>`;
    },

    _renderRatioBar(up, down) {
        const total = up + down;
        if (total === 0) return '<div style="flex:1;background:var(--text-muted)"></div>';
        const upPct = (up / total * 100).toFixed(1);
        return `
            <div style="width:${upPct}%;background:var(--up-color);transition:width 0.5s"></div>
            <div style="flex:1;background:var(--down-color);transition:width 0.5s"></div>`;
    },

    /**
     * 渲染12项信号卡片
     */
    renderSignals(signals) {
        if (!signals || signals.length === 0) {
            return '<div class="empty-state"><div class="text">暂无信号数据</div></div>';
        }

        let html = '<div class="signal-grid">';

        signals.forEach((s, index) => {
            const triggered = s.isTriggered === 1;
            html += `
            <div class="signal-card ${triggered ? 'triggered' : ''}"
                 onclick="dashboard.toggleSignalDetail(this)"
                 title="点击查看详情">
                <div class="signal-header">
                    <span class="signal-name">${s.signalName}</span>
                    <span class="signal-score">${s.score}分</span>
                </div>
                <div class="signal-status ${triggered ? 'triggered' : 'not-triggered'}">
                    ${triggered ? '✓ 达标' : '✗ 未达标'}
                </div>
                <div class="signal-detail">
                    ${s.dataSource || '无详细数据'}
                    ${s.remark ? `<br>备注: ${s.remark}` : ''}
                </div>
            </div>`;
        });

        html += '</div>';
        return html;
    },

    /**
     * 切换信号卡片展开/收起
     */
    toggleSignalDetail(card) {
        card.classList.toggle('expanded');
    },

    /**
     * 渲染仓位建议
     */
    renderSuggestion(score, suggestion) {
        if (!suggestion) suggestion = {};

        const levelClass = utils.getSuggestionLevelClass(score || 0);
        const scoreColorClass = utils.getScoreColorClass(score || 0);

        return `
        <div class="suggestion-area ${levelClass}">
            <div class="suggestion-title">📋 当前建议</div>
            <div class="suggestion-grid">
                <div class="suggestion-item">
                    <div class="label">建议仓位</div>
                    <div class="value ${scoreColorClass}">${suggestion.position || '--'}</div>
                </div>
                <div class="suggestion-item">
                    <div class="label">操作方向</div>
                    <div class="value ${scoreColorClass}">${suggestion.direction || '--'}</div>
                </div>
                <div class="suggestion-item">
                    <div class="label">综合得分</div>
                    <div class="value ${scoreColorClass}">${score || 0}分</div>
                </div>
                <div class="suggestion-item">
                    <div class="label">风险等级</div>
                    <div class="value ${scoreColorClass}">${suggestion.level || '--'}</div>
                </div>
            </div>
            <div class="suggestion-advice">
                <strong>操作建议：</strong>${suggestion.advice || '暂无建议'}<br>
                <strong>风险提示：</strong>${suggestion.riskNote || '请注意控制仓位风险'}
            </div>
        </div>`;
    },

    /**
     * 渲染预警摘要
     */
    renderWarningSummary(warnings) {
        const count = warnings.length;
        const l1 = warnings.filter(w => w.warningLevel === '一级预警').length;

        return `
        <div class="card" style="border-left: 3px solid var(--warning-l1); cursor: pointer;"
             onclick="app.switchTab('warnings')">
            <div style="display:flex;align-items:center;justify-content:space-between">
                <div>
                    <span style="font-size:16px;font-weight:600">当前触发 ${count} 项预警</span>
                    ${l1 > 0 ? `<span class="pulse" style="color:var(--warning-l1);margin-left:8px;font-size:14px">
                        含${l1}项一级预警
                    </span>` : ''}
                </div>
                <span style="color:var(--accent-blue);font-size:13px">查看详情 →</span>
            </div>
        </div>`;
    },

    /**
     * 启动自动刷新
     */
    startAutoRefresh() {
        // 清除旧定时器
        if (this.refreshTimer) clearInterval(this.refreshTimer);
        if (this.countdownTimer) clearInterval(this.countdownTimer);

        this.countdownValue = 30;

        // 倒计时更新
        this.countdownTimer = setInterval(() => {
            this.countdownValue--;
            if (this.countdownValue <= 0) this.countdownValue = 30;
            this.updateCountdown();
        }, 1000);

        // 数据刷新
        this.refreshTimer = setInterval(async () => {
            if (utils.isTradingHours()) {
                await this.loadData();
            }
        }, 30000);
    },

    /**
     * 更新倒计时显示
     */
    updateCountdown() {
        const el = document.getElementById('countdown-display');
        if (el) {
            el.textContent = this.countdownValue;
        }
    },

    /**
     * 启动实时时钟
     */
    startClock() {
        const updateClock = () => {
            const now = new Date();
            const timeStr = String(now.getHours()).padStart(2, '0') + ':' +
                           String(now.getMinutes()).padStart(2, '0') + ':' +
                           String(now.getSeconds()).padStart(2, '0');
            const clockEl = document.getElementById('header-clock');
            if (clockEl) clockEl.textContent = timeStr;

            // 更新日期
            const dateEl = document.getElementById('header-date');
            if (dateEl) {
                dateEl.textContent = utils.formatDate(now) + ' ' + utils.getWeekDay(now);
            }

            // 更新市场状态
            const statusEl = document.getElementById('market-status');
            if (statusEl) {
                const status = utils.getMarketStatus();
                statusEl.textContent = status.text;
                statusEl.className = 'market-status ' + status.cls;
            }
        };

        updateClock();
        setInterval(updateClock, 1000);
    },

    /**
     * 销毁看板（清理定时器）
     */
    destroy() {
        if (this.refreshTimer) clearInterval(this.refreshTimer);
        if (this.countdownTimer) clearInterval(this.countdownTimer);
    }
};
