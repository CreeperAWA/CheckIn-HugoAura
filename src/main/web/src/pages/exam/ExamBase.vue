<script setup>
import {RouterView} from "vue-router";
import router from "@/router/index.js";

const {proxy} = getCurrentInstance();
const facadeData = ref({});
const gradingData = ref({});
const extraData = ref({});
const partitions = ref({});
const loading = ref(true);
const error = ref(false);

const getData = () => {
    loading.value = true;
    error.value = false;
    
    proxy.$http.get("post-exam-data").then((response) => {
        if (response && response.passed === true) {
            proxy.$cookies.set("phase", "result", "7d");
            proxy.$cookies.set("result", JSON.stringify({
                qq: response.qq,
                score: response.score,
                correctCount: response.correctCount,
                halfCorrectCount: response.halfCorrectCount,
                wrongCount: response.wrongCount,
                questionCount: response.questionCount,
                level: response.level,
                levelId: response.levelId,
                colorHex: response.colorHex,
                message: response.message,
                signUpCompletingType: response.signUpCompletingType,
                showCreatingAccountGuide: response.showCreatingAccountGuide
            }), "7d");
            
            facadeData.value = response.facadeData || {};
            gradingData.value = response.gradingData || {};
            
            router.push({name: "result"}).then(() => {
                loading.value = false;
            });
        } else {
            loadPreExamData();
        }
    }).catch((e) => {
        console.log("Post exam data check failed or no valid record, using pre-exam-data", e);
        loadPreExamData();
    });
};

const loadPreExamData = () => {
    proxy.$http.get("pre-exam-data").then((response) => {
        facadeData.value = response.facadeData;
        gradingData.value = response.gradingData;
        extraData.value = response.extraData;
        partitions.value = response.partitions;
        
        const phrase = proxy.$cookies.get("phase");
        switch (phrase) {
            case "examine":
            case "generate":
            case "result":
            case "sign-up":
                router.push({name: phrase}).then(() => {
                    loading.value = false;
                });
                break;
            default:
                proxy.$cookies.set("phase", "facade", "7d");
                router.push({name: "facade"}).then(() => {
                    loading.value = false;
                });
                break;
        }
    }, (e) => {
        console.error(e);
        loading.value = false;
        error.value = true;
    });
};

onBeforeMount(() => {
    getData();
})
</script>

<template>
    <div style="display: flex;flex-direction: column;width:100dvw;height:100dvh">
        <div class="pwa-title-common"></div>
        <el-scrollbar v-loading="loading">
            <div class="center-base">
                <template v-if="!loading && !error">
                    <router-view v-slot="{ Component }">
                        <transition name="route-page" mode="out-in">
                            <component :is="Component" :facade-data="facadeData" :grading-data="gradingData"
                                       :extra-data="extraData" :partitions="partitions"/>
                        </transition>
                    </router-view>
                </template>
                <div v-else-if="error"
                     style="display:flex;flex-direction: column;flex: 1;align-items: center;justify-content: center">
                    <el-empty description="加载失败"></el-empty>
                    <el-button link type="primary" class="disable-init-animate" @click="getData" size="large">重试
                    </el-button>
                </div>
            </div>
        </el-scrollbar>
    </div>
</template>

<style scoped>
/*noinspection CssUnusedSymbol*/
.route-page-enter-active {
    transition: all 0.4s var(--ease-out-quint);
}

/*noinspection CssUnusedSymbol*/
.route-page-leave-active {
    transition: all 0.4s var(--ease-in-quint);
}

/*noinspection CssUnusedSymbol*/
.route-page-enter-from, .route-page-leave-to {
    filter: blur(32px);
    opacity: 0;
}

/*noinspection CssUnusedSymbol*/
.route-page-enter-from {
    scale: 0.95;
}

/*noinspection CssUnusedSymbol*/
.route-page-leave-to {
    scale: 1.05;
}
</style>