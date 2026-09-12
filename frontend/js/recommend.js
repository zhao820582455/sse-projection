/* =====================================================
   A股实时行情信号跟踪系统 - 智能推荐模块
   板块推荐 / 个股推荐 / 基金推荐 渲染
   ===================================================== */

var recommendModule = {
    data: null,

    async init() {
        const container = document.getElementById('recommend-content');
        if (!container) return;
        container.innerHTML = '<div class="loading"><div class="spinner"></div><p style="margin-top:12px;color:var(--text-muted)">正在加载推荐数据...</p></div>';
        await this.loadData();
    },

    async loadData() {
        try {
            // _get 已自动解包 data 字段，直接拿到 sectors/funds/stocks
            const data = await api.getRecommendations(12);
            if (data && (data.industrySectors || data.conceptSectors || data.stocks || data.funds)) {
                this.data = data;
                this.render();
            } else {
                this.showError('数据加载失败：返回数据为空');
            }
        } catch (err) {
            this.showError('请求异常: ' + (err.message || '未知错误'));
        }
    },

    render() {
        const container = document.getElementById('recommend-content');
        if (!container || !this.data) return;

        let html = '';

        // ========== 板块推荐 ==========
        html += '<div class="rec-section">';
        html += '<h3 class="section-title">📊 板块推荐</h3>';
        html += '<div class="rec-grid">';

        // 行业板块
        html += '<div class="rec-card">';
        html += '<div class="rec-card-hd"><span class="rec-badge badge-ind">行业板块</span><span class="rec-card-sub">按资金流入排序</span></div>';
        html += '<table class="rec-table">';
        html += '<thead><tr><th>#</th><th>板块</th><th>涨跌幅</th><th>主力净流入</th></tr></thead><tbody>';
        const industries = this.data.industrySectors || [];
        for (let i = 0; i < Math.min(industries.length, 6); i++) {
            const s = industries[i];
            html += this.sectorRow(s, i === 0);
        }
        if (industries.length === 0) {
            html += this.emptyRow(4, '暂未获取到行业数据');
        }
        html += '</tbody></table></div>';

        // 概念板块
        html += '<div class="rec-card">';
        html += '<div class="rec-card-hd"><span class="rec-badge badge-con">概念板块</span><span class="rec-card-sub">按涨跌幅排序</span></div>';
        html += '<table class="rec-table">';
        html += '<thead><tr><th>#</th><th>板块</th><th>涨跌幅</th><th>主力净流入</th></tr></thead><tbody>';
        const concepts = this.data.conceptSectors || [];
        for (let i = 0; i < Math.min(concepts.length, 6); i++) {
            const s = concepts[i];
            html += this.sectorRow(s, i === 0);
        }
        if (concepts.length === 0) {
            html += this.emptyRow(4, '暂未获取到概念板块数据');
        }
        html += '</tbody></table></div>';

        html += '</div></div>';

        // ========== 个股推荐 ==========
        html += '<div class="rec-section">';
        html += '<h3 class="section-title">🎯 个股推荐</h3>';
        html += '<table class="rec-table rec-table-full">';
        html += '<thead><tr><th>#</th><th>股票</th><th>价格</th><th>涨跌幅</th><th>换手率</th><th>量比</th><th>策略</th><th>推荐原因</th></tr></thead><tbody>';
        const stocks = this.data.stocks || [];
        if (stocks.length > 0) {
            for (let i = 0; i < Math.min(stocks.length, 12); i++) {
                const s = stocks[i];
                html += this.stockRow(s, i);
            }
        } else {
            html += this.emptyRow(8, '暂未获取到个股推荐数据');
        }
        html += '</tbody></table></div>';

        // ========== 基金推荐 ==========
        html += '<div class="rec-section">';
        html += '<h3 class="section-title">💰 ETF基金推荐</h3>';
        html += '<div class="rec-grid">';

        const funds = this.data.funds || [];
        const fundTypes = { '宽基': [], '行业': [], '主题': [] };
        for (const f of funds) {
            if (fundTypes[f.fundType]) fundTypes[f.fundType].push(f);
        }

        for (const [ftype, flist] of Object.entries(fundTypes)) {
            html += '<div class="rec-card">';
            html += `<div class="rec-card-hd"><span class="rec-badge badge-fund">${ftype}ETF</span><span class="rec-card-sub">按涨跌幅排序</span></div>`;
            html += '<table class="rec-table">';
            html += '<thead><tr><th>#</th><th>名称</th><th>价格</th><th>涨跌幅</th><th>规模(亿)</th></tr></thead><tbody>';
            for (let i = 0; i < Math.min(flist.length, 4); i++) {
                const f = flist[i];
                html += this.fundRow(f, i);
            }
            if (flist.length === 0) {
                html += this.emptyRow(5, `暂未获取到${ftype}ETF数据`);
            }
            html += '</tbody></table></div>';
        }

        html += '</div></div>';

        container.innerHTML = html;
    },

    sectorRow(s, highlight) {
        const cls = highlight ? 'rec-champion' : '';
        const flow = s.mainFlow != null ? utils.formatNumber(s.mainFlow) : '--';
        const chg = s.changePct != null ? utils.formatChange(s.changePct) : '--';
        const chgCls = s.changePct != null ? utils.pctClass(s.changePct) : '';
        return `<tr class="${cls}">
            <td><span class="rec-rank">${s.ranking}</span></td>
            <td><span class="rec-name">${utils.escHtml(s.name)}</span><br><span class="rec-code">${s.code}</span></td>
            <td class="${chgCls}">${chg}</td>
            <td>${flow}亿</td>
        </tr>`;
    },

    stockRow(s, idx) {
        const chgCls = s.changePct != null ? utils.pctClass(s.changePct) : '';
        const strategyLabel = s.strategy === 'north_flow'
            ? '<span class="rec-tag tag-north">北向增持</span>'
            : '<span class="rec-tag tag-vol">量价突破</span>';
        const price = s.price != null ? s.price : '--';
        const chg = s.changePct != null ? utils.formatChange(s.changePct) : '--';
        const tr = s.turnoverRate != null ? s.turnoverRate + '%' : '--';
        const vr = s.volumeRatio != null ? s.volumeRatio : '--';
        return `<tr>
            <td><span class="rec-rank">${s.ranking}</span></td>
            <td><span class="rec-name">${utils.escHtml(s.name)}</span><br><span class="rec-code">${s.code}</span></td>
            <td>${price}</td>
            <td class="${chgCls}">${chg}</td>
            <td>${tr}</td>
            <td>${vr}</td>
            <td>${strategyLabel}</td>
            <td style="font-size:12px;color:var(--text-secondary)">${utils.escHtml(s.reason || '--')}</td>
        </tr>`;
    },

    fundRow(f, idx) {
        const chgCls = f.changePct != null ? utils.pctClass(f.changePct) : '';
        const price = f.price != null ? f.price : '--';
        const chg = f.changePct != null ? utils.formatChange(f.changePct) : '--';
        const cap = f.totalCap != null ? utils.formatNumber(f.totalCap) : '--';
        return `<tr>
            <td><span class="rec-rank">${f.ranking}</span></td>
            <td><span class="rec-name">${utils.escHtml(f.name)}</span><br><span class="rec-code">${f.code}</span></td>
            <td>${price}</td>
            <td class="${chgCls}">${chg}</td>
            <td>${cap}</td>
        </tr>`;
    },

    emptyRow(cols, msg) {
        return `<tr><td colspan="${cols}" class="rec-empty">${msg}</td></tr>`;
    },

    showError(msg) {
        const container = document.getElementById('recommend-content');
        if (container) {
            container.innerHTML = `<div class="rec-error">
                <p>⚠ ${msg}</p>
                <button class="btn btn-sm" onclick="recommendModule.loadData()">重试</button>
            </div>`;
        }
    }
};
