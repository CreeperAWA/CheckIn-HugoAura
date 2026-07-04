<script setup>
import {ElMessage, ElMessageBox} from "element-plus";
import {ref, onMounted, watch} from "vue";
import PermissionInfo from "@/auth/PermissionInfo.js";
import WebSocketConnector from "@/api/websocket.js";
import HarmonyOSIcon_Remove from "@/components/icons/HarmonyOSIcon_Remove.vue";
import HarmonyOSIcon_Plus from "@/components/icons/HarmonyOSIcon_Plus.vue";
import {uuidv7} from "uuidv7";
import UserDataInterface from "@/data/UserDataInterface.js";
import getAvatarUrlOf from "@/utils/Avatar.js";
import router from "@/router/index.js";



const hasManagePermission = ref(false);
const loading = ref(true);
const saving = ref(false);
const editing = ref(false);
const settings = ref({});
const customStringsDragging = ref(false);
const customStringsList = ref([]);
const thirdPartyApiTokenItems = ref([]);
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

const showCreateNewTokenDialog = ref(false);
const newTokenSid = ref("");
const newTokenDescription = ref("");
const onClose = () => {
    showCreateNewTokenDialog.value = false;
    newTokenSid.value = "";
    newTokenDescription.value = "";
}
const createTokenButtonOption = ref([{
    content: "确定",
    type: "primary",
    onclick: () => {
        let sid = newTokenSid.value ? newTokenSid.value.trim() : '';
        if (sid === '') {
            sid = uuidv7();
        } else if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(sid)) {
            ElMessage({type: 'error', message: 'SID 格式不正确，应为 UUID 格式'});
            return;
        }
        if (!(thirdPartyApiTokenItems.value instanceof Array)) {
            thirdPartyApiTokenItems.value = [];
        }
        const token = {
            id: uuidv7(),
            sid: sid,
            token: null,
            description: newTokenDescription.value,
            generateTime: null,
            generateByUserQQ: UserDataInterface.getCurrentUser().value.qq
        };
        thirdPartyApiTokenItems.value.push(token);
        settings.value.createdThirdPartyApiTokens = settings.value.createdThirdPartyApiTokens || [];
        settings.value.createdThirdPartyApiTokens.push(token);
        onClose();
    }
}, {
    content: "取消",
    type: "info",
    onclick: onClose
}]);
const createNewToken = () => {
    showCreateNewTokenDialog.value = true;
}

const allUsers = ref({});
UserDataInterface.getUsersAsync().then((users) => {
    allUsers.value = users;
});

const deleteToken = (index) => {
    if (Boolean(thirdPartyApiTokenItems.value[index].token)) {
        ElMessageBox.confirm(
                "该 Token 将无法再被使用",
                "确定删除 Token",
                {
                    showClose: false,
                    draggable: true,
                    confirmButtonText: "确定",
                    cancelButtonText: "取消",
                    type: "warning",
                }
        ).then(() => {
            console.log(thirdPartyApiTokenItems.value[index]);
            settings.value.deletedThirdPartyApiTokenIds = settings.value.deletedThirdPartyApiTokenIds || [];
            settings.value.deletedThirdPartyApiTokenIds.push(thirdPartyApiTokenItems.value[index].id);
            thirdPartyApiTokenItems.value.splice(index, 1);
        }, () => {
        });
    } else {
        thirdPartyApiTokenItems.value.splice(index, 1);
    }
}

const startEditing = () => {
    syncCustomStrings();
    backupJSON = JSON.stringify(settings.value);
    backup = JSON.parse(backupJSON);
    settings.value.createdThirdPartyApiTokens = [];
    settings.value.deletedThirdPartyApiTokenIds = [];
    editing.value = true;
};

const cancel = () => {
    settings.value = backup;
    customStringsList.value = parseCustomStrings();
    thirdPartyApiTokenItems.value = backup.thirdPartyApiTokenItems || [];
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
        }).then((response) => {
            if (response.data.currentTokens) {
                thirdPartyApiTokenItems.value = response.data.currentTokens;
            }
            settings.value.deletedThirdPartyApiTokenIds = [];
            settings.value.createdThirdPartyApiTokens = [];
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
        if (settings.value['qqVerify.validDays'] === undefined || settings.value['qqVerify.validDays'] === null) {
            settings.value['qqVerify.validDays'] = 3;
        }
        customStringsList.value = parseCustomStrings();
        thirdPartyApiTokenItems.value = settings.value.thirdPartyApiTokenItems || [];
        loading.value = false;
    }, (err) => {
        ElMessage({
            type: "error",
            message: "获取设置失败"
        });
        loading.value = false;
    });
};

onMounted(async () => {
    const hasAccess = await PermissionInfo.requirePageAccess('thirdPartyApi.view.setting', '无权限访问第三方 API 设置页面');
    if (hasAccess) {
        getSettings();
    }
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
                    <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">QQ 验证设置</el-text>
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
                                        <div class="custom-string-item" v-for="(str, index) of customStringsList" :key="str.id">
                                            <el-input class="disable-init-animate" type="text" size="large" placeholder="请输入验证字符串"
                                                      v-model="str.content" :disabled="!hasManagePermission || !editing"/>
                                            <el-button class="remove-string-button"
                                                       v-show="editing && customStringsList.length > 0" text
                                                       @click="removeCustomString(index)" :disabled="!hasManagePermission || !editing">
                                                <HarmonyOSIcon_Remove/>
                                            </el-button>
                                        </div>
                                    </template>
                                    <el-empty v-else description="No data" style="align-self: stretch"/>
                                </div>
                            </el-form-item>
                            <el-form-item label="验证引导提示">
                                <el-input
                                    v-model="settings['qqVerify.guideMessage']"
                                    type="textarea"
                                    :rows="4"
                                    placeholder="验证页面显示的引导提示内容，支持换行"
                                    :disabled="!hasManagePermission || !editing"
                                />
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
                                <span class="notification-text">用户登录后台失败</span>
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
                    <div style="display: flex;flex-direction: row;flex-wrap: wrap;align-items: center;margin-bottom: 8px">
                        <el-text size="large" style="font-weight: bold;align-self: center;margin-right: 16px">第三方 API Tokens</el-text>
                        <transition name="blur-scale">
                            <el-button class="disable-init-animate" link @click="createNewToken" v-if="editing">
                                <HarmonyOSIcon_Plus style="margin-right: 4px;"/>
                                新建 Token
                            </el-button>
                        </transition>
                    </div>
                    <transition name="blur-scale" mode="out-in">
                        <div style="display: flex;flex-direction: column"
                             v-if="thirdPartyApiTokenItems && thirdPartyApiTokenItems.length > 0">
                            <transition-group name="smooth-height">
                                <div class="smooth-height-base"
                                     v-for="(tokenItem,index) of thirdPartyApiTokenItems"
                                     :key="tokenItem.id">
                                    <div>
                                        <div style="display:flex;margin-bottom: 8px;flex-direction: column">
                                            <div class="panel-1 disable-init-animate"
                                                 style="padding: 4px 8px;display: flex;align-items: center;margin-bottom: 4px">
                                                <el-text type="info" style="margin-right: 8px;">SID</el-text>
                                                <transition name="blur-scale" mode="out-in">
                                                    <el-text v-if="tokenItem.generateTime" type="primary"
                                                             style="margin-left: 8px;margin-right: 8px;word-break: break-all">
                                                        {{ tokenItem.sid }}
                                                    </el-text>
                                                    <el-text v-else type="info">{{ tokenItem.sid }}</el-text>
                                                </transition>
                                            </div>
                                            <div class="panel-1 disable-init-animate"
                                                 style="padding: 4px 8px;display: flex;align-items: center;margin-bottom: 4px">
                                                <el-text type="info" style="margin-right: 8px;">Token</el-text>
                                                <transition name="blur-scale" mode="out-in">
                                                    <el-text v-if="tokenItem.generateTime" type="primary"
                                                             style="margin-left: 8px;margin-right: 8px;word-break: break-all">
                                                        {{ tokenItem.token }}
                                                    </el-text>
                                                    <el-text v-else type="info">等待保存后生成</el-text>
                                                </transition>
                                            </div>
                                            <div style="display: flex;flex-wrap: wrap">
                                                <div class="panel-1 disable-init-animate"
                                                     style="min-width: 200px;padding: 4px 8px;display: flex;align-items: center;margin-right: 4px">
                                                    <el-text type="info" style="margin-right: 8px;">生成时间
                                                    </el-text>
                                                    <transition name="blur-scale" mode="out-in">
                                                        <el-text v-if="tokenItem.generateTime"
                                                                 style="margin-left: 8px;margin-right: 8px;">
                                                            {{ tokenItem.generateTime }}
                                                        </el-text>
                                                        <el-text v-else type="info">等待保存后生成</el-text>
                                                    </transition>
                                                </div>
                                                <div class="panel-1 disable-init-animate"
                                                     style="min-width: 30px;flex: 1;padding: 4px 8px;display: flex;align-items: center;margin-right: 4px;">
                                                    <el-text type="info" style="margin-right: 8px;">描述</el-text>
                                                    <el-text v-if="tokenItem.description">{{
                                                            tokenItem.description
                                                        }}
                                                    </el-text>
                                                    <el-text v-else type="info">无</el-text>
                                                </div>
                                                <div class="panel-1 clickable disable-init-animate"
                                                     style="display: flex;flex-direction: row;padding: 4px 8px;margin-right: 4px;"
                                                     @click="router.push({name: 'user-detail',params: {id: tokenItem.generateByUserQQ}})">
                                                    <el-text type="info" style="margin-right: 8px;">创建用户
                                                    </el-text>
                                                    <el-avatar shape="circle" :size="24" fit="cover"
                                                               :src="getAvatarUrlOf(tokenItem.generateByUserQQ)"/>
                                                    <el-text v-if="allUsers[tokenItem.generateByUserQQ]"
                                                             style="margin-right: 4px;margin-left: 8px;align-self: center">
                                                        {{ allUsers[tokenItem.generateByUserQQ].name }}
                                                    </el-text>
                                                    <el-text type="info"
                                                             style="margin-right: 4px;align-self: center">
                                                        {{ tokenItem.generateByUserQQ }}
                                                    </el-text>
                                                </div>
                                                <el-button @click="deleteToken(index)" :disabled="!editing"
                                                           style="height: 34px">
                                                    <HarmonyOSIcon_Remove/>
                                                    删除
                                                </el-button>
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </transition-group>
                        </div>
                        <el-text style="align-self: baseline" type="info" v-else>无数据</el-text>
                    </transition>
                </div>
            </div>
        </el-scrollbar>

        <custom-dialog v-model="showCreateNewTokenDialog"
                       title="新建 Token"
                       :buttons-option="createTokenButtonOption">
            <el-text style="display: block;margin-bottom: 4px;">SID（可选，不填则自动生成）</el-text>
            <el-input style="margin-bottom: 16px" v-model="newTokenSid" placeholder="留空将自动生成 UUID 格式的 SID"/>
            <el-text style="display: block;margin-bottom: 4px;">描述</el-text>
            <el-input style="margin-top: 8px" v-model="newTokenDescription"/>
        </custom-dialog>
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
</style>
