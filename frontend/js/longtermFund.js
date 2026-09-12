/**
 * 长期基金分析模块
 * 从板块长期趋势筛选顺势和逆势投资机会
 */
const longtermModule = {

    data: null,

    async init() {
        document.getElementById('longterm-content').innerHTML =
            '<div class="loading"><div class="spinner"></div>' +
            '<p style="margin-top:12px;color:var(--text-muted)">正在分析板块长期趋势...</p></div>';
        await this.loadData();
    },

    async loadData() {
        try {
            const data = await api.getLongTermAnalysis(10);
            if (data && (data.momentum || data.contrarian)) {
                this.data = data;
                this.render();
            } else {
                this.showError('数据加载失败：返回数据为空');
            }
        } catch (err) {
            this.showError('请求异常: ' + (err.message || '未知错误'));
        }
    },

    showError(msg) {
        document.getElementById('longterm-content').innerHTML =
            '<div class="rec-error">' +
            '<p>⚠ ' + utils.escHtml(msg) + '</p>' +
            '<button class="btn" onclick="longtermModule.init()">重试</button>' +
            '</div>';
    },

    render() {
        const d = this.data;
        const mList = d.momentum || [];
        const cList = d.contrarian || [];

        let html = '';

        // === 策略说明区 ===
        html += '<div class="lt-header">';
        html += '<h3 class="lt-page-title">📆 长期基金投资分析</h3>';
        html += '<p class="lt-desc">基于板块长期趋势（1M/3M/6M涨跌幅 + MA20/60均线排列），筛选顺势持有和逆势定投机会，并匹配对应ETF</p>';
        html += '</div>';

        // === 顺势持有区 ===
        html += '<div class="rec-section">';
        html += '<div class="section-title lt-section-green">📈 顺势持有 · 多头排列板块</div>';
        html += '<p class="lt-hint">板块处于多头排列(价>MA20>MA60)，趋势向上，适合持有对应ETF</p>';

        if (mList.length === 0) {
            html += '<div class="lt-empty">暂无符合条件的顺势板块</div>';
        } else {
            html += '<div class="rec-grid">';
            html += '<div class="rec-card rec-table-full">';
            html += '<table class="rec-table"><thead><tr>' +
                '<th>#</th><th>板块</th><th>趋势</th>' +
                '<th>最新价</th><th>1月%</th><th>3月%</th><th>6月%</th>' +
                '<th>MA20</th><th>MA60</th>' +
                '<th>推荐ETF</th><th>ETF涨跌</th><th>规模(亿)</th>' +
                '</tr></thead><tbody>';
            mList.forEach((it, i) => {
                html += this.renderRow(it, i + 1);
            });
            html += '</tbody></table></div></div>';
        }
        html += '</div>';

        // === 逆势定投区 ===
        html += '<div class="rec-section">';
        html += '<div class="section-title lt-section-red">📉 逆势定投 · 空头排列/底部企稳板块</div>';
        html += '<p class="lt-hint">板块处于空头排列或底部企稳信号，适合定投等待反转，关注对应ETF</p>';

        if (cList.length === 0) {
            html += '<div class="lt-empty">暂无符合条件的逆势板块</div>';
        } else {
            html += '<div class="rec-grid">';
            html += '<div class="rec-card rec-table-full">';
            html += '<table class="rec-table"><thead><tr>' +
                '<th>#</th><th>板块</th><th>趋势</th>' +
                '<th>最新价</th><th>1月%</th><th>3月%</th><th>6月%</th>' +
                '<th>MA20</th><th>MA60</th>' +
                '<th>推荐ETF</th><th>ETF涨跌</th><th>规模(亿)</th>' +
                '</tr></thead><tbody>';
            cList.forEach((it, i) => {
                html += this.renderRow(it, i + 1);
            });
            html += '</tbody></table></div></div>';
        }
        html += '</div>';

        // === 使用提示 ===
        html += '<div class="rules-disclaimer" style="margin-top:20px">';
        html += '<p><strong>使用说明:</strong></p>';
        html += '<p>• 顺势持有: 均线多头排列+6个月上涨的板块，趋势明确，可长期持有对应ETF | 关注止损位(跌破MA60减仓)</p>';
        html += '<p>• 逆势定投: 均线空头排列+持续下跌的板块，适合分批定投等待反转 | 关注底部企稳信号(站上MA20)</p>';
        html += '<p>• 趋势评分基于MA排列+涨跌幅综合计算，评分越高趋势越明确 | 数据基于日线前复权，仅供参考不构成投资建议</p>';
        html += '</div>';

        document.getElementById('longterm-content').innerHTML = html;
    },

    renderRow(it, rank) {
        // 趋势标签颜色
        const isUp = it.trendLabel && (it.trendLabel.includes('多头') || it.trendLabel.includes('上涨'));
        const isDown = it.trendLabel && (it.trendLabel.includes('空头') || it.trendLabel.includes('下跌'));
        const tlClass = isUp ? 'tag-up' : isDown ? 'tag-down' : 'tag-flat';

        const pctCls = (v) => {
            if (v == null) return '';
            const n = Number(v);
            return n > 0 ? 'up' : n < 0 ? 'down' : '';
        };

        const fmt = (v, dg) => {
            if (v == null) return '--';
            const n = Number(v);
            if (dg === 0) return n.toFixed(0);
            if (dg === 2) return n.toFixed(2);
            return n.toFixed(dg != null ? dg : 2);
        };

        const fmtPct = (v) => {
            if (v == null) return '--';
            const n = Number(v);
            const p = n > 0 ? '+' : '';
            return p + n.toFixed(2) + '%';
        };

        const isChampion = rank === 1;

        return '<tr' + (isChampion ? ' class="rec-champion"' : '') + '>' +
            '<td><span class="rec-rank">' + rank + '</span></td>' +
            '<td><span class="rec-name">' + utils.escHtml(it.sectorName) + '</span>' +
            '<br><span class="rec-code">' + utils.escHtml(it.sectorCode) + '</span></td>' +
            '<td><span class="rec-tag ' + tlClass + '">' + utils.escHtml(it.trendLabel || '--') + '</span>' +
            '<br><span class="rec-sub" style="font-size:10px;color:var(--text-muted)">评分 ' + (it.trendScore || '--') + '</span></td>' +
            '<td>' + fmt(it.latestPrice, 2) + '</td>' +
            '<td class="' + pctCls(it.pct1M) + '">' + fmtPct(it.pct1M) + '</td>' +
            '<td class="' + pctCls(it.pct3M) + '">' + fmtPct(it.pct3M) + '</td>' +
            '<td class="' + pctCls(it.pct6M) + '">' + fmtPct(it.pct6M) + '</td>' +
            '<td>' + fmt(it.ma20, 2) + '</td>' +
            '<td>' + fmt(it.ma60, 2) + '</td>' +
            '<td>' + (it.fundName
                ? '<span class="rec-name">' + utils.escHtml(it.fundName) + '</span><br><span class="rec-code">' + utils.escHtml(it.fundCode) + '</span>'
                : '<span class="text-muted">--</span>') + '</td>' +
            '<td class="' + pctCls(it.fundChangePct) + '">' + fmtPct(it.fundChangePct) + '</td>' +
            '<td>' + fmt(it.fundScale, 2) + '</td>' +
            '</tr>';
    }
};
