<script setup>
import WebSocketConnector from "@/api/websocket.js";
import PermissionInfo from "@/auth/PermissionInfo.js";
import {ElMessage, ElMessageBox} from "element-plus";
import {ref, onMounted, onUnmounted} from "vue";

const loading = ref(false);
const pendingList = ref([]);

const triggerTypeMap = {
    ANSWER_KEY_CHANGE: {label: '答案变更', type: 'danger'},
    MANUAL: {label: '手动触发', type: 'warning'}
};

function getTriggerTypeLabel(type) {
    return triggerTypeMap[type]?.label || type;
}

function getTriggerTypeTagType(type) {
    return triggerTypeMap[type]?.type || 'info';
}

function formatTime(time) {
    if (!time) return '';
    try {
        let date;
        if (Array.isArray(time) && time.length >= 5) {
            date = new Date(time[0], time[1] - 1, time[2], time[3] || 0, time[4] || 0, time[5] || 0, Math.floor((time[6] || 0) / 1000000));
        } else if (typeof time === 'string') {
            return time.replace('T', ' ').substring(0, 19);
        } else {
            return time;
        }
        if (isNaN(date.getTime())) return time;
        const y = date.getFullYear();
        const M = String(date.getMonth() + 1).padStart(2, '0');
        const d = String(date.getDate()).padStart(2, '0');
        const h = String(date.getHours()).padStart(2, '0');
        const m = String(date.getMinutes()).padStart(2, '0');
        const s = String(date.getSeconds()).padStart(2, '0');
        return `${y}-${M}-${d} ${h}:${m}:${s}`;
    } catch (e) {
        return time;
    }
}

function fetchPendingList() {
    loading.value = true;
    WebSocketConnector.send({
        type: 'getPendingRecalculations'
    }).then((response) => {
        pendingList.value = response.data.recalculations;
    }).catch((err) => {
        ElMessage.error('获取待审批列表失败');
    }).finally(() => {
        loading.value = false;
    });
}

function handleApprove(row) {
    WebSocketConnector.send({
        type: 'approveRecalculation',
        data: {logId: row.logId}
    }).then(() => {
        ElMessage.success('已批准重算');
        fetchPendingList();
    }).catch((err) => {
        ElMessage.error(err.data?.message || '批准失败');
    });
}

function handleReject(row) {
    ElMessageBox.confirm(
        '确定驳回此重算请求？驳回后受影响的考试将保持原成绩不变。',
        '确认驳回',
        {confirmButtonText: '确定驳回', cancelButtonText: '取消', type: 'warning'}
    ).then(() => {
        WebSocketConnector.send({
            type: 'rejectRecalculation',
            data: {logId: row.logId}
        }).then(() => {
            ElMessage.success('已驳回重算');
            fetchPendingList();
        }).catch((err) => {
            ElMessage.error(err.data?.message || '驳回失败');
        });
    }).catch(() => {});
}

function onRecalculationBroadcast() {
    fetchPendingList();
}

onMounted(async () => {
    const hasAccess = await PermissionInfo.requirePageAccess(
        'questionVersion.approveRecalculation',
        '无权限访问成绩核算页面'
    );
    if (hasAccess) {
        fetchPendingList();
        WebSocketConnector.registerAction('recalculationAwaitingApproval', onRecalculationBroadcast);
    }
});

onUnmounted(() => {
    const actions = WebSocketConnector.actions?.['recalculationAwaitingApproval'];
    if (actions) {
        const idx = actions.indexOf(onRecalculationBroadcast);
        if (idx !== -1) actions.splice(idx, 1);
    }
});
</script>

<template>
    <div style="display: flex;flex-direction: column;height: 100%">
        <div style="display: flex;flex-direction: row;margin-bottom: 24px;align-items: center;padding: 0 24px;margin-top: 16px">
            <el-text style="font-size: 24px;font-weight: bold">成绩核算</el-text>
            <el-tag v-if="pendingList.length > 0" type="danger" style="margin-left: 12px">
                {{ pendingList.length }} 个待审批
            </el-tag>
            <el-button style="margin-left: auto" @click="fetchPendingList" :loading="loading">
                刷新
            </el-button>
        </div>

        <el-scrollbar v-loading="loading">
            <div style="padding: 0 24px 32px 24px;box-sizing: border-box">
                <el-table :data="pendingList" stripe style="width: 100%"
                          :default-expand-all="false" row-key="logId">
                    <el-table-column type="expand">
                        <template #default="{ row }">
                            <div style="padding: 12px 24px">
                                <el-text type="info" size="small">受影响的考试 ID：</el-text>
                                <div style="margin-top: 8px;display: flex;flex-wrap: wrap;gap: 6px">
                                    <el-tag v-for="examId in row.affectedExamIds" :key="examId"
                                            size="small" type="info">
                                        {{ examId }}
                                    </el-tag>
                                    <el-text v-if="!row.affectedExamIds || row.affectedExamIds.length === 0"
                                             type="info" size="small">
                                        无受影响考试
                                    </el-text>
                                </div>
                            </div>
                        </template>
                    </el-table-column>
                    <el-table-column label="题目内容预览" min-width="200">
                        <template #default="{ row }">
                            <el-text :truncated="true" style="max-width: 100%">
                                {{ row.questionContentPreview || row.questionId }}
                            </el-text>
                        </template>
                    </el-table-column>
                    <el-table-column label="触发类型" width="120" align="center">
                        <template #default="{ row }">
                            <el-tag :type="getTriggerTypeTagType(row.triggerType)" size="small">
                                {{ getTriggerTypeLabel(row.triggerType) }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="触发时间" width="180">
                        <template #default="{ row }">
                            {{ formatTime(row.triggeredAt) }}
                        </template>
                    </el-table-column>
                    <el-table-column label="受影响考试数" width="130" align="center">
                        <template #default="{ row }">
                            <el-text :type="row.affectedExamCount > 0 ? 'danger' : 'info'">
                                {{ row.affectedExamCount }}
                            </el-text>
                        </template>
                    </el-table-column>
                    <el-table-column label="操作" width="180" align="center">
                        <template #default="{ row }">
                            <el-button type="primary" size="small" @click="handleApprove(row)">
                                批准
                            </el-button>
                            <el-button type="danger" size="small" plain @click="handleReject(row)">
                                驳回
                            </el-button>
                        </template>
                    </el-table-column>
                </el-table>

                <el-empty v-if="!loading && pendingList.length === 0"
                          description="暂无待审批的重算请求"/>
            </div>
        </el-scrollbar>
    </div>
</template>

<style scoped>
</style>
