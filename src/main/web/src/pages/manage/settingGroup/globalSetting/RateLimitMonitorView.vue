<script setup>
import WebSocketConnector from "@/api/websocket.js";
import {ElMessage} from "element-plus";
import {ref, onMounted, onUnmounted} from "vue";
import PermissionInfo from "@/auth/PermissionInfo.js";

// 检查权限
if (!PermissionInfo.hasPermission('VIEW_RATE_LIMIT_MONITOR')) {
    ElMessage({
        type: "error",
        message: "无权限访问限流监控页面"
    });
    // 可以在这里添加路由跳转逻辑，比如跳转到首页
}

const loading = ref(true);
const statistics = ref({
    totalCount: 0,
    byDimension: {},
    topIps: [],
    topQqs: []
});
const recentLogs = ref([]);
let refreshInterval = null;

const getStatistics = () => {
    WebSocketConnector.send({
        type: "getRateLimitStatistics"
    }).then((response) => {
        statistics.value = response.data.data;
        recentLogs.value = response.data.data.recentLogs || [];
        loading.value = false;
    }, (err) => {
        ElMessage({
            type: "error", message: "获取统计数据失败"
        });
        loading.value = false;
    });
};

onMounted(() => {
    getStatistics();
    refreshInterval = setInterval(getStatistics, 10000);
});

onUnmounted(() => {
    if (refreshInterval) {
        clearInterval(refreshInterval);
    }
});

const formatTime = (timeStr) => {
    if (!timeStr) return "";
    try {
        let date;
        // 处理后端返回的LocalDateTime数组格式 [年, 月, 日, 时, 分, 秒, 毫秒]
        if (Array.isArray(timeStr) && timeStr.length >= 6) {
            date = new Date(timeStr[0], timeStr[1] - 1, timeStr[2], timeStr[3], timeStr[4], timeStr[5], timeStr[6] || 0);
        } else {
            return timeStr;
        }
        
        if (isNaN(date.getTime())) return timeStr;
        
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        const seconds = String(date.getSeconds()).padStart(2, '0');
        
        return `${year}-${month}-${day} ${hours}:${minutes}:${seconds}`;
    } catch (error) {
        return timeStr;
    }
};

const getDimensionTagType = (dimension) => {
    const types = {
        IP: "",
        COOKIE: "success",
        QQ: "warning",
        OAUTH: "danger"
    };
    return types[dimension] || "info";
};
</script>

<template>
    <div style="display: flex;flex-direction: column;height: 100%">
        <div style="display: flex;flex-direction: row;margin-bottom: 24px;align-items: center">
            <el-text style="font-size: 24px;font-weight: bold">限流监控</el-text>
            <el-button style="margin-left: auto" @click="getStatistics" :loading="loading">
                刷新数据
            </el-button>
        </div>

        <el-scrollbar v-loading="loading">
            <div style="max-width: 1280px;width: min(85%,1280px);display: flex;flex-direction: column;gap: 24px;padding-bottom: 32px">
                <!-- 统计概览 -->
                <div style="display: grid;grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));gap: 16px">
                    <div class="panel-1" style="padding: 20px;display: flex;flex-direction: column;align-items: center">
                        <el-text type="info" size="small">24小时总限流次数</el-text>
                        <el-text style="font-size: 36px;font-weight: bold;margin-top: 8px;color: var(--el-color-danger)">
                            {{ statistics.totalCount?.toLocaleString() || 0 }}
                        </el-text>
                    </div>
                    
                    <div class="panel-1" style="padding: 20px;display: flex;flex-direction: column;align-items: center"
                         v-for="(count, dimension) in statistics.byDimension" :key="dimension">
                        <el-text type="info" size="small">{{ dimension }} 维度</el-text>
                        <el-text style="font-size: 28px;font-weight: bold;margin-top: 8px">
                            {{ count?.toLocaleString() || 0 }}
                        </el-text>
                        <el-tag :type="getDimensionTagType(dimension)" size="small" style="margin-top: 4px">
                            {{ dimension }}
                        </el-tag>
                    </div>
                </div>

                <!-- TOP IP 排行 -->
                <div class="panel-1" style="padding: 20px">
                    <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">TOP 限流 IP 地址</el-text>
                    <div v-if="statistics.topIps && statistics.topIps.length > 0">
                        <div v-for="(item, index) in statistics.topIps" :key="index"
                             style="display: flex;align-items: center;padding: 8px 0;border-bottom: 1px solid var(--el-border-color-lighter)">
                            <el-text style="width: 40px;font-weight: bold" :type="index < 3 ? 'danger' : 'info'">
                                {{ index + 1 }}
                            </el-text>
                            <el-text style="flex: 1;font-family: monospace">{{ item.identifier }}</el-text>
                            <el-tag type="danger" size="small">{{ item.count }} 次</el-tag>
                        </div>
                    </div>
                    <el-empty v-else description="暂无数据" :image-size="60"/>
                </div>

                <!-- TOP QQ 排行 -->
                <div class="panel-1" style="padding: 20px">
                    <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">TOP 限流 QQ 号</el-text>
                    <div v-if="statistics.topQqs && statistics.topQqs.length > 0">
                        <div v-for="(item, index) in statistics.topQqs" :key="index"
                             style="display: flex;align-items: center;padding: 8px 0;border-bottom: 1px solid var(--el-border-color-lighter)">
                            <el-text style="width: 40px;font-weight: bold" :type="index < 3 ? 'danger' : 'info'">
                                {{ index + 1 }}
                            </el-text>
                            <el-text style="flex: 1;font-family: monospace">{{ item.identifier }}</el-text>
                            <el-tag type="danger" size="small">{{ item.count }} 次</el-tag>
                        </div>
                    </div>
                    <el-empty v-else description="暂无数据" :image-size="60"/>
                </div>

                <!-- 最近限流日志 -->
                <div class="panel-1" style="padding: 20px">
                    <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">最近限流日志</el-text>
                    <div v-if="recentLogs.length > 0">
                        <div v-for="log in recentLogs" :key="log.id"
                             style="padding: 12px;background: var(--el-fill-color-light);border-radius: 6px;margin-bottom: 8px">
                            <div style="display: flex;align-items: center;gap: 8px;margin-bottom: 8px;flex-wrap: wrap">
                                <el-tag :type="getDimensionTagType(log.triggeredDimension)" size="small">
                                    {{ log.triggeredDimension }}
                                </el-tag>
                                <el-tag type="danger" size="small">{{ log.responseAction }}</el-tag>
                                <el-text type="info" size="small">{{ formatTime(log.createdAt) }}</el-text>
                            </div>
                            <div style="display: flex;gap: 16px;flex-wrap: wrap;font-size: 13px">
                                <span><strong>IP:</strong> {{ log.ipAddress || '-' }}</span>
                                <span><strong>Cookie:</strong> {{ log.cookieValue ? log.cookieValue.substring(0, 20) + '...' : '-' }}</span>
                                <span><strong>QQ:</strong> {{ log.qqNumber && log.qqNumber !== 0 ? log.qqNumber : '-' }}</span>
                                <span><strong>路径:</strong> {{ log.requestPath }}</span>
                                <span><strong>方法:</strong> {{ log.requestMethod }}</span>
                            </div>
                        </div>
                    </div>
                    <el-empty v-else description="暂无限流日志" :image-size="60"/>
                </div>
            </div>
        </el-scrollbar>
    </div>
</template>

<style scoped>
.panel-1 {
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-light);
    border-radius: 8px;
}
</style>
