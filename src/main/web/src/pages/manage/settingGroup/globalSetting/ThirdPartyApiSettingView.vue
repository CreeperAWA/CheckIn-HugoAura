<script setup>
import {ElMessage} from "element-plus";
import {ref, onMounted, watch, nextTick} from "vue";
import PermissionInfo from "@/auth/PermissionInfo.js";
import WebSocketConnector from "@/api/websocket.js";
import {VueDraggable} from "vue-draggable-plus";
import HarmonyOSIcon_Remove from "@/components/icons/HarmonyOSIcon_Remove.vue";
import HarmonyOSIcon_Handle from "@/components/icons/HarmonyOSIcon_Handle.vue";
import HarmonyOSIcon_Plus from "@/components/icons/HarmonyOSIcon_Plus.vue";
import {uuidv7} from "uuidv7";

if (!PermissionInfo.hasPermission('thirdPartyApi.view.setting')) {
    ElMessage({
        type: "error",
        message: "无权限访问第三方 API 设置页面"
    });
}

const hasManagePermission = ref(false);
const loading = ref(true);
const saving = ref(false);
const editing = ref(false);
const settings = ref({});
const customStringsDragging = ref(false);
const customStringsList = ref([]);
const sidTokensList = ref([]);
let backup = {};
let backupJSON;

const parseSidTokens = () => {
    try {
        const val = settings.value['robot.sidTokens'];
        if (typeof val === 'string') {
            return JSON.parse(val || '[]');
        }
        if (Array.isArray(val)) {
            return val;
        }
        return [];
    } catch {
        return [];
    }
};

const syncSidTokens = () => {
    settings.value['robot.sidTokens'] = JSON.stringify(sidTokensList.value);
};

const addSidToken = () => {
    sidTokensList.value.push({ id: uuidv7(), sid: "", token: "" });
};

const removeSidToken = (index) => {
    sidTokensList.value.splice(index, 1);
};

const generateTokenForSid = (item) => {
    if (!item.sid || item.sid.trim() === '') {
        ElMessage({
            type: "error",
            message: "请先输入 SID"
        });
        return;
    }
    const sid = item.sid.trim();
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(sid)) {
        ElMessage({
            type: "error",
            message: "SID 格式不正确，应为 UUID 格式"
        });
        return;
    }
    WebSocketConnector.send({
        type: "generateThirdPartyApiSidToken",
        data: { sid }
    }).then((response) => {
        item.token = response.data.token;
        syncSidTokens();
        ElMessage({
            type: "success",
            message: "Token 生成成功"
        });
    }, (err) => {
        ElMessage({
            type: "error",
            message: err.message || "Token 生成失败"
        });
    });
};

const generatingAll = ref(false);

const generateAllTokens = () => {
    const itemsWithoutToken = sidTokensList.value.filter(item => item.sid && item.sid.trim() !== '' && (!item.token || item.token.trim() === ''));
    if (itemsWithoutToken.length === 0) {
        ElMessage({
            type: "warning",
            message: "所有 SID 都已生成 Token"
        });
        return;
    }
    generatingAll.value = true;
    let completed = 0;
    let failed = 0;
    const promises = itemsWithoutToken.map(item => {
        const sid = item.sid.trim();
        return WebSocketConnector.send({
            type: "generateThirdPartyApiSidToken",
            data: { sid }
        }).then((response) => {
            item.token = response.data.token;
            completed++;
        }, (err) => {
            failed++;
        });
    });
    Promise.all(promises).then(() => {
        syncSidTokens();
        generatingAll.value = false;
        ElMessage({
            type: "success",
            message: `批量生成完成：成功 ${completed} 个，失败 ${failed} 个`
        });
    });
};

const copyToken = (token) => {
    navigator.clipboard.writeText(token).then(() => {
        ElMessage({
            type: "success",
            message: "Token 已复制到剪贴板"
        });
    }, () => {
        ElMessage({
            type: "error",
            message: "复制失败"
        });
    });
};

const parseCustomStrings = () => {
    try {
        const val = settings.value['qqVerify.customStrings'];
        let strings;
        if (typeof val === 'string') {
            strings = JSON.parse(val || '[]');
        } else if (Array.isArray(val)) {
            strings = val;
        } else {
            strings = [];
        }
        if (strings.length > 0 && typeof strings[0] === 'string') {
            return strings.map(s => ({ id: uuidv7(), content: s }));
        }
        if (strings.length > 0 && typeof strings[0] === 'object' && strings[0].id) {
            return strings;
        }
        return [];
    } catch {
        return [];
    }
};

const syncCustomStrings = () => {
    const strings = customStringsList.value.map(item => item.content);
    settings.value['qqVerify.customStrings'] = JSON.stringify(strings);
};

const addCustomString = () => {
    customStringsList.value.push({ id: uuidv7(), content: "" });
};

const timeoutOptionValues = ["fail", "allow"];
const timeoutOptionNames = {
    fail: "验证失败（不生成试题）",
    allow: "允许答题"
};

const cannotVerifyOptionValues = ["fail", "skip"];
const cannotVerifyOptionNames = {
    fail: "验证失败（不生成试题）",
    skip: "跳过验证（生成试题）"
};

const removeCustomString = (index) => {
    customStringsList.value.splice(index, 1);
};

const onStartDrag = () => {
    customStringsDragging.value = true;
};

const onEndDrag = () => {
    nextTick(() => {
        customStringsDragging.value = false;
    });
};

const startEditing = () => {
    syncCustomStrings();
    syncSidTokens();
    backupJSON = JSON.stringify(settings.value);
    backup = JSON.parse(backupJSON);
    editing.value = true;
};

const cancel = () => {
    settings.value = backup;
    customStringsList.value = parseCustomStrings();
    sidTokensList.value = parseSidTokens();
    editing.value = false;
};

const finishEditing = () => {
    syncCustomStrings();
    syncSidTokens();
    editing.value = false;
    if (backupJSON !== JSON.stringify(settings.value)) {
        saving.value = true;
        WebSocketConnector.send({
            type: "saveThirdPartyApiSetting",
            data: {
                data: settings.value
            }
        }).then(() => {
            ElMessage({
                type: "success",
                message: "保存成功"
            });
            saving.value = false;
        }, (err) => {
            ElMessage({
                type: "error",
                message: err.message || "保存失败"
            });
            saving.value = false;
        });
    }
};

watch(() => PermissionInfo.permissions.value, () => {
    hasManagePermission.value = PermissionInfo.hasPermission('thirdPartyApi.manage.setting');
}, {immediate: true, deep: true});

const getSettings = () => {
    loading.value = true;
    WebSocketConnector.send({
        type: "getThirdPartyApiSetting"
    }).then((response) => {
        settings.value = response.data.data || {};
        customStringsList.value = parseCustomStrings();
        sidTokensList.value = parseSidTokens();
        loading.value = false;
    }, (err) => {
        ElMessage({
            type: "error",
            message: "获取设置失败"
        });
        loading.value = false;
    });
};

onMounted(() => {
    getSettings();
});
</script>

<template>
    <div style="display: flex;flex-direction: column;height: 100%">
        <div style="display: flex;flex-direction: row;flex-wrap: wrap;margin-bottom: 24px;align-items: center;padding: 0 24px;margin-top: 16px">
            <el-text style="font-size: 24px;font-weight: bold;margin-right: 32px">第三方 API 设置</el-text>
            <div v-if="hasManagePermission" style="display: flex;">
                <transition-group name="blur-scale">
                    <el-button-group key="button-group" style="margin: 2px 24px 2px 0;">
                        <transition-group name="blur-scale">
                            <el-button class="disable-init-animate" style="margin-right: 4px;" :disabled="loading || saving" @click="editing ? finishEditing() : startEditing()" key="edit">
                                {{ editing ? '完成' : '编辑' }}
                            </el-button>
                            <el-button class="disable-init-animate" v-if="editing" @click="cancel" key="cancel">
                                取消
                            </el-button>
                        </transition-group>
                    </el-button-group>
                </transition-group>
            </div>
        </div>

        <el-scrollbar v-loading="loading">
            <div style="width: 100%;display: flex;flex-direction: column;gap: 24px;padding: 0 24px 32px 24px;box-sizing: border-box">
                <div class="panel-1" style="padding: 20px">
                    <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">QQ验证设置</el-text>
                    <el-form label-position="top">
                        <el-form-item label="启用 QQ 号验证">
                            <el-switch v-model="settings['qqVerify.enabled']" :disabled="!hasManagePermission || !editing"/>
                        </el-form-item>
                        <template v-if="settings['qqVerify.enabled']">
                            <el-form-item label="验证有效期（天）">
                                <el-input-number v-model="settings['qqVerify.validDays']" :min="1" :max="365" :disabled="!hasManagePermission || !editing"/>
                            </el-form-item>
                            <el-form-item label="超时处理方式">
                                <transition name="blur-scale" mode="out-in">
                                    <el-segmented :options="timeoutOptionValues" v-if="editing" v-model="settings['qqVerify.timeoutAction']">
                                        <template #default="{item}">
                                            <el-text>{{ timeoutOptionNames[item] }}</el-text>
                                        </template>
                                    </el-segmented>
                                    <span v-else style="border: 1px solid var(--el-border-color);border-radius: 8px;padding: 4px 12px;display: inline-block;">
                                        <el-text style="text-wrap: wrap;word-break: break-all">{{ timeoutOptionNames[settings['qqVerify.timeoutAction']] || timeoutOptionNames.fail }}</el-text>
                                    </span>
                                </transition>
                            </el-form-item>
                            <el-form-item label="无法验证处理方式">
                                <transition name="blur-scale" mode="out-in">
                                    <el-segmented :options="cannotVerifyOptionValues" v-if="editing" v-model="settings['qqVerify.cannotVerifyAction']">
                                        <template #default="{item}">
                                            <el-text>{{ cannotVerifyOptionNames[item] }}</el-text>
                                        </template>
                                    </el-segmented>
                                    <span v-else style="border: 1px solid var(--el-border-color);border-radius: 8px;padding: 4px 12px;display: inline-block;">
                                        <el-text style="text-wrap: wrap;word-break: break-all">{{ cannotVerifyOptionNames[settings['qqVerify.cannotVerifyAction']] || cannotVerifyOptionNames.skip }}</el-text>
                                    </span>
                                </transition>
                            </el-form-item>
                            <el-form-item label="自定义验证字符串列表">
                                <div class="custom-strings-panel">
                                    <el-button v-if="editing" @click="addCustomString" size="large" text :disabled="!hasManagePermission || !editing">
                                        <HarmonyOSIcon_Plus/>
                                        <el-text>添加字符串</el-text>
                                    </el-button>
                                    <template v-if="customStringsList.length > 0">
                                        <VueDraggable
                                            ref="draggable"
                                            v-model="customStringsList"
                                            :animation="150"
                                            :disabled="!hasManagePermission || !editing"
                                            ghostClass="ghost"
                                            handle=".handle"
                                            @start="onStartDrag"
                                            @end="onEndDrag"
                                        >
                                            <transition-group :name="customStringsDragging ? null : 'drag'">
                                                <div class="custom-string-item" v-for="(str, index) of customStringsList" :key="str.id">
                                                    <div class="handle" :style="{ cursor: editing ? 'grab' : 'not-allowed' }">
                                                        <HarmonyOSIcon_Handle/>
                                                    </div>
                                                    <el-input class="disable-init-animate" type="text" size="large" placeholder="请输入验证字符串"
                                                              v-model="str.content" :disabled="!hasManagePermission || !editing"/>
                                                    <transition name="delete-string-button">
                                                        <el-button class="remove-string-button"
                                                                   v-show="editing && customStringsList.length > 0" text
                                                                   @click="removeCustomString(index)" :disabled="!hasManagePermission || !editing">
                                                            <HarmonyOSIcon_Remove/>
                                                        </el-button>
                                                    </transition>
                                                </div>
                                            </transition-group>
                                        </VueDraggable>
                                    </template>
                                    <el-empty v-else description="No data" style="align-self: stretch"/>
                                </div>
                            </el-form-item>
                        </template>
                    </el-form>
                </div>

                <div class="panel-1" style="padding: 20px">
                    <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">通知设置</el-text>
                    <div class="notification-list">
                        <div class="notification-item">
                            <div class="notification-content">
                                <span class="notification-text">同一 QQ 号</span>
                                <el-input-number v-model="settings['notification.submitFrequency.timeWindow']" :min="1" :max="60" :controls="false" :disabled="!hasManagePermission || !editing || !settings['notification.submitFrequency.enabled']" class="notification-input"/>
                                <span class="notification-text">分钟内提交</span>
                                <el-input-number v-model="settings['notification.submitFrequency.threshold']" :min="1" :max="100" :controls="false" :disabled="!hasManagePermission || !editing || !settings['notification.submitFrequency.enabled']" class="notification-input"/>
                                <span class="notification-text">次试题</span>
                            </div>
                            <el-switch v-model="settings['notification.submitFrequency.enabled']" :disabled="!hasManagePermission || !editing" class="notification-switch"/>
                        </div>

                        <div class="notification-item">
                            <div class="notification-content">
                                <span class="notification-text">用户多次尝试登录后台失败达</span>
                                <el-input-number v-model="settings['notification.loginFailure.threshold']" :min="1" :max="10" :controls="false" :disabled="!hasManagePermission || !editing || !settings['notification.loginFailure.enabled']" class="notification-input"/>
                                <span class="notification-text">次</span>
                            </div>
                            <el-switch v-model="settings['notification.loginFailure.enabled']" :disabled="!hasManagePermission || !editing" class="notification-switch"/>
                        </div>

                        <div class="notification-item">
                            <div class="notification-content">
                                <span class="notification-text">用户登录后台成功</span>
                            </div>
                            <el-switch v-model="settings['notification.loginSuccess.enabled']" :disabled="!hasManagePermission || !editing" class="notification-switch"/>
                        </div>

                        <div class="notification-item">
                            <div class="notification-content">
                                <span class="notification-text">用户生成试题后</span>
                                <el-input-number v-model="settings['notification.quickSubmit.threshold']" :min="0" :max="60" :controls="false" :disabled="!hasManagePermission || !editing || !settings['notification.quickSubmit.enabled']" class="notification-input"/>
                                <span class="notification-text">分钟内提交</span>
                            </div>
                            <el-switch v-model="settings['notification.quickSubmit.enabled']" :disabled="!hasManagePermission || !editing" class="notification-switch"/>
                        </div>

                        <div class="notification-item">
                            <div class="notification-content">
                                <span class="notification-text">用户提交试卷</span>
                            </div>
                            <el-switch v-model="settings['notification.paperSubmit.enabled']" :disabled="!hasManagePermission || !editing" class="notification-switch"/>
                        </div>

                        <div class="notification-item">
                            <div class="notification-content">
                                <span class="notification-text">用户开始考试</span>
                            </div>
                            <el-switch v-model="settings['notification.examStart.enabled']" :disabled="!hasManagePermission || !editing" class="notification-switch"/>
                        </div>
                    </div>
                </div>

                <div class="panel-1" style="padding: 20px">
                    <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">Token 管理</el-text>
                    <el-text type="info" style="margin-bottom: 16px;display: block;font-size: 12px">
                        每个 SID 对应一个 Token，用于 WebSocket 机器人连接认证。SID 为机器人唯一标识（UUID 格式）。
                    </el-text>
                    <div class="sid-token-list">
                        <div class="sid-token-actions-bar">
                            <el-button v-if="editing" @click="addSidToken" size="large" text :disabled="!hasManagePermission || !editing">
                                <HarmonyOSIcon_Plus/>
                                <el-text>添加 Token</el-text>
                            </el-button>
                            <el-button v-if="editing" @click="generateAllTokens" size="large" text :disabled="!hasManagePermission || !editing" :loading="generatingAll">
                                <el-text>批量生成（无 Token 的 SID）</el-text>
                            </el-button>
                        </div>
                        <template v-if="sidTokensList.length > 0">
                            <div class="sid-token-item" v-for="(item, index) of sidTokensList" :key="item.id">
                                <div class="sid-token-row">
                                    <div class="sid-token-field">
                                        <span class="sid-token-label">SID</span>
                                        <el-input class="disable-init-animate" type="text" size="large" placeholder="请输入 SID (UUID 格式)"
                                                  v-model="item.sid" :disabled="!hasManagePermission || !editing"/>
                                    </div>
                                    <div class="sid-token-field sid-token-actions">
                                        <el-button size="large" @click="generateTokenForSid(item)" :disabled="!hasManagePermission || !editing">
                                            生成 Token
                                        </el-button>
                                        <transition name="delete-token-button">
                                            <el-button class="remove-token-button"
                                                       v-show="editing && sidTokensList.length > 0" text
                                                       @click="removeSidToken(index)" :disabled="!hasManagePermission || !editing">
                                                <HarmonyOSIcon_Remove/>
                                            </el-button>
                                        </transition>
                                    </div>
                                </div>
                                <div class="sid-token-row" v-if="item.token">
                                    <div class="sid-token-field sid-token-field-full">
                                        <span class="sid-token-label">Token</span>
                                        <div class="token-display">
                                            <el-input class="disable-init-animate" type="text" size="large" readonly
                                                      v-model="item.token" :disabled="!hasManagePermission || !editing"/>
                                            <el-button size="large" @click="copyToken(item.token)" :disabled="!hasManagePermission || !editing">
                                                复制
                                            </el-button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </template>
                        <el-empty v-else description="No data" style="align-self: stretch"/>
                    </div>
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

.notification-list {
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.notification-item {
    display: flex;
    flex-direction: row;
    align-items: center;
    justify-content: space-between;
    padding: 12px 16px;
    background: var(--el-fill-color-light);
    border-radius: 6px;
}

.notification-content {
    display: flex;
    flex-direction: row;
    align-items: center;
    flex-wrap: wrap;
    gap: 8px;
    flex: 1;
}

.notification-text {
    font-size: 14px;
    color: var(--el-text-color-regular);
}

.notification-input {
    width: 100px;
}

.notification-switch {
    margin-left: 16px;
    flex-shrink: 0;
}

.custom-strings-panel {
    display: flex;
    flex-direction: column;
    width: 100%;
    border: 1px solid var(--el-border-color-light);
    border-radius: 6px;
    padding: 8px;
    background: var(--el-fill-color-blank);
    box-sizing: border-box;
}

.custom-string-item {
    display: flex;
    flex-direction: row;
    align-items: center;
    width: 100%;
    margin-bottom: 4px;
    overflow: hidden;
}

.custom-string-item .handle {
    width: 30px;
    aspect-ratio: 1;
    padding: 0;
    display: flex;
    align-items: center;
    justify-items: center;
    align-content: center;
    justify-content: center;
    flex-shrink: 0;
}

.custom-string-item .el-input {
    flex: 1;
    min-width: 0;
}

.remove-string-button {
    overflow: hidden;
    padding: 0 !important;
    width: 45px;
    height: 100%;
    margin: 0;
    flex-shrink: 0;
}

.ghost {
    opacity: 0.5;
    background: var(--el-fill-color-light);
}

.delete-string-button-enter-active,
.delete-string-button-leave-active {
    transition: 300ms var(--ease-in-out-quint);
}

.delete-string-button-enter-from,
.delete-string-button-leave-to {
    opacity: 0;
    width: 0;
    max-width: 0;
    transform: scale(0.8);
}

.drag-move, .drag-enter-active, .drag-leave-active {
    transition: 0.4s;
    overflow: hidden;
}

.drag-enter-from, .drag-leave-to {
    opacity: 0;
    height: 0;
    margin-bottom: 0;
}

.sid-token-list {
    display: flex;
    flex-direction: column;
    width: 100%;
    border: 1px solid var(--el-border-color-light);
    border-radius: 6px;
    padding: 8px;
    background: var(--el-fill-color-blank);
    box-sizing: border-box;
}

.sid-token-actions-bar {
    display: flex;
    flex-direction: row;
    gap: 4px;
    margin-bottom: 8px;
}

.sid-token-item {
    display: flex;
    flex-direction: column;
    width: 100%;
    margin-bottom: 12px;
    padding: 12px;
    background: var(--el-fill-color-light);
    border-radius: 6px;
    gap: 8px;
}

.sid-token-row {
    display: flex;
    flex-direction: row;
    align-items: center;
    width: 100%;
    gap: 12px;
}

.sid-token-field {
    display: flex;
    flex-direction: column;
    gap: 4px;
    flex: 1;
}

.sid-token-field-full {
    flex: 1;
    min-width: 0;
}

.sid-token-actions {
    flex-shrink: 0;
    align-items: flex-end;
}

.sid-token-label {
    font-size: 12px;
    color: var(--el-text-color-secondary);
}

.token-display {
    display: flex;
    flex-direction: row;
    align-items: center;
    gap: 8px;
    width: 100%;
}

.token-display .el-input {
    flex: 1;
    min-width: 0;
}

.remove-token-button {
    overflow: hidden;
    padding: 0 !important;
    width: 45px;
    height: 100%;
    margin: 0;
    flex-shrink: 0;
}

.delete-token-button-enter-active,
.delete-token-button-leave-active {
    transition: 300ms var(--ease-in-out-quint);
}

.delete-token-button-enter-from,
.delete-token-button-leave-to {
    opacity: 0;
    width: 0;
    max-width: 0;
    transform: scale(0.8);
}
</style>
