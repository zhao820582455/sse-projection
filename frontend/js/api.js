/* =====================================================
   A股实时行情信号跟踪系统 - API服务层
   统一管理所有后端API调用
   ===================================================== */

const API_BASE = 'http://127.0.0.1:1919/api';

const api = {
    /**
     * 通用GET请求
     */
    async _get(path) {
        try {
            const response = await fetch(`${API_BASE}${path}`, {
                method: 'GET',
                headers: { 'Accept': 'application/json' },
                signal: AbortSignal.timeout(15000)
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}: ${response.statusText}`);
            }

            const result = await response.json();
            if (result.code !== 200) {
                throw new Error(result.message || '请求失败');
            }
            return result.data;

        } catch (err) {
            if (err.name === 'TimeoutError') {
                console.error(`请求超时: ${path}`);
                throw new Error('请求超时，请检查网络连接');
            }
            console.error(`API请求失败 [${path}]:`, err.message);
            throw err;
        }
    },

    /**
     * 通用POST请求
     */
    async _post(path, body) {
        try {
            const response = await fetch(`${API_BASE}${path}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'Accept': 'application/json'
                },
                body: JSON.stringify(body),
                signal: AbortSignal.timeout(15000)
            });

            if (!response.ok) {
                throw new Error(`HTTP ${response.status}`);
            }

            const result = await response.json();
            if (result.code !== 200) {
                throw new Error(result.message || '请求失败');
            }
            return result.data;

        } catch (err) {
            if (err.name === 'TimeoutError') {
                throw new Error('请求超时');
            }
            console.error(`API请求失败 [${path}]:`, err.message);
            throw err;
        }
    },

    // ---- 核心接口 ----

    /**
     * 获取当日全量数据（行情、信号、得分、预警）
     * GET /api/current
     */
    async getCurrentData() {
        return this._get('/current');
    },

    /**
     * 获取历史得分数据
     * GET /api/history?days=N
     */
    async getHistory(days = 30) {
        return this._get(`/history?days=${days}`);
    },

    /**
     * 获取仓位操作建议
     * GET /api/suggestion
     */
    async getSuggestion() {
        return this._get('/suggestion');
    },

    /**
     * 获取当前触发的所有预警
     * GET /api/warnings/current
     */
    async getCurrentWarnings() {
        return this._get('/warnings/current');
    },

    /**
     * 获取历史预警记录
     * GET /api/warnings/history?days=N
     */
    async getWarningHistory(days = 15) {
        return this._get(`/warnings/history?days=${days}`);
    },

    /**
     * 获取强制止损规则
     * GET /api/warnings/stop-loss
     */
    async getStopLossRules() {
        return this._get('/warnings/stop-loss');
    },

    /**
     * 手动修正信号状态
     * POST /api/manual-update
     */
    async manualUpdate(tradeDate, signalCode, isTriggered, remark = '') {
        return this._post('/manual-update', {
            tradeDate,
            signalCode,
            isTriggered,
            remark
        });
    },

    /**
     * 手动触发数据刷新
     * POST /api/refresh
     */
    async refreshData() {
        return this._post('/refresh', {});
    },

    /**
     * 获取指定日期的信号详情
     * GET /api/date/{date}/signals
     */
    async getSignalsByDate(date) {
        return this._get(`/date/${date}/signals`);
    },

    /**
     * 获取指定日期的预警详情
     * GET /api/date/{date}/warnings
     */
    async getWarningsByDate(date) {
        return this._get(`/date/${date}/warnings`);
    },

    /**
     * 获取智能推荐数据（板块、个股、基金）
     * GET /api/recommend/all?topN=12
     */
    async getRecommendations(topN = 12) {
        return this._get(`/recommend/all?topN=${topN}`);
    },

    /**
     * 获取长期基金分析数据
     * GET /api/longterm/analysis?topN=10
     */
    async getLongTermAnalysis(topN = 10) {
        return this._get(`/longterm/analysis?topN=${topN}`);
    }
};
