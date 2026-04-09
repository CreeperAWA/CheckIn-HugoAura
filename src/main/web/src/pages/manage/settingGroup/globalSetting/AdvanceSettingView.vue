<script setup>
import WebSocketConnector from "@/api/websocket.js";
import {ElMessage, ElMessageBox} from "element-plus";
import HarmonyOSIcon_Plus from "@/components/icons/HarmonyOSIcon_Plus.vue";
import CustomDialog from "@/components/common/CustomDialog.vue";
import UserDataInterface from "@/data/UserDataInterface.js";
import getAvatarUrlOf from "@/utils/Avatar.js";
import { uuidv7 } from "uuidv7";
import HarmonyOSIcon_Remove from "@/components/icons/HarmonyOSIcon_Remove.vue";
import router from "@/router/index.js";
import PermissionInfo from "@/auth/PermissionInfo.js";

// 检查修改权限
const canModify = PermissionInfo.hasPermission('MODIFY_RATE_LIMIT_CONFIG');

const editing = ref(false);
const data = ref({
    questionAmount: 0,
    questionScore: 5,
    partitionRange: [0, 100],
    rateLimitRules: [],
    rateLimitWhitelist: []
});
const loading = ref(true);
const error = ref(false);
let backup = {};
let backupJSON;
const cancel = () => {
    data.value = backup;
    editing.value = false;
}
const startEditing = () => {
    backupJSON = JSON.stringify(data.value)
    backup = JSON.parse(backupJSON);
    editing.value = true;
}
const finishEditing = () => {
    editing.value = false;
    if (backupJSON !== JSON.stringify(data.value)) {
        // 验证限流规则
        if (!validateRateLimitRules()) {
            return;
        }
        
        const rules = JSON.parse(JSON.stringify(data.value.rateLimitRules || []));
        const rateLimitData = {
            rules: rules,
            createdWhitelistItems: JSON.parse(JSON.stringify(createdWhitelistItems.value)),
            deletedWhitelistIds: JSON.parse(JSON.stringify(deletedWhitelistIds.value))
        };
        
        WebSocketConnector.send({
            type: "saveRateLimitSetting",
            data: rateLimitData
        }).then((response) => {
            createdWhitelistItems.value = [];
            deletedWhitelistIds.value = [];
            
            // 保存其他高级设置
            WebSocketConnector.send({
                type: "saveAdvanceSetting",
                data: {
                    data: data.value
                }
            }).then((response) => {
                // 只更新 robotTokenItems，不覆盖整个 data 对象
                if (response.data.currentTokens) {
                    data.value.robotTokenItems = response.data.currentTokens;
                }
                data.value.deletedRobotTokenIds = [];
                data.value.createdRobotTokens = [];
                ElMessage({
                    type: "success", message: "保存成功"
                });
            }, (err) => {
                ElMessage({
                    type: "error", message: "保存失败"
                })
            });
        }).catch((err) => {
            console.error('保存错误:', err);
            ElMessage({
                type: "error", message: "保存失败：" + (err.message || '未知错误')
            });
        });
    }
}

const validateRateLimitRules = () => {
    const rules = data.value.rateLimitRules || [];
    for (let i = 0; i < rules.length; i++) {
        const rule = rules[i];
        const ruleIndex = `规则 #${i + 1}`;
        
        if (!rule.timeWindowSeconds) {
            ElMessage({type: 'error', message: `${ruleIndex}：时间窗口不能为空`});
            return false;
        }
        if (rule.timeWindowSeconds <= 0) {
            ElMessage({type: 'error', message: `${ruleIndex}：时间窗口必须为正整数（单位：秒）`});
            return false;
        }
        if (rule.timeWindowSeconds > 86400) {
            ElMessage({type: 'error', message: `${ruleIndex}：时间窗口不能超过 24 小时（86400 秒）`});
            return false;
        }
        
        if (!rule.maxRequests) {
            ElMessage({type: 'error', message: `${ruleIndex}：最大请求数不能为空`});
            return false;
        }
        if (rule.maxRequests <= 0) {
            ElMessage({type: 'error', message: `${ruleIndex}：最大请求数必须为正整数`});
            return false;
        }
        if (rule.maxRequests > 100000) {
            ElMessage({type: 'error', message: `${ruleIndex}：最大请求数不能超过 100000`});
            return false;
        }
        
        if (!rule.responseStrategy) {
            ElMessage({type: 'error', message: `${ruleIndex}：响应策略不能为空`});
            return false;
        }
        if (rule.responseStrategy === 'CUSTOM_MESSAGE' && (!rule.customMessage || rule.customMessage.trim() === '')) {
            ElMessage({type: 'error', message: `${ruleIndex}：自定义提示信息不能为空`});
            return false;
        }
        
        if (rule.baseDelayMs !== undefined && rule.baseDelayMs !== null) {
            if (rule.baseDelayMs < 0) {
                ElMessage({type: 'error', message: `${ruleIndex}：基础延迟不能为负数`});
                return false;
            }
            if (rule.baseDelayMs > 60000) {
                ElMessage({type: 'error', message: `${ruleIndex}：基础延迟不能超过 60000 毫秒`});
                return false;
            }
        }
        
        if (rule.priority !== undefined && rule.priority !== null) {
            if (rule.priority < 0 || rule.priority > 100) {
                ElMessage({type: 'error', message: `${ruleIndex}：优先级必须在 0-100 之间`});
                return false;
            }
        }
        
        if (!rule.limitDurationSeconds) {
            ElMessage({type: 'error', message: `${ruleIndex}：限流持续时间不能为空`});
            return false;
        }
        if (rule.limitDurationSeconds <= 0) {
            ElMessage({type: 'error', message: `${ruleIndex}：限流持续时间必须为正整数（单位：秒）`});
            return false;
        }
        if (rule.limitDurationSeconds > 86400) {
            ElMessage({type: 'error', message: `${ruleIndex}：限流持续时间不能超过 24 小时（86400 秒）`});
            return false;
        }
    }
    return true;
}

const getData = () => {
    loading.value = true;
    error.value = false;
    
    // 加载其他高级设置
    WebSocketConnector.send({
        type: "getAdvanceSetting",
    }).then((response) => {
        data.value = response.data.data;
        data.value.deletedRobotTokenIds = [];
        data.value.createdRobotTokens = [];
        if (!data.value.ipSource) {
            data.value.ipSource = "request";
        }
        if (data.value.useRequestIpIfSourceIsNull === null ||
                data.value.useRequestIpIfSourceIsNull === undefined) {
            data.value.useRequestIpIfSourceIsNull = true;
        }
        
        // 加载限流设置
        WebSocketConnector.send({
            type: "getRateLimitSetting",
        }).then((response) => {
            data.value.rateLimitRules = response.data?.data?.rules || [];
            data.value.rateLimitWhitelist = response.data?.data?.whitelist || [];
            loading.value = false;
        }, (err) => {
            ElMessage({
                type: "error", message: "获取限流设置失败"
            });
            loading.value = false;
            error.value = true;
        });
    }, (err) => {
        ElMessage({
            type: "error", message: "获取设置失败"
        });
        loading.value = false;
        error.value = true;
    });
}
getData();

// 限流相关数据和方法
const showAddWhitelistDialog = ref(false);
const newWhitelistItem = ref({
    dimension: 'IP',
    value: '',
    description: ''
});
const createdWhitelistItems = ref([]);
const deletedWhitelistIds = ref([]);

const getDimensionName = (dimension) => {
    const names = {
        IP: 'IP 地址限流',
        COOKIE: 'Cookie 限流',
        QQ: 'QQ 号限流',
        OAUTH: 'OAuth 信息限流'
    };
    return names[dimension] || dimension;
};

const getWhitelistDimensionType = (dimension) => {
    const types = {
        IP: '',
        COOKIE: 'success',
        OAUTH: 'warning'
    };
    return types[dimension] || 'info';
};

const addWhitelistItem = () => {
    newWhitelistItem.value = {dimension: 'IP', value: '', description: ''};
    showAddWhitelistDialog.value = true;
};

const whitelistDialogButtons = ref([{
    content: '确定',
    type: 'primary',
    onclick: () => {
        if (!newWhitelistItem.value.value) {
            ElMessage({type: 'warning', message: '请输入白名单值'});
            return;
        }
        if (!(data.value.rateLimitWhitelist instanceof Array)) {
            data.value.rateLimitWhitelist = [];
        }
        const item = {
            id: uuidv7(),
            dimension: newWhitelistItem.value.dimension,
            value: newWhitelistItem.value.value,
            description: newWhitelistItem.value.description,
            createdAt: new Date().toISOString()
        };
        data.value.rateLimitWhitelist.push(item);
        createdWhitelistItems.value.push(item);
        showAddWhitelistDialog.value = false;
    }
}, {
    content: '取消',
    type: 'info',
    onclick: () => {
        showAddWhitelistDialog.value = false;
    }
}]);

const removeWhitelistItem = (index) => {
    const item = data.value.rateLimitWhitelist[index];
    if (item.id) {
        deletedWhitelistIds.value.push(item.id);
    }
    data.value.rateLimitWhitelist.splice(index, 1);
};

const showCreateNewTokenDialog = ref(false);
const newTokenDescription = ref("");
const onClose = () => {
    showCreateNewTokenDialog.value = false;
    newTokenDescription.value = "";
}
const createTokenButtonOption = ref([{
    content: "确定",
    type: "primary",
    onclick: () => {
        if (!(data.value.robotTokenItems instanceof Array)) {
            data.value.robotTokenItems = [];
        }
        const token = {
            id: uuidv7(),
            token: null,
            description: newTokenDescription.value,
            generateTime: null,
            generateByUserQQ: UserDataInterface.getCurrentUser().value.qq
        };
        data.value.robotTokenItems.push(token);
        data.value.createdRobotTokens.push(token);
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
    if (Boolean(data.value.robotTokenItems[index].token)) {
        ElMessageBox.confirm(
                "该 token 将无法再被使用",
                "确定删除 token",
                {
                    showClose: false,
                    draggable: true,
                    confirmButtonText: "确定",
                    cancelButtonText: "取消",
                    type: "warning",
                }
        ).then(() => {
            console.log(data.value.robotTokenItems[index]);
            data.value.deletedRobotTokenIds.push(data.value.robotTokenItems[index].id);
            data.value.robotTokenItems.splice(index, 1);
        }, () => {
        });
    } else {
        data.value.robotTokenItems.splice(index, 1);
    }
}
</script>

<template>
    <div style="display: flex;flex-direction: column;">
        <custom-dialog v-model="showCreateNewTokenDialog"
                       title="新建 token"
                       :buttons-option="createTokenButtonOption">
            <el-text>描述</el-text>
            <el-input style="margin-top: 8px" v-model="newTokenDescription"/>
        </custom-dialog>
        
        <!-- 白名单添加对话框 -->
        <custom-dialog v-model="showAddWhitelistDialog" title="添加白名单项" :buttons-option="whitelistDialogButtons">
            <div style="display: flex;flex-direction: column;gap: 16px">
                <div>
                    <el-text style="margin-bottom: 4px;display: block">维度</el-text>
                    <el-select v-model="newWhitelistItem.dimension" style="width: 100%">
                        <el-option label="IP 地址" value="IP"/>
                        <el-option label="Cookie" value="COOKIE"/>
                        <el-option label="OAuth 信息" value="OAUTH"/>
                    </el-select>
                </div>
                <div>
                    <el-text style="margin-bottom: 4px;display: block">值</el-text>
                    <el-input v-model="newWhitelistItem.value" placeholder="输入要加入白名单的值..."/>
                </div>
                <div>
                    <el-text style="margin-bottom: 4px;display: block">描述（可选）</el-text>
                    <el-input v-model="newWhitelistItem.description" placeholder="添加描述说明..."/>
                </div>
            </div>
        </custom-dialog>
        
        <div style="display: flex;flex-direction: row;margin-bottom: 32px;">
            <el-text style="align-self:baseline;font-size: 24px">高级设置</el-text>
            <div style="display: flex;margin-left: 32px;">
                <transition-group name="blur-scale">
                    <el-button-group key="button-group">
                        <transition-group name="blur-scale">
                            <el-button class="disable-init-animate" style="margin-right: 4px;"
                                       @click="editing ? finishEditing():startEditing()"
                                       :disabled="loading || error" key="edit">
                                {{ editing ? '完成' : '编辑' }}
                            </el-button>
                            <el-button class="disable-init-animate" style="margin-right: 24px;"
                                       @click="cancel" v-if="editing" key="cancel">
                                取消
                            </el-button>
                        </transition-group>
                    </el-button-group>
                </transition-group>
            </div>
        </div>
        <el-scrollbar v-loading="loading">
            <div style="display: flex;flex-direction: column;align-items: center">
                <transition name="blur-scale" mode="out-in">
                    <div v-if="!loading && !error"
                         style="max-width: 1280px;width: min(85%,1280px);display: flex;flex-direction: column;align-items: stretch">
                        <div style="display: flex;flex-direction: row;flex-wrap: wrap;align-items: center;margin-bottom: 12px">
                            <el-text size="large" style="align-self: center;margin-bottom: 8px">IP 兼容</el-text>
                            <el-radio-group v-model="data.ipSource" :disabled="!editing">
                                <el-radio value="request">使用传入 IP (默认)</el-radio>
                                <el-radio value="x_real_ip">从请求头 "x-real-ip" 解析 IP</el-radio>
                                <el-radio value="x_forwarded_for">从请求头 "x-forwarded-for" 解析 IP</el-radio>
                                <el-radio value="remote_host">从请求头 "remote-host" 解析 IP</el-radio>
                                <el-radio value="cf_connecting_ip">从请求头 "cf-connecting-ip" 解析 IP (Cloudflare)</el-radio>
                                <el-radio value="true_client_ip">从请求头 "true-client-ip" 解析 IP (Cloudflare Enterprise)</el-radio>
                                <el-radio value="custom_header_regex">从与正则表达式匹配的自定义请求头解析 IP</el-radio>
                            </el-radio-group>
                        </div>
                        <transition name="smooth-height" mode="out-in">
                            <div class="smooth-height-base" v-if="data.ipSource === 'custom_header_regex'">
                                <div>
                                    <div style="display: flex;flex-direction: column;flex-wrap: wrap;align-items: start;margin-bottom: 12px">
                                        <el-text size="large" style="align-self: start;margin-bottom: 8px;">自定义正则表达式</el-text>
                                        <el-input class="disable-init-animate" style="max-width: min(100%, 400px)"
                                                  v-model="data.ipSourceCustomHeaderRegex" :disabled="!editing"/>
                                    </div>
                                </div>
                            </div>
                        </transition>
                        <div style="display: flex;flex-direction: row;flex-wrap: wrap;align-items: center;margin-bottom: 24px">
                            <el-text size="large" style="align-self: center;margin-right: 16px">无法从请求头中获取 IP 时回退至从请求获取</el-text>
                            <el-switch class="disable-init-animate" v-model="data.useRequestIpIfSourceIsNull" :disabled="!editing"></el-switch>
                        </div>
                        <div style="display: flex;flex-direction: column;flex-wrap: wrap;align-items: start;margin-bottom: 4px">
                            <el-text size="large" style="align-self: start;margin-bottom: 8px;">启用 Cloudflare Turnstile</el-text>
                            <div style="display: flex;flex-direction: row;flex-wrap: wrap;align-items: center;">
                                <div style="margin-right: 36px;margin-left: 12px">
                                    <el-text style="margin-right: 16px">登录</el-text>
                                    <el-switch class="disable-init-animate" v-model="data.enableTurnstileOnLogin" :disabled="!editing"></el-switch>
                                </div>
                                <div>
                                    <el-text style="margin-right: 16px">答题</el-text>
                                    <el-switch class="disable-init-animate" v-model="data.enableTurnstileOnExam" :disabled="!editing"></el-switch>
                                </div>
                            </div>
                        </div>
                        <transition name="smooth-height" mode="out-in">
                            <div class="smooth-height-base" v-if="data.enableTurnstileOnLogin || data.enableTurnstileOnExam">
                                <div style="display: flex;flex-direction: row;flex-wrap: wrap;align-items: start;margin-left: 12px">
                                    <div style="width: 360px;margin-right: 8px;display: flex;flex-direction: column;align-items: start;justify-content: start">
                                        <el-text style="align-self: start">Site Key</el-text>
                                        <el-input style="margin-top: 4px;max-width: calc(85% - 12px);" type="text" v-model="data.turnstileSiteKey"
                                                  placeholder="留空则启用无效" class="disable-init-animate"
                                                  :disabled="!editing || (!data.enableTurnstileOnLogin && !data.enableTurnstileOnExam)"></el-input>
                                    </div>
                                    <div style="width: 360px;margin-right: 8px;display: flex;flex-direction: column;align-items: start;justify-content: start">
                                        <el-text style="align-self: start">Secret Key</el-text>
                                        <el-input style="margin-top: 4px;max-width: calc(85% - 12px)" type="text"
                                                  v-model="data.turnstileSecret" placeholder="留空则启用无效"
                                                  class="disable-init-animate"
                                                  :disabled="!editing || (!data.enableTurnstileOnLogin && !data.enableTurnstileOnExam)"></el-input>
                                    </div>
                                </div>
                            </div>
                        </transition>
                        
                        <!-- 限流规则配置 -->
                        <div style="margin-top: 32px;padding-top: 24px;border-top: 1px solid var(--el-border-color)">
                            <el-text size="large" style="font-weight: bold;margin-bottom: 16px;display: block">请求频率限流设置</el-text>
                            
                            <div v-for="(rule, ruleIndex) in (data.rateLimitRules || [])" :key="ruleIndex" 
                                 style="background: var(--el-fill-color-light);padding: 16px;border-radius: 8px;margin-bottom: 12px">
                                <div style="display: flex;align-items: center;margin-bottom: 12px">
                                    <el-switch v-model="rule.enabled" :disabled="!editing || !canModify" style="margin-right: 12px"/>
                                    <el-text size="large" style="font-weight: 500">{{ getDimensionName(rule.dimension) }}</el-text>
                                </div>
                                
                                <div v-if="rule.enabled || editing" style="display: flex;flex-direction: column;gap: 12px">
                                    <div style="display: flex;gap: 16px;flex-wrap: wrap;align-items: end">
                                        <div style="flex: 1;min-width: 200px">
                                            <el-text style="margin-bottom: 4px;display: block">时间窗口</el-text>
                                            <el-select v-model="rule.timeWindowSeconds" :disabled="!editing || !canModify" style="width: 100%">
                                                <el-option label="1 分钟" :value="60"/>
                                                <el-option label="5 分钟" :value="300"/>
                                                <el-option label="15 分钟" :value="900"/>
                                                <el-option label="1 小时" :value="3600"/>
                                                <el-option label="24 小时" :value="86400"/>
                                            </el-select>
                                        </div>
                                        
                                        <div style="flex: 1;min-width: 200px">
                                            <el-text style="margin-bottom: 4px;display: block">最大请求数</el-text>
                                            <el-input-number v-model="rule.maxRequests" :min="1" :max="10000" 
                                                           :disabled="!editing || !canModify" style="width: 100%"/>
                                        </div>
                                        
                                        <div style="flex: 1;min-width: 200px">
                                            <el-text style="margin-bottom: 4px;display: block">限流持续时间</el-text>
                                            <el-select v-model="rule.limitDurationSeconds" :disabled="!editing || !canModify" style="width: 100%">
                                                <el-option label="1 分钟" :value="60"/>
                                                <el-option label="5 分钟" :value="300"/>
                                                <el-option label="15 分钟" :value="900"/>
                                                <el-option label="1 小时" :value="3600"/>
                                                <el-option label="24 小时" :value="86400"/>
                                            </el-select>
                                        </div>
                                        
                                        <div style="flex: 1;min-width: 200px">
                                            <el-text style="margin-bottom: 4px;display: block">响应策略</el-text>
                                            <el-select v-model="rule.responseStrategy" :disabled="!editing || !canModify" style="width: 100%">
                                                <el-option label="返回 429 状态码" value="RETURN_429"/>
                                                <el-option label="自定义提示信息" value="CUSTOM_MESSAGE"/>
                                                <el-option label="渐进式延迟" value="PROGRESSIVE_DELAY"/>
                                            </el-select>
                                        </div>
                                    </div>
                                    
                                    <div v-if="rule.responseStrategy === 'CUSTOM_MESSAGE'" style="max-width: 600px">
                                        <el-text style="margin-bottom: 4px;display: block">自定义提示信息</el-text>
                                        <el-input v-model="rule.customMessage" type="textarea" :rows="2" 
                                                  placeholder="输入自定义的限流提示信息..." :disabled="!editing || !canModify"/>
                                    </div>
                                    
                                    <div v-if="rule.responseStrategy === 'PROGRESSIVE_DELAY'" style="max-width: 300px">
                                        <el-text style="margin-bottom: 4px;display: block">基础延迟（毫秒）</el-text>
                                        <el-input-number v-model="rule.baseDelayMs" :min="100" :max="10000" :step="100"
                                                       :disabled="!editing || !canModify" style="width: 100%"/>
                                    </div>
                                    
                                    <div style="max-width: 200px">
                                        <el-text style="margin-bottom: 4px;display: block">优先级</el-text>
                                        <el-input-number v-model="rule.priority" :min="0" :max="100" 
                                                       :disabled="!editing || !canModify" style="width: 100%"/>
                                    </div>
                                </div>
                            </div>
                            
                            <!-- 白名单管理 -->
                            <div style="margin-top: 32px;padding-top: 24px;border-top: 1px solid var(--el-border-color)">
                                <div style="display: flex;align-items: center;margin-bottom: 12px">
                                    <el-text size="large" style="font-weight: 500">白名单管理</el-text>
                                    <el-button v-if="editing && canModify" link type="primary" style="margin-left: 12px" @click="addWhitelistItem">
                                        <HarmonyOSIcon_Plus style="margin-right: 4px"/>
                                        添加白名单
                                    </el-button>
                                </div>
                                
                                <div v-for="(item, index) in (data.rateLimitWhitelist || [])" :key="index"
                                     style="display: flex;align-items: center;gap: 12px;padding: 8px;background: var(--el-fill-color-lighter);border-radius: 4px;margin-bottom: 8px">
                                    <el-tag :type="getWhitelistDimensionType(item.dimension)" size="small">{{ item.dimension }}</el-tag>
                                    <el-text style="flex: 1;font-family: monospace">{{ item.value }}</el-text>
                                    <el-text v-if="item.description" type="info" style="flex: 2">{{ item.description }}</el-text>
                                    <el-button v-if="editing && canModify" link type="danger" size="small" @click="removeWhitelistItem(index)">
                                        移除
                                    </el-button>
                                </div>
                                
                                <el-empty v-if="!data.rateLimitWhitelist || data.rateLimitWhitelist.length === 0" description="暂无白名单项" :image-size="60"/>
                            </div>
                        </div>
                        
                        <div style="display: flex;flex-direction: row;flex-wrap: wrap;align-items: center;margin-bottom: 8px;margin-top: 24px">
                            <el-text size="large" style="align-self: center;margin-right: 16px">Robot tokens</el-text>
                            <transition name="blur-scale">
                                <el-button class="disable-init-animate" link @click="createNewToken" v-if="editing">
                                    <HarmonyOSIcon_Plus style="margin-right: 4px;"/>
                                    新建 token
                                </el-button>
                            </transition>
                        </div>
                        <transition name="blur-scale" mode="out-in">
                            <div style="display: flex;flex-direction: column"
                                 v-if="data.robotTokenItems && data.robotTokenItems.length > 0">
                                <transition-group name="smooth-height">
                                    <div class="smooth-height-base"
                                         v-for="(tokenItem,index) of data.robotTokenItems"
                                         :key="tokenItem.id">
                                        <div>
                                            <div style="display:flex;margin-bottom: 8px;flex-direction: column">
                                                <div class="panel-1 disable-init-animate"
                                                     style="padding: 4px 8px;display: flex;align-items: center;margin-bottom: 4px">
                                                    <el-text type="info" style="margin-right: 8px;">token</el-text>
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
                    <div v-else-if="error" style="display:flex;flex-direction: column">
                        <el-empty description="获取设置失败"></el-empty>
                        <el-button link type="primary" @click="getData" size="large">重试</el-button>
                    </div>
                </transition>
            </div>
        </el-scrollbar>
    </div>
</template>

<style scoped>
.field-label {
    align-self: baseline;
    margin-top: 16px;
}
</style>