<script setup>
import WebSocketConnector from "@/api/websocket.js";
import {ElMessage, ElMessageBox} from "element-plus";
import {ref, onMounted, watch} from "vue";
import PermissionInfo from "@/auth/PermissionInfo.js";
import UserDataInterface from "@/data/UserDataInterface.js";
import getAvatarUrlOf from "@/utils/Avatar.js";
import router from "@/router/index.js";

// 检查权限
PermissionInfo.requirePageAccess(
    ['answerLimit.view.setting', 'answerLimit.view.count', 'answerLimit.view.whitelist'],
    '无权限访问答题次数限制管理页面'
);

const hasSettingPermission = ref(false);
const hasManageSettingPermission = ref(false);
const hasCountPermission = ref(false);
const hasWhitelistViewPermission = ref(false);
const hasWhitelistManagePermission = ref(false);
const hasRecordManagePermission = ref(false);
const loading = ref(false);
const whitelist = ref([]);
const examRecords = ref([]);
const maxCount = ref(5);
const newWhitelistQQ = ref("");
const newWhitelistReason = ref("");
const whitelistDialogVisible = ref(false);
const searchQQ = ref("");
const searchedQQInfo = ref(null);
const allUsers = ref({});

UserDataInterface.getUsersAsync().then((users) => {
    allUsers.value = users;
});

watch(() => PermissionInfo.permissions.value, () => {
    hasSettingPermission.value = PermissionInfo.hasPermission('answerLimit.view.setting');
    hasManageSettingPermission.value = PermissionInfo.hasPermission('answerLimit.manage.setting');
    hasCountPermission.value = PermissionInfo.hasPermission('answerLimit.view.count');
    hasWhitelistViewPermission.value = PermissionInfo.hasPermission('answerLimit.view.whitelist');
    hasWhitelistManagePermission.value = PermissionInfo.hasPermission('answerLimit.manage.whitelist');
    hasRecordManagePermission.value = PermissionInfo.hasPermission('answerLimit.manage.record');
}, {immediate: true, deep: true});

const getSetting = () => {
    if (!hasSettingPermission.value) return;
    WebSocketConnector.send({
        type: "getAnswerLimitSetting"
    }).then((response) => {
        maxCount.value = response.data.maxCount;
    }, (err) => {
        ElMessage({
            type: "error",
            message: "获取设置失败"
        });
    });
};

const saveSetting = () => {
    if (maxCount.value <= 0) {
        ElMessage({
            type: "warning",
            message: "最大答题次数必须大于0"
        });
        return;
    }
    WebSocketConnector.send({
        type: "saveAnswerLimitSetting",
        data: {
            maxCount: maxCount.value
        }
    }).then(() => {
        ElMessage({
            type: "success",
            message: "保存成功"
        });
    }, (err) => {
        ElMessage({
            type: "error",
            message: err.message || "保存失败"
        });
    });
};

const getWhitelist = () => {
    if (!hasWhitelistViewPermission.value) return;
    loading.value = true;
    WebSocketConnector.send({
        type: "getAnswerLimitWhitelist"
    }).then((response) => {
        whitelist.value = response.data.whitelist || [];
    }, (err) => {
        ElMessage({
            type: "error",
            message: "获取白名单失败"
        });
    }).finally(() => {
        loading.value = false;
    });
};

const searchAnswerCount = () => {
    if (!hasCountPermission.value) return;
    if (!searchQQ.value.trim()) {
        ElMessage({
            type: "warning",
            message: "请输入 QQ 号"
        });
        return;
    }

    if (!/^\d{5,12}$/.test(searchQQ.value.trim())) {
        ElMessage({
            type: "warning",
            message: "QQ 号格式不正确，应为 5-12 位数字"
        });
        return;
    }

    loading.value = true;
    Promise.all([
        WebSocketConnector.send({
            type: "getAnswerCounts",
            data: {
                qq: searchQQ.value.trim()
            }
        }),
        WebSocketConnector.send({
            type: "getAnswerLimitExamRecordsByQQ",
            data: {
                qq: searchQQ.value.trim()
            }
        })
    ]).then(([countResponse, recordsResponse]) => {
        searchedQQInfo.value = countResponse.data;
        examRecords.value = recordsResponse.data.examRecords || [];
    }, (err) => {
        ElMessage({
            type: "error",
            message: err.message || "查询失败"
        });
    }).finally(() => {
        loading.value = false;
    });
};

const deleteExamRecord = (item) => {
    ElMessageBox.confirm(
        `确定要删除该答题记录吗？`,
        "确认删除",
        {
            confirmButtonText: "确定",
            cancelButtonText: "取消",
            type: "warning"
        }
    ).then(() => {
        WebSocketConnector.send({
            type: "deleteExamRecord",
            data: {
                examId: item.id
            }
        }).then(() => {
            ElMessage({
                type: "success",
                message: "删除成功"
            });
            searchAnswerCount();
        }, (err) => {
            ElMessage({
                type: "error",
                message: err.message || "删除失败"
            });
        });
    }).catch(() => {
    });
};

const goToExamRecordDetail = (row) => {
    router.push({name: 'exam-record-detail', params: {id: row.id}});
};

const addToWhitelist = () => {
    if (!newWhitelistQQ.value.trim()) {
        ElMessage({
            type: "warning",
            message: "请输入 QQ 号"
        });
        return;
    }

    if (!/^\d{5,12}$/.test(newWhitelistQQ.value.trim())) {
        ElMessage({
            type: "warning",
            message: "QQ 号格式不正确，应为 5-12 位数字"
        });
        return;
    }

    WebSocketConnector.send({
        type: "addToAnswerLimitWhitelist",
        data: {
            qq: newWhitelistQQ.value.trim(),
            reason: newWhitelistReason.value.trim()
        }
    }).then(() => {
        ElMessage({
            type: "success",
            message: "添加成功"
        });
        newWhitelistQQ.value = "";
        newWhitelistReason.value = "";
        whitelistDialogVisible.value = false;
        getWhitelist();
    }, (err) => {
        ElMessage({
            type: "error",
            message: err.message || "添加失败"
        });
    });
};

const removeFromWhitelist = (item) => {
    ElMessageBox.confirm(
        `确定要将 QQ 号 ${item.qq} 从白名单中移除吗？`,
        "确认移除",
        {
            confirmButtonText: "确定",
            cancelButtonText: "取消",
            type: "warning"
        }
    ).then(() => {
        WebSocketConnector.send({
            type: "removeFromAnswerLimitWhitelist",
            data: {
                qq: item.qq
            }
        }).then(() => {
            ElMessage({
                type: "success",
                message: "移除成功"
            });
            getWhitelist();
        }, (err) => {
            ElMessage({
                type: "error",
                message: err.message || "移除失败"
            });
        });
    }).catch(() => {
    });
};

const formatTime = (timeStr) => {
    if (!timeStr) return "";
    try {
        let date;
        if (Array.isArray(timeStr) && timeStr.length >= 5) {
            const year = timeStr[0];
            const month = timeStr[1] - 1;
            const day = timeStr[2];
            const hours = timeStr[3];
            const minutes = timeStr[4];
            const seconds = timeStr[5] || 0;
            const nanos = timeStr[6] || 0;
            const milliseconds = Math.floor(nanos / 1000000);
            date = new Date(year, month, day, hours, minutes, seconds, milliseconds);
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

const refresh = () => {
    getSetting();
    getWhitelist();
};

onMounted(() => {
    refresh();
});
</script>

<template>
    <div style="display: flex;flex-direction: column;height: 100%">
        <div style="display: flex;flex-direction: row;margin-bottom: 24px;align-items: center;padding: 0 24px;margin-top: 16px">
            <el-text style="font-size: 24px;font-weight: bold">答题次数限制</el-text>
            <el-button style="margin-left: auto" @click="refresh" :loading="loading">
                刷新
            </el-button>
        </div>

        <el-scrollbar v-loading="loading">
            <div style="width: 100%;display: flex;flex-direction: column;gap: 24px;padding: 0 24px 32px 24px;box-sizing: border-box">
                <div v-if="hasSettingPermission" class="panel-1" style="padding: 20px">
                    <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">全局设置</el-text>
                    <el-form label-position="top">
                        <el-form-item label="最大答题次数">
                            <el-input-number v-model="maxCount" :min="1" :max="1000" style="width: 200px"/>
                            <el-text type="info" style="margin-left: 16px">设置用户可答题的最大次数</el-text>
                        </el-form-item>
                        <el-form-item>
                            <el-button v-if="hasManageSettingPermission" type="primary" @click="saveSetting">
                                保存设置
                            </el-button>
                        </el-form-item>
                    </el-form>
                </div>

                <div v-if="hasWhitelistViewPermission" class="panel-1" style="padding: 20px">
                    <div style="display: flex; flex-direction: row; align-items: center; margin-bottom: 16px">
                        <el-text size="large" style="font-weight: bold">白名单管理</el-text>
                        <el-button v-if="hasWhitelistManagePermission" type="primary" style="margin-left: auto" @click="whitelistDialogVisible = true">
                            添加 QQ 号
                        </el-button>
                    </div>
                    
                    <div v-if="whitelist.length > 0">
                        <el-table :data="whitelist" style="width: 100%">
                            <el-table-column prop="qq" label="QQ 号" min-width="150"/>
                            <el-table-column prop="reason" label="原因" min-width="200">
                                <template #default="{row}">
                                    {{ row.reason || '无' }}
                                </template>
                            </el-table-column>
                            <el-table-column prop="createdAt" label="添加时间" min-width="180">
                                <template #default="{row}">
                                    {{ formatTime(row.createdAt) }}
                                </template>
                            </el-table-column>
                            <el-table-column prop="createdByQQ" label="操作人" min-width="280">
                                <template #default="{row}">
                                    <div v-if="row.createdByQQ" class="panel-1 clickable disable-init-animate" 
                                         style="display: flex; flex-direction: row; padding: 4px 8px; margin-right: 4px;"
                                         @click="router.push({name: 'user-detail', params: {id: row.createdByQQ}})">
                                        <el-avatar shape="circle" :size="24" fit="cover" 
                                                   :src="getAvatarUrlOf(row.createdByQQ)"/>
                                        <el-text v-if="allUsers[row.createdByQQ]" 
                                                 style="margin-right: 4px; margin-left: 8px; align-self: center">
                                            {{ allUsers[row.createdByQQ].name }}
                                        </el-text>
                                        <el-text type="info" style="margin-right: 4px; align-self: center">
                                            {{ row.createdByQQ }}
                                        </el-text>
                                    </div>
                                    <el-text v-else>
                                        系统
                                    </el-text>
                                </template>
                            </el-table-column>
                            <el-table-column label="操作" width="100" v-if="hasWhitelistManagePermission">
                                <template #default="{row}">
                                    <el-button type="danger" size="small" @click="removeFromWhitelist(row)">
                                        移除
                                    </el-button>
                                </template>
                            </el-table-column>
                        </el-table>
                    </div>
                    
                    <el-empty v-else description="暂无白名单数据" :image-size="60"/>
                </div>

                <div v-if="hasCountPermission" class="panel-1" style="padding: 20px">
                    <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">答题次数查询</el-text>
                    
                    <el-form label-position="top" style="margin-bottom: 24px">
                        <el-form-item>
                            <el-input v-model="searchQQ" placeholder="请输入 QQ 号进行查询" style="width: 300px" clearable/>
                            <el-button type="primary" style="margin-left: 12px" @click="searchAnswerCount">
                                查询
                            </el-button>
                        </el-form-item>
                    </el-form>

                    <div v-if="searchedQQInfo">
                        <el-descriptions :column="4" border style="margin-bottom: 24px">
                            <el-descriptions-item label="QQ 号">
                                {{ searchedQQInfo.qq }}
                            </el-descriptions-item>
                            <el-descriptions-item label="答题次数">
                                <el-tag :type="searchedQQInfo.count >= maxCount ? 'danger' : 'success'">
                                    {{ searchedQQInfo.count }}
                                </el-tag>
                            </el-descriptions-item>
                            <el-descriptions-item label="首次提交时间">
                                {{ formatTime(searchedQQInfo.firstAnswerTime) || '无' }}
                            </el-descriptions-item>
                            <el-descriptions-item label="最后提交时间">
                                {{ formatTime(searchedQQInfo.lastAnswerTime) || '无' }}
                            </el-descriptions-item>
                        </el-descriptions>

                        <div style="margin-bottom: 16px">
                            <el-text size="large" style="font-weight: bold">答题记录</el-text>
                        </div>
                        
                        <div v-if="examRecords.length > 0">
                            <el-table :data="examRecords" style="width: 100%; cursor: pointer" @row-click="goToExamRecordDetail">
                                <el-table-column prop="id" label="记录 ID" min-width="200"/>
                                <el-table-column label="提交时间" min-width="180">
                                    <template #default="{row}">
                                        {{ formatTime(row.submitTime || row.generateTime) }}
                                    </template>
                                </el-table-column>
                                <el-table-column prop="questionAmount" label="题量" width="100"/>
                                <el-table-column label="成绩" width="120">
                                    <template #default="{row}">
                                        {{ (row.status === 'SUBMITTED' || row.status === 'SCORE_INVALIDED' || row.status === 'SIGN_UP_COMPLETED' || row.status === 'MANUAL_INVALIDED') ? (row.examResult?.score || 0) : '未提交' }}
                                    </template>
                                </el-table-column>
                                <el-table-column prop="status" label="状态" width="120">
                                    <template #default="{row}">
                                        <el-tag :type="(row.status === 'SUBMITTED' || row.status === 'SIGN_UP_COMPLETED' || row.status === 'SCORE_INVALIDED' || row.status === 'MANUAL_INVALIDED') ? 'success' : 'info'">
                                            {{ row.status }}
                                        </el-tag>
                                    </template>
                                </el-table-column>
                                <el-table-column label="操作" width="100" v-if="hasRecordManagePermission">
                                    <template #default="{row}">
                                        <el-button type="danger" size="small" @click.stop="deleteExamRecord(row)">
                                            删除
                                        </el-button>
                                    </template>
                                </el-table-column>
                            </el-table>
                        </div>
                        
                        <el-empty v-else description="暂无答题记录" :image-size="60"/>
                    </div>
                </div>
            </div>
        </el-scrollbar>

        <el-dialog v-model="whitelistDialogVisible" title="添加到白名单" width="400px">
            <el-form label-position="top">
                <el-form-item label="QQ 号">
                    <el-input v-model="newWhitelistQQ" placeholder="请输入 QQ 号"/>
                </el-form-item>
                <el-form-item label="原因（可选）">
                    <el-input v-model="newWhitelistReason" type="textarea" :rows="3" placeholder="请输入添加原因"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="whitelistDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="addToWhitelist">确定</el-button>
            </template>
        </el-dialog>
    </div>
</template>

<style scoped>
.panel-1 {
    background: var(--el-bg-color);
    border: 1px solid var(--el-border-color-light);
    border-radius: 8px;
}

:deep(.el-button--danger) {
    background-color: #f56c6c !important;
    border-color: #f56c6c !important;
    color: #ffffff !important;
}

:deep(.el-button--danger:hover) {
    background-color: #fadddd !important;
    border-color: #fadddd !important;
    color: #f56c6c !important;
}
</style>
