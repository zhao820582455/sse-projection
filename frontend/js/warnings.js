/* =====================================================
   A股实时行情信号跟踪系统 - 预警中心模块
   ===================================================== */

const warnings = {
    currentFilter: 'all',
    notificationEnabled: false,

    /**
     * 初始化预警中心
     */
    async init() {
        await utils.requestNotificationPermission();
        this.notificationEnabled = Notification.permission === 'granted';
        await this.loadWarnings();
    },

    /**
     * 加载预警数据
     */
    async loadWarnings() {
        const container = document.getElementById('warnings-content');
        container.innerHTML = '<div class="loading"><div class="spinner"></div></div>';

        try {
            const warningList = await api.getCurrentWarnings();
            const stopLossRules = await api.getStopLossRules();
            this.render(warningList, stopLossRules);
        } catch (err) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="icon">🔔</div>
                    <div class="text">预警数据加载失败：${err.message}</div>
                    <button class="refresh-btn mt-16" onclick="warnings.loadWarnings()">重新加载</button>
                </div>`;
        }
    },

    /**
     * 渲染预警中心
     */
    render(warningList, stopLossRules) {
        const container = document.getElementById('warnings-content');

        if (!warningList || warningList.length === 0) {
            container.innerHTML = `
                <div class="empty-state">
                    <div class="icon">✅</div>
                    <div class="text">当前无触发预警，市场运行正常</div>
                    <button class="refresh-btn mt-16" onclick="warnings.loadWarnings()">刷新检查</button>
                </div>
            `;
            return;
        }

        // 统计各等级数量
        const l1Count = warningList.filter(w => w.warningLevel === '一级预警').length;
        const l2Count = warningList.filter(w => w.warningLevel === '二级预警').length;
        const l3Count = warningList.filter(w => w.warningLevel === '三级预警').length;

        // 检查是否有新的一级预警需要弹窗
        if (l1Count > 0) {
            this.checkNewL1Warning(warningList);
        }

        let html = '';

        // 概览统计
        html += `
        <div class="warning-overview">
            <div class="warning-badge l1 ${this.currentFilter === '一级预警' ? 'active' : ''}"
                 onclick="warnings.filterByLevel('一级预警')">
                一级预警 <strong>${l1Count}</strong>
            </div>
            <div class="warning-badge l2 ${this.currentFilter === '二级预警' ? 'active' : ''}"
                 onclick="warnings.filterByLevel('二级预警')">
                二级预警 <strong>${l2Count}</strong>
            </div>
            <div class="warning-badge l3 ${this.currentFilter === '三级预警' ? 'active' : ''}"
                 onclick="warnings.filterByLevel('三级预警')">
                三级预警 <strong>${l3Count}</strong>
            </div>
            ${this.currentFilter !== 'all' ? `
            <button class="refresh-btn" style="background:var(--bg-card);border:1px solid var(--border-color);color:var(--text-primary)"
                    onclick="warnings.filterByLevel('all')">显示全部</button>
            ` : ''}
        </div>`;

        // 过滤
        const filtered = this.currentFilter === 'all'
            ? warningList
            : warningList.filter(w => w.warningLevel === this.currentFilter);

        // 预警列表
        html += '<div class="warning-list">';

        filtered.forEach(w => {
            const levelClass = utils.getWarningLevelClass(w.warningLevel);
            const tagClass = utils.getWarningTagClass(w.warningLevel);
            const levelDisplay = utils.getWarningLevelDisplay(w.warningLevel);

            html += `
            <div class="warning-card ${levelClass} ${w.warningLevel === '一级预警' ? 'pulse' : ''}"
                 onclick="warnings.toggleWarningDetail(this)">
                <div class="warning-header">
                    <span class="warning-name">${w.warningName}</span>
                    <span class="warning-level-tag ${tagClass}">${levelDisplay}</span>
                </div>
                <div class="warning-meta">
                    <span>标的: ${w.targetName || '--'}</span>
                    <span>触发时间: ${utils.formatDateTime(w.createTime)}</span>
                    <span>状态: ${w.status === 'ACTIVE' ? '🔴 活跃' : '🟢 已解除'}</span>
                </div>
                <div class="warning-basis">
                    <strong>触发依据：</strong><br>${w.triggerBasis || '暂无详细数据'}
                </div>
            </div>`;
        });

        html += '</div>';

        // 强制止损规则
        if (stopLossRules && stopLossRules.length > 0) {
            html += '<div class="section-title mt-24">🛑 强制止损规则</div>';
            html += '<div class="warning-list">';

            stopLossRules.forEach(rule => {
                const isL1 = rule.level === '一级止损';
                html += `
                <div class="warning-card ${isL1 ? 'level-1' : 'level-2'}">
                    <div class="warning-header">
                        <span class="warning-name">${rule.rule}</span>
                        <span class="warning-level-tag ${isL1 ? 'l1' : 'l2'}">${rule.level}</span>
                    </div>
                    <div class="warning-meta">
                        <span>条件: ${rule.condition}</span>
                    </div>
                    <div class="warning-basis" style="display:block">
                        <strong>应对措施：</strong>${rule.action}
                    </div>
                </div>`;
            });

            html += '</div>';
        }

        container.innerHTML = html;
    },

    /**
     * 按等级过滤
     */
    filterByLevel(level) {
        this.currentFilter = level;
        this.loadWarnings();
    },

    /**
     * 切换预警详情
     */
    toggleWarningDetail(card) {
        card.classList.toggle('expanded');
    },

    /**
     * 检查是否有新的一级预警
     */
    checkNewL1Warning(warningList) {
        const l1Warnings = warningList.filter(w => w.warningLevel === '一级预警');
        if (l1Warnings.length === 0) return;

        // 检查是否刚触发（5分钟内）
        const now = new Date();
        l1Warnings.forEach(w => {
            if (w.createTime) {
                const triggerTime = new Date(w.createTime);
                const diff = now - triggerTime;
                if (diff < 5 * 60 * 1000) {
                    // 新触发的一级预警，弹窗提醒
                    this.showL1WarningPopup(w);
                    // 发送浏览器通知
                    if (this.notificationEnabled) {
                        utils.sendNotification(
                            `⚠ 一级预警: ${w.warningName}`,
                            `标的: ${w.targetName || '--'} - ${w.triggerBasis || ''}`
                        );
                    }
                }
            }
        });
    },

    /**
     * 显示一级预警弹窗
     */
    showL1WarningPopup(warning) {
        const modal = document.getElementById('warning-modal');
        const title = document.getElementById('modal-title');
        const body = document.getElementById('modal-body');

        if (!modal || !title || !body) return;

        title.textContent = `⚠ 一级预警触发`;
        body.innerHTML = `
            <strong>预警名称：</strong>${warning.warningName}<br>
            <strong>标的名称：</strong>${warning.targetName || '--'}<br>
            <strong>触发依据：</strong>${warning.triggerBasis || '暂无'}<br>
            <strong>触发时间：</strong>${utils.formatDateTime(warning.createTime)}<br>
            <br>
            <span style="color:var(--warning-l1);font-weight:600">
                请立即关注并评估风险！
            </span>
        `;

        modal.classList.add('active');
    }
};
