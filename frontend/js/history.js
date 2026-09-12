/* =====================================================
   A股实时行情信号跟踪系统 - 历史回溯模块
   ===================================================== */

const historyModule = {
    chart: null,
    historyData: [],

    /**
     * 初始化历史回溯
     */
    async init() {
        this.initDatePicker();
        await this.loadHistory();
    },

    /**
     * 初始化日期选择器
     */
    initDatePicker() {
        const today = utils.formatDate(new Date());
        const thirtyDaysAgo = utils.formatDate(new Date(Date.now() - 30 * 24 * 60 * 60 * 1000));

        document.getElementById('history-date-start').value = thirtyDaysAgo;
        document.getElementById('history-date-end').value = today;
    },

    /**
     * 加载历史数据
     */
    async loadHistory() {
        const container = document.getElementById('history-chart-container');
        container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

        const startDate = document.getElementById('history-date-start').value;
        const endDate = document.getElementById('history-date-end').value;

        const days = Math.ceil((new Date(endDate) - new Date(startDate)) / (1000 * 60 * 60 * 24));

        try {
            const data = await api.getHistory(Math.max(days, 5));
            this.historyData = data || [];
            this.renderChart(data || []);
        } catch (err) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="icon">📈</div>
                    <div class="text">历史数据加载失败：${err.message}</div>
                    <button class="refresh-btn mt-16" onclick="historyModule.loadHistory()">重新加载</button>
                </div>`;
        }
    },

    /**
     * 渲染折线图
     */
    renderChart(data) {
        const container = document.getElementById('history-chart-container');
        let canvas = document.getElementById('score-chart');

        // 如果 canvas 不存在（被 spinner 清掉了），重新创建
        if (!canvas) {
            canvas = document.createElement('canvas');
            canvas.id = 'score-chart';
            container.innerHTML = '';
            container.appendChild(canvas);
        }

        // 销毁旧图表
        if (this.chart) {
            this.chart.destroy();
            this.chart = null;
        }

        const ctx = canvas.getContext('2d');

        // 按日期排序
        const sorted = [...data].sort((a, b) => {
            return new Date(a.tradeDate) - new Date(b.tradeDate);
        });

        const labels = sorted.map(d => d.tradeDate);
        const scores = sorted.map(d => d.totalScore);
        const triggeredCounts = sorted.map(d => d.triggeredCount || 0);

        // 计算颜色区域
        const colors = scores.map(s => {
            if (s >= 80) return '#00c853';
            if (s >= 60) return '#ffd600';
            if (s >= 40) return '#ff9100';
            return '#ff1744';
        });

        this.chart = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: '综合得分',
                        data: scores,
                        borderColor: '#58a6ff',
                        backgroundColor: 'rgba(88, 166, 255, 0.08)',
                        borderWidth: 2.5,
                        fill: true,
                        tension: 0.3,
                        pointBackgroundColor: colors,
                        pointBorderColor: colors,
                        pointRadius: 5,
                        pointHoverRadius: 8,
                        yAxisID: 'y'
                    },
                    {
                        label: '达标信号数',
                        data: triggeredCounts,
                        borderColor: 'rgba(163, 113, 247, 0.7)',
                        backgroundColor: 'transparent',
                        borderWidth: 1.5,
                        borderDash: [6, 3],
                        tension: 0.3,
                        pointRadius: 3,
                        pointHoverRadius: 5,
                        pointBackgroundColor: '#a371f7',
                        yAxisID: 'y1'
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: {
                    mode: 'index',
                    intersect: false
                },
                onClick: (event, elements) => {
                    if (elements.length > 0) {
                        const index = elements[0].index;
                        const date = sorted[index].tradeDate;
                        this.showDayDetail(date);
                    }
                },
                plugins: {
                    title: {
                        display: true,
                        text: '反弹信号综合得分趋势',
                        color: '#e6edf3',
                        font: { size: 15, weight: '600' },
                        padding: { bottom: 20 }
                    },
                    legend: {
                        labels: {
                            color: '#8b949e',
                            usePointStyle: true,
                            padding: 20
                        }
                    },
                    tooltip: {
                        backgroundColor: '#1c2129',
                        borderColor: '#30363d',
                        borderWidth: 1,
                        titleColor: '#e6edf3',
                        bodyColor: '#8b949e',
                        callbacks: {
                            label: function(ctx) {
                                if (ctx.dataset.label === '综合得分') {
                                    return `综合得分: ${ctx.raw}分`;
                                }
                                return `达标信号: ${ctx.raw}个`;
                            }
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: 'rgba(48, 54, 61, 0.5)' },
                        ticks: {
                            color: '#8b949e',
                            maxTicksLimit: 10,
                            maxRotation: 45
                        }
                    },
                    y: {
                        type: 'linear',
                        position: 'left',
                        min: 0,
                        max: 100,
                        grid: { color: 'rgba(48, 54, 61, 0.5)' },
                        ticks: {
                            color: '#8b949e',
                            callback: v => v + '分'
                        },
                        title: {
                            display: true,
                            text: '得分',
                            color: '#8b949e'
                        }
                    },
                    y1: {
                        type: 'linear',
                        position: 'right',
                        min: 0,
                        max: 12,
                        grid: { drawOnChartArea: false },
                        ticks: {
                            color: '#a371f7',
                            stepSize: 2
                        },
                        title: {
                            display: true,
                            text: '信号数',
                            color: '#a371f7'
                        }
                    }
                }
            },
            plugins: [{
                id: 'scoreZones',
                beforeDraw(chart) {
                    const { ctx, chartArea: { left, right, top, bottom }, scales: { y } } = chart;

                    const zones = [
                        { yMin: 80, yMax: 100, color: 'rgba(0, 200, 83, 0.04)' },
                        { yMin: 60, yMax: 80, color: 'rgba(255, 214, 0, 0.04)' },
                        { yMin: 40, yMax: 60, color: 'rgba(255, 145, 0, 0.04)' },
                        { yMin: 0, yMax: 40, color: 'rgba(255, 23, 68, 0.04)' }
                    ];

                    zones.forEach(zone => {
                        const yTop = y.getPixelForValue(zone.yMax);
                        const yBottom = y.getPixelForValue(zone.yMin);

                        ctx.fillStyle = zone.color;
                        ctx.fillRect(left, yTop, right - left, yBottom - yTop);
                    });
                }
            }]
        });

        // 确保图表尺寸正确
        if (this.chart) {
            this.chart.resize();
        }
    },

    /**
     * 显示某日详情
     */
    async showDayDetail(date) {
        const panel = document.getElementById('day-detail-panel');

        panel.innerHTML = '<div class="loading"><div class="spinner"></div></div>';
        panel.style.display = 'block';

        try {
            const [signals, dayWarnings] = await Promise.all([
                api.getSignalsByDate(date),
                api.getWarningsByDate(date)
            ]);

            const triggeredSignals = (signals || []).filter(s => s.isTriggered === 1);
            const totalScore = triggeredSignals.reduce((sum, s) => sum + s.score, 0);

            let html = `<h3>📅 ${date} 详情</h3>`;

            html += `<p style="color:var(--text-secondary);margin-bottom:16px">
                综合得分: <strong class="${utils.getScoreColorClass(totalScore)}">${totalScore}分</strong> |
                达标信号: <strong>${triggeredSignals.length}/12</strong>
            </p>`;

            // 信号详情表格
            html += `<table class="manual-update-table" style="margin-bottom:16px">
                <thead><tr>
                    <th>信号名称</th><th>分值</th><th>状态</th><th>数据来源</th>
                </tr></thead><tbody>`;

            (signals || []).forEach(s => {
                const status = s.isTriggered === 1
                    ? '<span style="color:var(--accent-green)">✓ 达标</span>'
                    : '<span style="color:var(--text-muted)">✗ 未达标</span>';
                html += `<tr>
                    <td>${s.signalName}</td>
                    <td>${s.score}分</td>
                    <td>${status}</td>
                    <td style="font-size:12px;color:var(--text-muted)">${s.dataSource || '--'}</td>
                </tr>`;
            });

            html += '</tbody></table>';

            // 当日预警
            if (dayWarnings && dayWarnings.length > 0) {
                html += '<h4 style="margin-bottom:8px;color:var(--warning-l1)">⚠ 当日预警</h4>';
                dayWarnings.forEach(w => {
                    html += `<div style="font-size:13px;color:var(--text-secondary);padding:6px 0;border-bottom:1px solid var(--border-color)">
                        ${w.warningName} (${utils.getWarningLevelDisplay(w.warningLevel)}) - ${w.targetName || ''}
                    </div>`;
                });
            } else {
                html += '<div style="font-size:13px;color:var(--text-muted)">当日无触发预警</div>';
            }

            panel.innerHTML = html;

        } catch (err) {
            panel.innerHTML = `
                <div class="empty-state">
                    <div class="text">加载详情失败：${err.message}</div>
                </div>`;
        }
    },

    /**
     * 导出CSV
     */
    exportCSV() {
        if (!this.historyData || this.historyData.length === 0) {
            utils.showToast('没有可导出的数据', 'error');
            return;
        }

        const headers = ['日期', '综合得分', '得分等级', '达标信号数'];
        const rows = this.historyData.map(d => [
            d.tradeDate,
            d.totalScore,
            d.scoreLevel || '',
            d.triggeredCount || 0
        ]);

        let csv = '\uFEFF' + headers.join(',') + '\n';
        rows.forEach(row => {
            csv += row.join(',') + '\n';
        });

        const blob = new Blob([csv], { type: 'text/csv;charset=utf-8' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `反弹信号历史数据_${utils.formatDate(new Date())}.csv`;
        a.click();
        URL.revokeObjectURL(url);

        utils.showToast('导出成功', 'success');
    },

    /**
     * 销毁图表
     */
    destroy() {
        if (this.chart) {
            this.chart.destroy();
            this.chart = null;
        }
    }
};
