<script setup>
import {ElMessage, ElMessageBox} from "element-plus";
import {ref, onMounted, watch} from "vue";
import PermissionInfo from "@/auth/PermissionInfo.js";
import UserDataInterface from "@/data/UserDataInterface.js";
import getAvatarUrlOf from "@/utils/Avatar.js";
import router from "@/router/index.js";
import WebSocketConnector from "@/api/websocket.js";



const hasManagePermission = ref(false);
const loading = ref(true);
const whitelist = ref([]);
const newTargetId = ref("");
const newReason = ref("");
const dialogVisible = ref(false);
const allUsers = ref({});

UserDataInterface.getUsersAsync().then((users) => {
    allUsers.value = users;
});

watch(() => PermissionInfo.permissions.value, () => {
    hasManagePermission.value = PermissionInfo.hasPermission('thirdPartyApi.manageWhitelist');
}, {immediate: true, deep: true});

const getWhitelist = () => {
    loading.value = true;
    WebSocketConnector.send({
        type: "getWhitelist"
    }).then((response) => {
        whitelist.value = response.data.whitelist || [];
        loading.value = false;
    }, (err) => {
        ElMessage({
            type: "error",
            message: "获取白名单失败"
        });
        loading.value = false;
    });
};

const addToWhitelist = () => {
    if (!newTargetId.value.trim()) {
        ElMessage({
            type: "warning",
            message: "请输入 QQ 号"
        });
        return;
    }

    if (!/^\d{5,12}$/.test(newTargetId.value.trim())) {
        ElMessage({
            type: "warning",
            message: "QQ 号格式不正确，应为 5-12 位数字"
        });
        return;
    }

    WebSocketConnector.send({
        type: "addToWhitelist",
        data: {
            targetId: newTargetId.value.trim(),
            reason: newReason.value.trim()
        }
    }).then(() => {
        ElMessage({
            type: "success",
            message: "添加成功"
        });
        newTargetId.value = "";
        newReason.value = "";
        dialogVisible.value = false;
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
        `确定要将 QQ 号 ${item.targetId} 从白名单中移除吗？`,
        "确认移除",
        {
            confirmButtonText: "确定",
            cancelButtonText: "取消",
            type: "warning"
        }
    ).then(() => {
        WebSocketConnector.send({
            type: "removeFromWhitelist",
            data: {
                targetId: item.targetId
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

onMounted(async () => {
    const hasAccess = await PermissionInfo.requirePageAccess('thirdPartyApi.viewWhitelist', '无权限访问白名单管理页面');
    if (hasAccess) {
        getWhitelist();
    }
});
</script>

<template>
    <div style="display: flex;flex-direction: column;height: 100%">
        <div style="display: flex;flex-direction: row;margin-bottom: 24px;align-items: center;padding: 0 24px;margin-top: 16px">
            <el-text style="font-size: 24px;font-weight: bold">白名单管理</el-text>
            <el-button style="margin-left: auto" @click="getWhitelist" :loading="loading">
                刷新
            </el-button>
            <el-button v-if="hasManagePermission" type="primary" @click="dialogVisible = true">
                添加 QQ 号
            </el-button>
        </div>

        <el-scrollbar v-loading="loading">
            <div style="width: 100%;display: flex;flex-direction: column;gap: 24px;padding: 0 24px 32px 24px;box-sizing: border-box">
                <div class="panel-1" style="padding: 20px">
                    <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">白名单列表</el-text>
                    
                    <div v-if="whitelist.length > 0">
                        <el-table :data="whitelist" style="width: 100%">
                            <el-table-column prop="targetId" label="QQ 号" width="150"/>
                            <el-table-column prop="reason" label="添加原因" min-width="200">
                                <template #default="{row}">
                                    {{ row.reason || '无' }}
                                </template>
                            </el-table-column>
                            <el-table-column prop="createdAt" label="添加时间" width="180">
                                <template #default="{row}">
                                    {{ formatTime(row.createdAt) }}
                                </template>
                            </el-table-column>
                            <el-table-column prop="createdByQQ" label="操作人" width="280">
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
                            <el-table-column label="操作" width="100" v-if="hasManagePermission">
                                <template #default="{row}">
                                    <el-button type="danger" size="small" @click="removeFromWhitelist(row)">
                                        移除
                                    </el-button>
                                </template>
                            </el-table-column>
                        </el-table>
                    </div>
                    
                    <el-empty v-else description="暂无数据" :image-size="60"/>
                </div>
            </div>
        </el-scrollbar>

        <el-dialog v-model="dialogVisible" title="添加到白名单" width="400px">
            <el-form label-position="top">
                <el-form-item label="QQ 号">
                    <el-input v-model="newTargetId" placeholder="请输入 QQ 号"/>
                </el-form-item>
                <el-form-item label="添加原因（可选）">
                    <el-input v-model="newReason" type="textarea" :rows="3" placeholder="请输入添加原因"/>
                </el-form-item>
            </el-form>
            <template #footer>
                <el-button @click="dialogVisible = false">取消</el-button>
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
