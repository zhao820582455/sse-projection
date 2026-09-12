/* =====================================================
   A股实时行情信号跟踪系统 - 主应用控制器
   管理Tab切换、全局状态、手动修正功能
   ===================================================== */

const app = {
    currentTab: 'dashboard',

    /**
     * 应用初始化
     */
    async init() {
        this.bindEvents();
        await dashboard.init();
        this.startClock();
    },

    /**
     * 绑定事件
     */
    bindEvents() {
        // Tab切换
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const tab = btn.dataset.tab;
                this.switchTab(tab);
            });
        });

        // 手动刷新按钮
        const refreshBtn = document.getElementById('btn-refresh');
        if (refreshBtn) {
            refreshBtn.addEventListener('click', async () => {
                refreshBtn.disabled = true;
                refreshBtn.textContent = '刷新中...';
                try {
                    await api.refreshData();
                    utils.showToast('数据刷新成功', 'success');
                    if (this.currentTab === 'dashboard') {
                        await dashboard.loadData();
                    }
                } catch (err) {
                    utils.showToast('刷新失败: ' + err.message, 'error');
                }
                refreshBtn.disabled = false;
                refreshBtn.textContent = '手动刷新';
            });
        }

        // 预警弹窗关闭
        const modalClose = document.getElementById('modal-close');
        if (modalClose) {
            modalClose.addEventListener('click', () => {
                document.getElementById('warning-modal').classList.remove('active');
            });
        }

        // 历史查询
        const historyQueryBtn = document.getElementById('btn-history-query');
        if (historyQueryBtn) {
            historyQueryBtn.addEventListener('click', () => historyModule.loadHistory());
        }

        // 导出CSV
        const exportBtn = document.getElementById('btn-export-csv');
        if (exportBtn) {
            exportBtn.addEventListener('click', () => historyModule.exportCSV());
        }

        // 手动修正加载
        const muLoadBtn = document.getElementById('btn-mu-load');
        if (muLoadBtn) {
            muLoadBtn.addEventListener('click', () => this.loadManualUpdateData());
        }

        // 手动修正保存
        const muSaveBtn = document.getElementById('btn-mu-save');
        if (muSaveBtn) {
            muSaveBtn.addEventListener('click', () => this.saveManualUpdate());
        }

        // 规则说明弹窗
        const btnRules = document.getElementById('btn-rules');
        const rulesModal = document.getElementById('rules-modal');
        const btnRulesClose = document.getElementById('btn-rules-close');

        if (btnRules && rulesModal) {
            btnRules.addEventListener('click', () => {
                rulesModal.style.display = 'flex';
                document.body.style.overflow = 'hidden';
            });
        }
        if (btnRulesClose && rulesModal) {
            btnRulesClose.addEventListener('click', () => {
                rulesModal.style.display = 'none';
                document.body.style.overflow = '';
            });
        }
        if (rulesModal) {
            rulesModal.addEventListener('click', (e) => {
                if (e.target === rulesModal) {
                    rulesModal.style.display = 'none';
                    document.body.style.overflow = '';
                }
            });
        }
    },

    /**
     * 切换Tab
     */
    async switchTab(tab) {
        this.currentTab = tab;

        // 更新Tab按钮状态
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.classList.toggle('active', btn.dataset.tab === tab);
        });

        // 更新内容区
        document.querySelectorAll('.tab-content').forEach(el => {
            el.classList.toggle('active', el.id === `tab-${tab}`);
        });

        // 销毁旧模块定时器
        if (tab !== 'dashboard') {
            dashboard.destroy();
        }

        // 初始化对应模块
        switch (tab) {
            case 'dashboard':
                await dashboard.init();
                break;
            case 'warnings':
                await warnings.init();
                break;
            case 'history':
                await historyModule.init();
                break;
            case 'manual-update':
                // 手动修正tab，等待用户操作
                break;
            case 'recommend':
                await recommendModule.init();
                break;
            case 'longterm':
                await longtermModule.init();
                break;
        }
    },

    /**
     * 加载手动修正数据
     */
    async loadManualUpdateData() {
        const dateInput = document.getElementById('mu-date');
        const tableBody = document.getElementById('mu-table-body');

        if (!dateInput || !tableBody) return;

        const date = dateInput.value;
        if (!date) {
            utils.showToast('请先选择日期', 'error');
            return;
        }

        tableBody.innerHTML = '<tr><td colspan="5" class="text-center"><div class="spinner"></div></td></tr>';

        try {
            const signals = await api.getSignalsByDate(date);

            let html = '';
            signals.forEach((s, index) => {
                html += `
                <tr>
                    <td>${s.signalName}</td>
                    <td>${s.score}分</td>
                    <td>
                        <input type="checkbox"
                               id="mu-signal-${index}"
                               ${s.isTriggered === 1 ? 'checked' : ''}
                               onchange="app.trackManualChange(${index}, '${s.signalCode}', this.checked)">
                    </td>
                    <td>
                        <input type="text" class="remark-input"
                               id="mu-remark-${index}"
                               value="${s.remark || ''}"
                               placeholder="修改原因">
                    </td>
                    <td style="color:var(--text-muted);font-size:11px">${s.dataSource || '--'}</td>
                </tr>`;
            });

            // 存储信号数据用于保存
            tableBody.innerHTML = html;
            tableBody.dataset.signals = JSON.stringify(signals);
            tableBody.dataset.date = date;

        } catch (err) {
            tableBody.innerHTML = `<tr><td colspan="5" class="text-center" style="color:var(--accent-red)">
                加载失败: ${err.message}</td></tr>`;
        }
    },

    /**
     * 记录手动修改
     */
    manualChanges: [],

    trackManualChange(index, signalCode, checked) {
        const remarkEl = document.getElementById(`mu-remark-${index}`);
        const remark = remarkEl ? remarkEl.value : '';

        // 查找是否已有该信号的修改记录
        const existing = this.manualChanges.find(c => c.signalCode === signalCode);
        if (existing) {
            existing.isTriggered = checked ? 1 : 0;
            existing.remark = remark;
        } else {
            this.manualChanges.push({
                signalCode,
                isTriggered: checked ? 1 : 0,
                remark
            });
        }
    },

    /**
     * 保存手动修正
     */
    async saveManualUpdate() {
        const tableBody = document.getElementById('mu-table-body');
        if (!tableBody || !tableBody.dataset.date) {
            utils.showToast('请先加载数据', 'error');
            return;
        }

        const date = tableBody.dataset.date;

        // 收集所有信号当前状态
        const signals = JSON.parse(tableBody.dataset.signals || '[]');
        const updates = signals.map((s, index) => {
            const checkbox = document.getElementById(`mu-signal-${index}`);
            const remarkEl = document.getElementById(`mu-remark-${index}`);
            return {
                signalCode: s.signalCode,
                isTriggered: checkbox ? (checkbox.checked ? 1 : 0) : s.isTriggered,
                remark: remarkEl ? remarkEl.value : ''
            };
        });

        const saveBtn = document.getElementById('btn-mu-save');
        saveBtn.disabled = true;
        saveBtn.textContent = '保存中...';

        try {
            // 逐个保存修改
            for (const update of updates) {
                await api.manualUpdate(date, update.signalCode, update.isTriggered, update.remark);
            }

            utils.showToast(`手动修正已保存 (${updates.length}条记录)`, 'success');
            this.manualChanges = [];

        } catch (err) {
            utils.showToast('保存失败: ' + err.message, 'error');
        }

        saveBtn.disabled = false;
        saveBtn.textContent = '保存修正';
    },

    /**
     * 启动时钟
     */
    startClock() {
        const updateClock = () => {
            const now = new Date();
            const timeStr = String(now.getHours()).padStart(2, '0') + ':' +
                           String(now.getMinutes()).padStart(2, '0') + ':' +
                           String(now.getSeconds()).padStart(2, '0');
            const clockEl = document.getElementById('header-clock');
            if (clockEl) clockEl.textContent = timeStr;

            const dateEl = document.getElementById('header-date');
            if (dateEl) {
                dateEl.textContent = utils.formatDate(now) + ' ' + utils.getWeekDay(now);
            }

            const statusEl = document.getElementById('market-status');
            if (statusEl) {
                const status = utils.getMarketStatus();
                statusEl.textContent = status.text;
                statusEl.className = 'market-status ' + status.cls;
            }
        };

        updateClock();
        setInterval(updateClock, 1000);
    }
};

// 页面加载完成后初始化
document.addEventListener('DOMContentLoaded', () => {
    app.init();
});
