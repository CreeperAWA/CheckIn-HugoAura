<script setup>
import getAvatarUrlOf from "@/utils/Avatar.js";
import HarmonyOSIcon_InfoCircle from "@/components/icons/HarmonyOSIcon_InfoCircle.vue";
import router from "@/router/index.js";
import {ElMessage, ElMessageBox} from "element-plus";
import {ArrowLeftBold, Link, PictureRounded, Close} from "@element-plus/icons-vue";
import _Loading_ from "@/components/common/_Loading_.vue";
import VueTurnstile from "vue-turnstile";
import WebSocketConnector from "@/api/websocket.js";
import {jwtDecode} from "jwt-decode";
import {ref, computed, onErrorCaptured, onBeforeUnmount, watchEffect, getCurrentInstance} from "vue";

const {proxy} = getCurrentInstance();
const props = defineProps({
    facadeData: {
        required: true,
        type: Object
    },
    extraData: {
        required: true,
        type: Object
    }
});

const requiredPartitionIds = props.extraData.requiredPartitionIds;
const selectablePartitionIds = props.extraData.selectablePartitionIds;

const selectedPartitionIds = ref([]);
const selectPartition = (partitionId) => {
    const index = selectedPartitionIds.value.indexOf(partitionId);
    if (index === -1) {
        selectedPartitionIds.value.push(partitionId);
    } else {
        selectedPartitionIds.value.splice(index, 1);
    }
}

proxy.$http.get("check-turnstile").then((resp) => {
    proxy.$cookies.set("verifyLogin", resp.enableTurnstileOnLogin, "7d", "/checkIn");
    proxy.$cookies.set("verifyExam", resp.enableTurnstileOnExam, "7d", "/checkIn");
    proxy.$cookies.set("siteKey", resp.siteKey, "7d", "/checkIn");
});

const qqNumber = ref();

const loadingExam = ref(false);

let verifyResultUnregister = null;
let wsDisconnectTimer = null;
let isVerifying = false;

const startExam = () => {
    startExamGenerationFlow();
};

const startExamGenerationFlow = () => {
    console.log("[Exam Generation] Starting exam generation flow");
    console.log("[Exam Generation] QQ:", qqNumber.value);
    console.log("[Exam Generation] Selected partition IDs:", selectedPartitionIds.value);
    
    loadingExam.value = true;
    isVerifying = true;
    
    verifyResultUnregister = WebSocketConnector.registerAction("qq_verify_result", handleVerifyResult);
    
    const messageId = crypto.randomUUID();
    WebSocketConnector.send({
        type: "start_qq_verify",
        messageId: messageId,
        data: {
            qq: String(qqNumber.value)
        }
    }).then((response) => {
        if (response.type === "error") {
            loadingExam.value = false;
            isVerifying = false;
            ElMessage({
                type: "error",
                message: "启动验证失败：" + (response.data?.message || "未知错误")
            });
            reset();
        }
    }).catch((err) => {
        loadingExam.value = false;
        isVerifying = false;
        ElMessage({
            type: "error",
            message: "启动验证异常" + ((err && err.message) ? err.message : "")
        });
        reset();
    });
};

const startWsDisconnectMonitor = () => {
    clearWsDisconnectTimer();
    
    wsDisconnectTimer = setTimeout(() => {
        if (isVerifying && (!WebSocketConnector.ws || WebSocketConnector.ws.readyState !== WebSocket.OPEN)) {
            loadingExam.value = false;
            isVerifying = false;
            showVerifyDialog.value = false;
            verifyLoading.value = false;
            
            ElMessageBox.alert(
                "验证系统连接异常，请重新验证",
                "验证系统异常", {
                    type: "error",
                    draggable: true,
                    showClose: false,
                    confirmButtonText: "确定"
                }
            );
            reset();
        }
    }, 10 * 1000);
};

const clearWsDisconnectTimer = () => {
    if (wsDisconnectTimer) {
        clearTimeout(wsDisconnectTimer);
        wsDisconnectTimer = null;
    }
};

const handleVerifyResult = (message) => {
    const resultData = message.data;
    const status = resultData.status;
    
    console.log("[Verify] Received result:", resultData);
    
    if (status === "verify_check_result") {
        loadingExam.value = false;
        
        try {
            const checkData = typeof resultData.message === 'string' ? JSON.parse(resultData.message) : resultData.message;
            
            if (checkData.type === "verify_required") {
                verifyGuideMessage.value = checkData.guide_message || "请进行QQ号验证";
                verifyContent.value = checkData.verify_content;
                showVerifyDialog.value = true;
                verifyLoading.value = true;
                startWsDisconnectMonitor();
            } else if (checkData.type === "no_verify_needed") {
                showVerifyDialog.value = false;
                verifyLoading.value = false;
                isVerifying = false;
                clearWsDisconnectTimer();
                generateExamAfterVerify();
            } else if (checkData.type === "error") {
                showVerifyDialog.value = false;
                verifyLoading.value = false;
                loadingExam.value = false;
                isVerifying = false;
                clearWsDisconnectTimer();
                ElMessage({
                    type: "error",
                    message: checkData.message || "验证询问出错"
                });
                reset();
            }
        } catch (e) {
            console.error("解析验证检查结果失败", e);
            loadingExam.value = false;
            isVerifying = false;
            clearWsDisconnectTimer();
            ElMessage({
                type: "error",
                message: "验证检查结果解析失败"
            });
            reset();
        }
        return;
    }
    
    if (status === "verify_result") {
        try {
            const verifyData = typeof resultData.message === 'string' ? JSON.parse(resultData.message) : resultData.message;
            const verifyStatus = verifyData.status;
            
            verifyLoading.value = false;
            showVerifyDialog.value = false;
            loadingExam.value = false;
            isVerifying = false;
            clearWsDisconnectTimer();
            
            if (verifyStatus === "success") {
                ElMessage({
                    type: "success",
                    message: "验证成功，正在生成题目...",
                    duration: 2000
                });
                generateExamAfterVerify();
            } else if (verifyStatus === "failed") {
                ElMessageBox.alert(
                    verifyData.message || "验证失败，请重新验证",
                    "验证失败", {
                        type: "error",
                        draggable: true,
                        showClose: false,
                        confirmButtonText: "确定"
                    }
                );
                reset();
            } else if (verifyStatus === "timeout") {
                ElMessageBox.alert(
                    verifyData.message || "验证操作超时，请重新验证",
                    "验证超时", {
                        type: "error",
                        draggable: true,
                        showClose: false,
                        confirmButtonText: "确定"
                    }
                );
                reset();
            } else if (verifyStatus === "cannot_verify") {
                ElMessageBox.alert(
                    verifyData.message || "服务异常，请坐和放宽，稍后再试",
                    "服务异常", {
                        type: "error",
                        draggable: true,
                        showClose: false,
                        confirmButtonText: "确定"
                    }
                );
                reset();
            }
        } catch (e) {
            console.error("解析验证结果失败", e);
            loadingExam.value = false;
            isVerifying = false;
            clearWsDisconnectTimer();
            ElMessage({
                type: "error",
                message: "验证结果解析失败"
            });
            reset();
        }
    }
};

const generateExamAfterVerify = () => {
    loadingExam.value = true;
    
    proxy.$http.post("generate", {
        qq: qqNumber.value,
        partitionIds: selectedPartitionIds.value,
        turnstileToken: token.value
    }).then((data) => {
        console.log("[Generate] Exam generated:", data);
        loadingExam.value = false;
        
        if (data.type === "error") {
            handleGenerateError(data);
        } else {
            proxy.$cookies.set("examInfo", JSON.stringify(data), "7d");
            proxy.$cookies.set("phase", "examine", "7d");
            proxy.$cookies.remove("submissions");
            proxy.$cookies.remove("timestamps");
            router.push({name: "examine"});
        }
    }, (err) => {
        console.error("[Generate] Request failed:", err);
        loadingExam.value = false;
        ElMessage({
            type: "error",
            message: "生成题目时出错" + ((err && err.message) ? err.message : "")
        });
        reset();
    });
};

const clearVerifyTimer = () => {
    clearWsDisconnectTimer();
    isVerifying = false;
    if (verifyResultUnregister) {
        verifyResultUnregister.unregister();
        verifyResultUnregister = null;
    }
};

const validate1 = computed(() => selectedPartitionIds.value.length >= props.extraData.partitionRange[0] && selectedPartitionIds.value.length <= props.extraData.partitionRange[1]);
const validate2 = computed(() => qqNumber.value > 10000 && qqNumber.value < 100000000000);

const back = () => {
    proxy.$cookies.remove("phase");
    router.push({name: "facade"});
}

const token = ref("");
const turnstile = ref(null);

function reset() {
    if (turnstile.value)
        turnstile.value.reset();
}

const siteKey = ref(proxy.$cookies.get("siteKey"));
const verifyExam = ref(proxy.$cookies.get("verifyExam") === "true");
proxy.$http.get("check-turnstile").then((resp) => {
    proxy.$cookies.set("verifyLogin", resp.enableTurnstileOnLogin, "7d", "/checkIn");
    proxy.$cookies.set("verifyExam", resp.enableTurnstileOnExam, "7d", "/checkIn");
    proxy.$cookies.set("siteKey", resp.siteKey, "7d", "/checkIn");
    verifyExam.value = resp.enableTurnstileOnExam;
    siteKey.value = resp.siteKey;
});

onErrorCaptured((e) => {
    if (e.name === "TurnstileError") {
        console.trace("turnstile disabled", e);
        return false;
    }
})

const showVerifyDialog = ref(false);
const verifyGuideMessage = ref("");
const verifyContent = ref("");
const verifyLoading = ref(false);

const handleGenerateError = (data) => {
    if (data.exceptionType === "QQVerifyFailed") {
        ElMessageBox.alert(
            data.description || "验证失败，请重新验证",
            "验证失败", {
                type: "error",
                draggable: true,
                showClose: false,
                confirmButtonText: "确定"
            }
        );
        reset();
    } else if (data.exceptionType === "QQVerifyTimeout") {
        ElMessageBox.alert(
            data.description || "验证操作超时，请重新验证",
            "验证超时", {
                type: "error",
                draggable: true,
                showClose: false,
                confirmButtonText: "确定"
            }
        );
        reset();
    } else if (data.exceptionType === "QQVerifyCannotVerify") {
        ElMessageBox.alert(
            data.description || "服务异常，请坐和放宽，稍后再试",
            "服务异常", {
                type: "error",
                draggable: true,
                showClose: false,
                confirmButtonText: "确定"
            }
        );
        reset();
    } else {
        reset();
        ElMessageBox.alert(
            data.description ? data.description : data.exceptionType,
            "生成题目时出错", {
                type: "error",
                draggable: true,
                showClose: false,
                confirmButtonText: "返回修改生成选项"
            }
        );
    }
};

const loadingIconIndex = ref(-1);

const errorMessage = proxy.$cookies.get("OAuth2ErrorMessage", "/checkIn");
if (errorMessage !== null) {
    ElMessageBox.alert(
        errorMessage,
        "绑定失败",
        {
            type: "error",
            draggable: true,
            showClose: false,
            confirmButtonText: "确定"
        }
    ).then(() => {
        proxy.$cookies.remove("OAuth2ErrorMessage", "/checkIn");
    });
}

const examToken = ref(proxy.$cookies.get("examToken", "/checkIn", "7d"));
const decodedHeaders = ref();
const allRequiredOAuth2HaveBend = ref({});

const haveBend = (provider) => {
    return decodedHeaders.value && decodedHeaders.value.OAuth2 ? Boolean(decodedHeaders.value.OAuth2[provider.id]) : false;
}

const unwatch1 = watchEffect(() => {
    decodedHeaders.value = Boolean(examToken.value) ? jwtDecode(examToken.value, {header: true}) : null
});
const unwatch2 = watchEffect(() => {
    for (const oAuth2Provider of props.extraData.oAuth2Providers) {
        if (oAuth2Provider.required && !haveBend(oAuth2Provider)) {
            allRequiredOAuth2HaveBend.value = false;
            return;
        }
    }
    allRequiredOAuth2HaveBend.value = true;
});

onBeforeUnmount(() => {
    unwatch1();
    unwatch2();
    clearVerifyTimer();
})

proxy.$http.post("refresh-exam-token", {}).then(() => {
    examToken.value = proxy.$cookies.get("examToken", "/checkIn");
});

const switchBinding = (provider, index) => {
    if (!haveBend(provider)) {
        loadingIconIndex.value = index;
        proxy.$cookies.set("OAuth2Mode", "exam", "10m", "/checkIn");
        proxy.$cookies.set("OAuth2FallbackRouteName", proxy.$router.currentRoute.value.name, "10m", "/checkIn");
        window.location.href = `${window.location.origin}/checkIn/api/oauth2/authorization/${provider.id}`;
    } else {
        ElMessageBox.confirm(
            "可重新绑定",
            "确认解绑？",
            {
                type: "warning",
                draggable: true,
                showClose: false,
                confirmButtonText: "确认解绑",
                cancelButtonText: "取消操作"
            },
        ).then(() => {
            proxy.$cookies.set("OAuth2Mode", "exam", "10m", "/checkIn");
            proxy.$cookies.set("OAuth2FallbackRouteName", proxy.$router.currentRoute.value.name, "10m", "/checkIn");
            proxy.$http.post("refresh-exam-token", {
                unbindOAuth2s: [provider.id]
            }).then(() => {
                delete decodedHeaders.value.OAuth2[provider.id];
                ElMessage({
                    type: "success",
                    message: "取消绑定成功",
                });
            }, (e) => {
                ElMessage({
                    type: "error",
                    message: "取消绑定失败：" + e.data,
                });
                console.error(e);
            })
        }).catch(() => {
        });
    }
}
</script>

<template>
    <div class="auto-padding-center" style="flex:1;padding-bottom: 200px;">
        <el-button link size="large" @click="back"
                   style="margin-top: 36px;align-self: baseline;padding: 8px 16px !important;font-size: 1em">
            <el-icon>
                <ArrowLeftBold/>
            </el-icon>
            返回
        </el-button>
        <template v-if="((requiredPartitionIds && requiredPartitionIds.length > 0)
                        || (selectablePartitionIds && selectablePartitionIds.length > 0))">
            <el-text style="font-size: 24px;align-self: baseline;margin-top: 24px">选择分区</el-text>
            <div class="panel" style="padding: 16px 24px;margin-top: 36px"
                 v-if="requiredPartitionIds && requiredPartitionIds.length > 0">
                <el-text size="large" style="align-self: baseline">必选分区</el-text>
                <div style="display: flex;flex-direction: row;flex-wrap: wrap;margin-top: 16px;">
                    <el-tag size="large" type="info" style="font-size: 14px;margin: 2px"
                            v-for="requiredPartitionId of requiredPartitionIds">
                        {{ extraData.partitions[requiredPartitionId] }}
                    </el-tag>
                </div>
            </div>
            <div class="panel" v-if="selectablePartitionIds && selectablePartitionIds.length > 0"
                 style="padding: 16px 24px;margin-top: 24px">
                <div style="display: flex;flex-direction: row;flex-wrap: wrap;">
                    <el-text size="large" style="margin-right: 8px;">可选分区</el-text>
                    <el-text>{{ selectedPartitionIds.length }} / {{ selectablePartitionIds.length }}</el-text>
                    <el-text style="margin-left: 8px;"
                             :type="validate1?'info':'danger'">
                        请选择 {{ extraData.partitionRange[0] }} ~
                        {{ Math.min(selectablePartitionIds.length, extraData.partitionRange[1]) }} 个
                    </el-text>
                </div>
                <div style="display: flex;flex-direction: row;flex-wrap: wrap;margin-top: 16px;">
                    <el-check-tag size="large" type="info" style="font-size: 14px;margin: 2px;"
                                  v-for="partitionId of selectablePartitionIds"
                                  :checked="selectedPartitionIds.includes(partitionId)"
                                  @click="selectPartition(partitionId)">
                        {{ extraData.partitions[partitionId] }}
                    </el-check-tag>
                </div>
            </div>
        </template>
        <template v-if="extraData.oAuth2Providers?.length > 0">
            <div style="display: flex;flex-direction: row;align-items: center;margin-top: 36px">
                <el-text style="font-size: 24px;align-self: baseline;margin-right: 16px;">绑定第三方账户</el-text>
                <el-popover trigger="hover" width="250">
                    <template #reference>
                        <HarmonyOSIcon_InfoCircle :size="20"/>
                    </template>
                    <template #default>
                        <el-text>仅用于记录答题信息及后续用户登录</el-text>
                    </template>
                </el-popover>
            </div>
            <div style="display: flex;flex-direction: row;align-items: start;margin-top: 16px;gap: 8px;flex-wrap: wrap">
                <div class="panel-1" style="padding: 12px 20px;display: flex;flex: 1;max-width: 240px"
                     v-for="(provider, index) of extraData.oAuth2Providers">
                    <el-image :src="'https://favicon.im/' + provider.iconDomain"
                              style="width: 30px;height: 30px;padding: 4px">
                        <template #error>
                            <el-icon :size="32">
                                <PictureRounded/>
                            </el-icon>
                        </template>
                    </el-image>
                    <div style="display: flex;flex-direction: column;justify-content: center;margin-left: 4px;">
                        <el-text style="align-self: start;" size="large">
                            {{ provider.name }}
                        </el-text>
                        <el-text v-if="provider.required"
                                 :type="haveBend(provider) ? 'info' : 'danger'"
                                 style="align-self: start;">
                            必须
                        </el-text>
                        <el-text v-else type="info" style="align-self: start;">
                            可选
                        </el-text>
                    </div>
                    <div class="flex-blank-1" style="min-width: 20px"></div>
                    <el-button :loading="loadingIconIndex === index" :disabled="loadingIconIndex !== -1"
                               :icon="haveBend(provider) ? Close : Link"
                               :loading-icon="_Loading_" link
                               :type="haveBend(provider) ? 'danger' : 'primary'"
                               style="align-self: center" @click="switchBinding(provider, index)">
                        {{ haveBend(provider) ? "解绑" : "绑定" }}
                    </el-button>
                </div>
            </div>
        </template>
        <div style="display: flex;flex-direction: row;align-items: center;margin-top: 36px">
            <el-text style="font-size: 24px;align-self: baseline;margin-right: 16px;">你的 QQ 号码</el-text>
            <el-popover trigger="hover" width="250">
                <template #reference>
                    <HarmonyOSIcon_InfoCircle :size="20"/>
                </template>
                <template #default>
                    <el-text>仅用于记录答题信息及后续用户登录</el-text>
                </template>
            </el-popover>
        </div>
        <div style="display: flex;flex-direction: row;align-items: center;margin-top: 16px;margin-left: 16px;">
            <el-avatar :size="64" style="margin-right: 16px" :src="getAvatarUrlOf(qqNumber)"/>
            <el-input-number :class="{error: !validate2}" v-model="qqNumber"
                             :controls="false" style="min-width: min(70dvw,200px)"/>
        </div>
        <div class="flex-blank-1"></div>
        <div style="height: 65px;width:300px;display: flex;">
            <vue-turnstile ref="turnstile" v-if="verifyExam" appearance="interaction-only" @error="reset"
                           :site-key="siteKey" v-model="token" size="normal"/>
        </div>
        <el-button type="primary" size="large" :loading="Boolean(loadingExam || (token.length === 0 && verifyExam))"
                   :loading-icon="_Loading_"
                   style="margin-top: 36px;align-self: center;min-width: 180px"
                   :disabled="!extraData.serviceAvailable || !(validate1 && validate2) || (token.length === 0 && verifyExam) || !allRequiredOAuth2HaveBend"
                   @click="startExam">
            {{
                extraData.serviceAvailable ? token.length === 0 && verifyExam ? "等待 Cloudflare 验证" : "生成题目" : "服务暂不可用"
            }}
        </el-button>
    </div>

    <!-- QQ验证对话框 -->
    <el-dialog
        v-model="showVerifyDialog"
        title="QQ号验证"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
        :show-close="false"
        width="500px"
    >
        <div style="padding: 20px;">
            <div v-if="verifyLoading" style="display: flex; align-items: center; justify-content: center; padding: 40px 0;">
                <el-icon class="is-loading" style="font-size: 32px; margin-right: 16px;">
                    <svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 1024 1024" fill="currentColor"><path d="M512 0a48 48 0 0 1 48 48v160a48 48 0 0 1-96 0V48a48 48 0 0 1 48-48zM272 192a48 48 0 0 1 48-48h160a48 48 0 0 1 0 96H320a48 48 0 0 1-48-48zm0 256a48 48 0 0 1 48-48h160a48 48 0 0 1 0 96H320a48 48 0 0 1-48-48zm256 0a48 48 0 0 1 48-48h160a48 48 0 0 1 0 96H576a48 48 0 0 1-48-48zm-256 256a48 48 0 0 1 48-48h160a48 48 0 0 1 0 96H320a48 48 0 0 1-48-48zm256 0a48 48 0 0 1 48-48h160a48 48 0 0 1 0 96H576a48 48 0 0 1-48-48zM464 0a48 48 0 0 1 48 48v160a48 48 0 0 1-96 0V48a48 48 0 0 1 48-48z" /></svg>
                </el-icon>
                <el-text size="large">等待进行验证</el-text>
            </div>
            <div v-else style="padding: 20px 0;">
                <div style="margin-bottom: 20px;">
                    <el-text style="white-space: pre-line;">{{ verifyGuideMessage }}</el-text>
                </div>
                <div style="margin-top: 20px; padding: 15px; background-color: #f5f7fa; border-radius: 4px;">
                    <el-text type="primary" size="large">验证内容: </el-text>
                    <el-tag type="primary" size="large" style="margin-left: 10px;">{{ verifyContent }}</el-tag>
                </div>
            </div>
        </div>
    </el-dialog>
</template>

<style scoped>

</style>