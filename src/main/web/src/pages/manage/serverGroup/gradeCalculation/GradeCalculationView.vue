<script setup>
import WebSocketConnector from "@/api/websocket.js";
import PermissionInfo from "@/auth/PermissionInfo.js";
import {ElMessage, ElMessageBox} from "element-plus";
import {ref, computed, onMounted, onUnmounted} from "vue";

const loading = ref(false);
const historyLoading = ref(false);
const pendingList = ref([]);
const historyList = ref([]);
const historyTotal = ref(0);
const historyPage = ref(1);
const historyPageSize = ref(20);

const canApprove = computed(() => PermissionInfo.hasPermission('questionVersion.approveRecalculation'));
const canViewLog = computed(() => PermissionInfo.hasPermission('questionVersion.viewRecalculationLog'));
const activeTab = ref(canApprove.value ? 'pending' : 'history');

const triggerTypeMap = {
    ANSWER_KEY_CHANGE: {label: '答案变更', type: 'danger'},
    MANUAL: {label: '手动触发', type: 'warning'}
};

const statusMap = {
    AWAITING_APPROVAL: {label: '待审批', type: 'warning'},
    PENDING: {label: '待执行', type: 'info'},
    IN_PROGRESS: {label: '执行中', type: 'primary'},
    COMPLETED: {label: '已完成', type: 'success'},
    FAILED: {label: '失败', type: 'danger'},
    REJECTED: {label: '已驳回', type: 'info'}
};

function getTriggerTypeLabel(type) {
    return triggerTypeMap[type]?.label || type;
}

function getTriggerTypeTagType(type) {
    return triggerTypeMap[type]?.type || 'info';
}

function getStatusLabel(status) {
    return statusMap[status]?.label || status;
}

function getStatusTagType(status) {
    return statusMap[status]?.type || 'info';
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

function fetchHistoryList() {
    historyLoading.value = true;
    WebSocketConnector.send({
        type: 'getScoreRecalculationLogs',
        data: {
            page: historyPage.value - 1,
            size: historyPageSize.value
        }
    }).then((response) => {
        historyList.value = response.data.logs;
        historyTotal.value = response.data.totalElements;
    }).catch((err) => {
        ElMessage.error('获取历史记录失败');
    }).finally(() => {
        historyLoading.value = false;
    });
}

function handlePageChange(page) {
    historyPage.value = page;
    fetchHistoryList();
}

function handleApprove(row) {
    ElMessageBox.confirm(
        `确定批准此重算请求？将对 ${row.affectedExamCount} 个考试重新计算成绩。`,
        '确认批准',
        {confirmButtonText: '确定批准', cancelButtonText: '取消', type: 'warning'}
    ).then(() => {
        WebSocketConnector.send({
            type: 'approveRecalculation',
            data: {logId: row.logId}
        }).then(() => {
            ElMessage.success('已批准重算');
            fetchPendingList();
        }).catch((err) => {
            ElMessage.error(err.data?.message || '批准失败');
        });
    }).catch(() => {});
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
    if (activeTab.value === 'history') {
        fetchHistoryList();
    }
}

function onTabChange(tab) {
    if (tab === 'history' && historyList.value.length === 0) {
        fetchHistoryList();
    }
}

onMounted(async () => {
    const hasAccess = await PermissionInfo.requirePageAccess(
        ['questionVersion.approveRecalculation', 'questionVersion.viewRecalculationLog'],
        '无权限访问成绩核算页面'
    );
    if (hasAccess) {
        if (canApprove.value) {
            fetchPendingList();
            WebSocketConnector.registerAction('recalculationAwaitingApproval', onRecalculationBroadcast);
        }
        if (canViewLog.value) {
            fetchHistoryList();
        }
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
        <div style="display: flex;flex-direction: row;margin-bottom: 16px;align-items: center;padding: 0 24px;margin-top: 16px">
            <el-text style="font-size: 24px;font-weight: bold">成绩核算</el-text>
            <el-tag v-if="canApprove && pendingList.length > 0" type="danger" style="margin-left: 12px">
                {{ pendingList.length }} 个待审批
            </el-tag>
            <el-button style="margin-left: auto" @click="canApprove ? fetchPendingList() : fetchHistoryList()" :loading="loading || historyLoading">
                刷新
            </el-button>
        </div>

        <el-tabs v-model="activeTab" style="padding: 0 24px" @tab-change="onTabChange">
            <!-- 待审批 Tab -->
            <el-tab-pane v-if="canApprove" label="待审批" name="pending">
                <el-table v-loading="loading" :data="pendingList" stripe style="width: 100%"
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
                    <el-table-column label="考试版本" width="100" align="center">
                        <template #default="{ row }">
                            <el-text type="info">{{ row.questionVersionNumber || '-' }}</el-text>
                        </template>
                    </el-table-column>
                    <el-table-column label="触发版本" width="100" align="center">
                        <template #default="{ row }">
                            <el-text type="primary">{{ row.triggerVersionNumber || '-' }}</el-text>
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
                            <div style="display: inline-flex;gap: 8px;align-items: center">
                                <el-button type="primary" size="small" @click="handleApprove(row)">
                                    批准
                                </el-button>
                                <el-button type="danger" size="small" plain @click="handleReject(row)">
                                    驳回
                                </el-button>
                            </div>
                        </template>
                    </el-table-column>
                </el-table>

                <el-empty v-if="!loading && pendingList.length === 0"
                          description="暂无待审批的重算请求"/>
            </el-tab-pane>

            <!-- 历史记录 Tab -->
            <el-tab-pane v-if="canViewLog" label="历史记录" name="history">
                <el-table v-loading="historyLoading" :data="historyList" stripe style="width: 100%"
                          row-key="id">
                    <el-table-column type="expand">
                        <template #default="{ row }">
                            <div style="padding: 12px 24px">
                                <template v-if="row.status === 'COMPLETED'">
                                    <el-text type="info" size="small">
                                        受影响 {{ row.affectedExamCount }} 个考试，
                                        成绩变更 {{ row.scoreChangedExamCount }} 个
                                    </el-text>
                                    <div v-if="row.details && row.details.length > 0" style="margin-top: 8px">
                                        <el-table :data="row.details.filter(d => d.scoreChanged)" size="small" border>
                                            <el-table-column label="考试 ID" prop="examDataId" min-width="200"/>
                                            <el-table-column label="原分数" width="100" align="center">
                                                <template #default="{ row: detail }">{{ detail.oldScore }}</template>
                                            </el-table-column>
                                            <el-table-column label="新分数" width="100" align="center">
                                                <template #default="{ row: detail }">{{ detail.newScore }}</template>
                                            </el-table-column>
                                            <el-table-column label="原等级" width="100" align="center">
                                                <template #default="{ row: detail }">{{ detail.oldLevel || '-' }}</template>
                                            </el-table-column>
                                            <el-table-column label="新等级" width="100" align="center">
                                                <template #default="{ row: detail }">{{ detail.newLevel || '-' }}</template>
                                            </el-table-column>
                                        </el-table>
                                    </div>
                                </template>
                                <template v-else-if="row.status === 'FAILED'">
                                    <el-text type="danger" size="small">错误信息：{{ row.errorMessage }}</el-text>
                                </template>
                                <template v-else-if="row.status === 'REJECTED'">
                                    <el-text type="info" size="small">
                                        驳回人：{{ row.rejectedByQq || '-' }}
                                        <template v-if="row.rejectedAt">
                                            &nbsp;|&nbsp;驳回时间：{{ formatTime(row.rejectedAt) }}
                                        </template>
                                    </el-text>
                                </template>
                                <template v-else-if="row.approvedByQq">
                                    <el-text type="info" size="small">
                                        审批人：{{ row.approvedByQq }}
                                        <template v-if="row.approvedAt">
                                            &nbsp;|&nbsp;审批时间：{{ formatTime(row.approvedAt) }}
                                        </template>
                                    </el-text>
                                </template>
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
                    <el-table-column label="考试版本" width="100" align="center">
                        <template #default="{ row }">
                            <el-text type="info">{{ row.questionVersionNumber || '-' }}</el-text>
                        </template>
                    </el-table-column>
                    <el-table-column label="触发版本" width="100" align="center">
                        <template #default="{ row }">
                            <el-text type="primary">{{ row.triggerVersionNumber || '-' }}</el-text>
                        </template>
                    </el-table-column>
                    <el-table-column label="触发类型" width="120" align="center">
                        <template #default="{ row }">
                            <el-tag :type="getTriggerTypeTagType(row.triggerType)" size="small">
                                {{ getTriggerTypeLabel(row.triggerType) }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="状态" width="100" align="center">
                        <template #default="{ row }">
                            <el-tag :type="getStatusTagType(row.status)" size="small">
                                {{ getStatusLabel(row.status) }}
                            </el-tag>
                        </template>
                    </el-table-column>
                    <el-table-column label="触发时间" width="180">
                        <template #default="{ row }">
                            {{ formatTime(row.triggeredAt) }}
                        </template>
                    </el-table-column>
                    <el-table-column label="完成时间" width="180">
                        <template #default="{ row }">
                            {{ formatTime(row.completedAt) }}
                        </template>
                    </el-table-column>
                    <el-table-column label="受影响/变更" width="120" align="center">
                        <template #default="{ row }">
                            <el-text v-if="row.status === 'COMPLETED'">
                                {{ row.affectedExamCount }}/<el-text type="danger">{{ row.scoreChangedExamCount }}</el-text>
                            </el-text>
                            <el-text v-else type="info">{{ row.affectedExamCount }}</el-text>
                        </template>
                    </el-table-column>
                </el-table>

                <div v-if="historyTotal > historyPageSize" style="margin-top: 16px;display: flex;justify-content: center">
                    <el-pagination
                        v-model:current-page="historyPage"
                        :page-size="historyPageSize"
                        :total="historyTotal"
                        layout="prev, pager, next"
                        @current-change="handlePageChange"
                    />
                </div>

                <el-empty v-if="!historyLoading && historyList.length === 0"
                          description="暂无历史核算记录"/>
            </el-tab-pane>
        </el-tabs>
    </div>
</template>

<style scoped>
</style>
