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
let backup = {};
let backupJSON;

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
    backupJSON = JSON.stringify(settings.value);
    backup = JSON.parse(backupJSON);
    editing.value = true;
};

const cancel = () => {
    settings.value = backup;
    customStringsList.value = parseCustomStrings();
    editing.value = false;
};

const finishEditing = () => {
    syncCustomStrings();
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
</style>
