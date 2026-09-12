/* =====================================================
   A股实时行情信号跟踪系统 - 工具函数
   ===================================================== */

const utils = {
    /**
     * 格式化日期 yyyy-MM-dd
     */
    formatDate(date) {
        if (!date) return '';
        const d = typeof date === 'string' ? new Date(date) : date;
        const y = d.getFullYear();
        const m = String(d.getMonth() + 1).padStart(2, '0');
        const day = String(d.getDate()).padStart(2, '0');
        return `${y}-${m}-${day}`;
    },

    /**
     * 格式化日期时间 yyyy-MM-dd HH:mm:ss
     */
    formatDateTime(dateStr) {
        if (!dateStr) return '';
        const d = new Date(dateStr);
        return `${this.formatDate(d)} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}:${String(d.getSeconds()).padStart(2, '0')}`;
    },

    /**
     * 格式化大数字（万/亿）
     */
    formatNumber(num) {
        if (num == null) return '--';
        const n = Number(num);
        const abs = Math.abs(n);
        const sign = n < 0 ? '-' : '';

        if (abs >= 100000000) {
            return sign + (abs / 100000000).toFixed(2) + '亿';
        }
        if (abs >= 10000) {
            return sign + (abs / 10000).toFixed(2) + '万';
        }
        if (abs >= 1000) {
            return sign + abs.toFixed(0).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
        }
        return n.toFixed(2);
    },

    /**
     * 格式化涨跌幅（带符号）
     */
    formatChange(num) {
        if (num == null) return '--';
        const n = Number(num);
        const prefix = n > 0 ? '+' : '';
        return prefix + n.toFixed(2) + '%';
    },

    /**
     * 格式化成交量（万手 → 亿手自动换算）
     */
    formatVolume(num) {
        if (num == null) return '--';
        const n = Number(num);
        if (n >= 10000) {
            return (n / 10000).toFixed(2) + '亿';
        }
        return n.toFixed(0).replace(/\B(?=(\d{3})+(?!\d))/g, ',');
    },

    /**
     * 根据得分获取颜色类名
     */
    getScoreColorClass(score) {
        if (score >= 80) return 'score-green';
        if (score >= 60) return 'score-yellow';
        if (score >= 40) return 'score-orange';
        return 'score-red';
    },

    /**
     * 根据得分获取背景类名
     */
    getScoreBgClass(score) {
        if (score >= 80) return 'bg-green';
        if (score >= 60) return 'bg-yellow';
        if (score >= 40) return 'bg-orange';
        return 'bg-red';
    },

    /**
     * 根据得分获取建议区域类名
     */
    getSuggestionLevelClass(score) {
        if (score >= 80) return 'level-active';
        if (score >= 60) return 'level-positive';
        if (score >= 40) return 'level-neutral';
        return 'level-defensive';
    },

    /**
     * 获取预警等级显示
     */
    getWarningLevelDisplay(level) {
        const map = {
            '一级预警': '一级',
            '二级预警': '二级',
            '三级预警': '三级'
        };
        return map[level] || level;
    },

    /**
     * 获取预警等级CSS类名
     */
    getWarningLevelClass(level) {
        const map = {
            '一级预警': 'level-1',
            '二级预警': 'level-2',
            '三级预警': 'level-3'
        };
        return map[level] || 'level-3';
    },

    /**
     * 获取预警等级tag类名
     */
    getWarningTagClass(level) {
        const map = {
            '一级预警': 'l1',
            '二级预警': 'l2',
            '三级预警': 'l3'
        };
        return map[level] || 'l3';
    },

    /**
     * 获取星期几
     */
    getWeekDay(date) {
        const d = typeof date === 'string' ? new Date(date) : date;
        const days = ['日', '一', '二', '三', '四', '五', '六'];
        return '星期' + days[d.getDay()];
    },

    /**
     * 判断是否为交易时段
     */
    isTradingHours() {
        const now = new Date();
        const h = now.getHours();
        const m = now.getMinutes();
        const totalMinutes = h * 60 + m;

        // 9:30 - 11:30 或 13:00 - 15:00
        const morningStart = 9 * 60 + 30;
        const morningEnd = 11 * 60 + 30;
        const afternoonStart = 13 * 60;
        const afternoonEnd = 15 * 60;

        return (totalMinutes >= morningStart && totalMinutes <= morningEnd) ||
               (totalMinutes >= afternoonStart && totalMinutes <= afternoonEnd);
    },

    /**
     * 获取市场状态文本
     */
    getMarketStatus() {
        const now = new Date();
        const day = now.getDay();
        if (day === 0 || day === 6) return { text: '休市', cls: 'closed' };

        const h = now.getHours();
        const m = now.getMinutes();
        const t = h * 60 + m;

        if (t >= 9 * 60 + 30 && t <= 11 * 60 + 30) return { text: '交易中', cls: 'trading' };
        if (t >= 13 * 60 && t <= 15 * 60) return { text: '交易中', cls: 'trading' };
        return { text: '已收盘', cls: 'closed' };
    },

    /**
     * 防抖
     */
    debounce(fn, delay = 300) {
        let timer;
        return function (...args) {
            clearTimeout(timer);
            timer = setTimeout(() => fn.apply(this, args), delay);
        };
    },

    /**
     * 节流
     */
    throttle(fn, interval = 300) {
        let lastTime = 0;
        return function (...args) {
            const now = Date.now();
            if (now - lastTime >= interval) {
                lastTime = now;
                fn.apply(this, args);
            }
        };
    },

    /**
     * 显示Toast通知
     */
    showToast(message, type = 'info') {
        let container = document.querySelector('.toast-container');
        if (!container) {
            container = document.createElement('div');
            container.className = 'toast-container';
            document.body.appendChild(container);
        }

        const toast = document.createElement('div');
        toast.className = `toast ${type}`;
        toast.textContent = message;
        container.appendChild(toast);

        setTimeout(() => {
            toast.style.opacity = '0';
            toast.style.transform = 'translateX(100%)';
            toast.style.transition = 'all 0.3s ease';
            setTimeout(() => toast.remove(), 300);
        }, 3000);
    },

    /**
     * 请求浏览器通知权限
     */
    async requestNotificationPermission() {
        if (!('Notification' in window)) return false;
        if (Notification.permission === 'granted') return true;
        const result = await Notification.requestPermission();
        return result === 'granted';
    },

    /**
     * 发送浏览器通知
     */
    sendNotification(title, body) {
        if (!('Notification' in window) || Notification.permission !== 'granted') return;
        new Notification(title, {
            body,
            icon: 'data:image/svg+xml,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100"><text y=".9em" font-size="90">📊</text></svg>',
            tag: 'stock-warning'
        });
    },

    /**
     * HTML转义
     */
    escHtml(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    },

    /**
     * 根据涨跌幅返回CSS类名
     */
    pctClass(val) {
        if (val == null) return '';
        return val > 0 ? 'up' : val < 0 ? 'down' : '';
    }
};
