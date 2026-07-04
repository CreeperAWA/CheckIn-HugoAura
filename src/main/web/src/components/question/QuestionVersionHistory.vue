<script setup>
import QuestionCache from "@/data/QuestionCache.js";
import QuestionDiffViewer from "@/components/question/QuestionDiffViewer.vue";
import PermissionInfo from "@/auth/PermissionInfo.js";
import getAvatarUrlOf from "@/utils/Avatar.js";
import {Clock, Document, Switch} from "@element-plus/icons-vue";

const props = defineProps({
    questionId: {
        type: String,
        required: true
    }
});

const dialogVisible = defineModel({required: true});

const loading = ref(false);
const versions = ref([]);
const mode = ref('list');
const selectedOldVersion = ref(null);
const selectedNewVersion = ref(null);
const oldQuestionData = ref(null);
const newQuestionData = ref(null);
const diffLoading = ref(false);

const canView = computed(() => PermissionInfo.hasPermission('questionVersion.view'));

const changeTypeMap = {
    CONTENT_CHANGE: { label: '内容变更', type: 'warning' },
    ANSWER_KEY_CHANGE: { label: '答案变更', type: 'danger' },
    MIXED_CHANGE: { label: '混合变更', type: 'danger' }
};

function getChangeTypeLabel(changeType) {
    return changeTypeMap[changeType]?.label || changeType || '初始版本';
}

function getChangeTypeTagType(changeType) {
    return changeTypeMap[changeType]?.type || 'info';
}

function formatTime(time) {
    if (!time) return '';
    if (typeof time === 'string') return time.replace('T', ' ').substring(0, 19);
    return time;
}

function onDialogOpen() {
    if (canView.value) {
        loadVersions();
    }
}

function onDialogClose() {
    versions.value = [];
    mode.value = 'list';
    selectedOldVersion.value = null;
    selectedNewVersion.value = null;
    oldQuestionData.value = null;
    newQuestionData.value = null;
}

function loadVersions() {
    loading.value = true;
    QuestionCache.getVersionHistory(props.questionId).then((data) => {
        versions.value = data;
    }).catch((err) => {
        console.error('Failed to load version history:', err);
    }).finally(() => {
        loading.value = false;
    });
}

function selectForCompare(version, role) {
    if (role === 'old') {
        selectedOldVersion.value = version;
    } else {
        selectedNewVersion.value = version;
    }
}

function startCompare() {
    if (!selectedOldVersion.value || !selectedNewVersion.value) return;
    mode.value = 'diff';
    diffLoading.value = true;

    const promises = [
        QuestionCache.getVersionQuestionData(selectedOldVersion.value.questionId),
        QuestionCache.getVersionQuestionData(selectedNewVersion.value.questionId)
    ];

    Promise.all(promises).then(([oldData, newData]) => {
        oldQuestionData.value = oldData;
        newQuestionData.value = newData;
    }).catch((err) => {
        console.error('Failed to load version data:', err);
    }).finally(() => {
        diffLoading.value = false;
    });
}

function backToList() {
    mode.value = 'list';
    selectedOldVersion.value = null;
    selectedNewVersion.value = null;
    oldQuestionData.value = null;
    newQuestionData.value = null;
}

function toggleVersionSelection(version) {
    if (!selectedOldVersion.value) {
        selectedOldVersion.value = version;
    } else if (!selectedNewVersion.value) {
        if (version.questionId === selectedOldVersion.value.questionId) {
            selectedOldVersion.value = null;
            return;
        }
        selectedNewVersion.value = version;
    } else {
        selectedOldVersion.value = null;
        selectedNewVersion.value = null;
    }
}

function isVersionSelected(version) {
    return (selectedOldVersion.value && selectedOldVersion.value.questionId === version.questionId) ||
           (selectedNewVersion.value && selectedNewVersion.value.questionId === version.questionId);
}

function getVersionSelectionLabel(version) {
    if (selectedOldVersion.value && selectedOldVersion.value.questionId === version.questionId) return '旧版';
    if (selectedNewVersion.value && selectedNewVersion.value.questionId === version.questionId) return '新版';
    return '';
}
</script>

<template>
    <el-dialog v-model="dialogVisible"
               width="720"
               @open="onDialogOpen"
               @close="onDialogClose"
               align-center
               draggable
               append-to-body
               :show-close="true"
               destroy-on-close>
        <template #header>
            <div style="display: flex;align-items: center;gap: 8px">
                <el-icon><Clock/></el-icon>
                <span>版本历史</span>
                <el-tag v-if="versions.length > 0" size="small" type="info">
                    共 {{ versions.length }} 个版本
                </el-tag>
            </div>
        </template>

        <div v-if="!canView" style="text-align: center;padding: 32px;">
            <el-text type="info">无权限查看版本历史</el-text>
        </div>

        <div v-else v-loading="loading" style="min-height: 200px;">
            <!-- Version List Mode -->
            <template v-if="mode === 'list'">
                <div v-if="versions.length === 0 && !loading" style="text-align: center;padding: 32px;">
                    <el-text type="info">暂无版本历史记录</el-text>
                </div>

                <div v-if="selectedOldVersion" class="compare-hint">
                    <el-text type="info" size="small">
                        已选旧版: V{{ selectedOldVersion.versionNumber }}
                        <template v-if="selectedNewVersion">
                            &nbsp;|&nbsp;已选新版: V{{ selectedNewVersion.versionNumber }}
                        </template>
                        <template v-else>
                            &nbsp;|&nbsp;请点击选择新版
                        </template>
                    </el-text>
                    <div style="display: flex;gap: 8px;margin-left: auto;">
                        <el-button v-if="selectedOldVersion && selectedNewVersion"
                                   type="primary" size="small" @click="startCompare"
                                   :icon="Switch">
                            对比差异
                        </el-button>
                        <el-button v-if="selectedOldVersion" size="small" @click="selectedOldVersion = null; selectedNewVersion = null">
                            取消选择
                        </el-button>
                    </div>
                </div>

                <div class="version-list">
                    <div v-for="(version, idx) in versions" :key="version.questionId"
                         class="version-item"
                         :class="{
                             'version-selected': isVersionSelected(version),
                             'version-active': version.versionStatus === 'ACTIVE'
                         }"
                         @click="toggleVersionSelection(version)">
                        <div class="version-indicator">
                            <div class="version-dot"
                                 :style="{background: version.versionStatus === 'ACTIVE' ? 'var(--el-color-primary)' : 'var(--el-color-info)'}">
                            </div>
                            <div v-if="idx < versions.length - 1" class="version-line"></div>
                        </div>
                        <div class="version-content">
                            <div class="version-header">
                                <div class="version-title-row">
                                    <el-text tag="b">V{{ version.versionNumber }}</el-text>
                                    <el-tag size="small" :type="version.versionStatus === 'ACTIVE' ? 'success' : 'info'">
                                        {{ version.versionStatus === 'ACTIVE' ? '当前' : '归档' }}
                                    </el-tag>
                                    <el-tag v-if="version.changeType" size="small" :type="getChangeTypeTagType(version.changeType)">
                                        {{ getChangeTypeLabel(version.changeType) }}
                                    </el-tag>
                                    <el-tag v-if="getVersionSelectionLabel(version)" size="small" type="primary">
                                        {{ getVersionSelectionLabel(version) }}
                                    </el-tag>
                                </div>
                                <div class="version-meta">
                                    <el-text type="info" size="small">
                                        {{ formatTime(version.lastModifiedTime) }}
                                    </el-text>
                                    <template v-if="version.modifiedByQq">
                                        <el-text type="info" size="small">&nbsp;|&nbsp;</el-text>
                                        <el-button link size="small" style="padding: 0;">
                                            <el-avatar :size="14" :src="getAvatarUrlOf(version.modifiedByQq)" style="margin-right: 2px;"/>
                                            <el-text size="small">{{ version.modifiedByQq }}</el-text>
                                        </el-button>
                                    </template>
                                </div>
                            </div>
                            <div v-if="version.contentPreview" class="version-preview">
                                <el-text size="small" type="info" class="version-preview-text">
                                    {{ version.contentPreview }}
                                </el-text>
                            </div>
                            <div v-if="version.changeDescription" class="version-description">
                                <el-text size="small">{{ version.changeDescription }}</el-text>
                            </div>
                        </div>
                    </div>
                </div>
            </template>

            <!-- Diff Mode -->
            <template v-if="mode === 'diff'">
                <div class="diff-header">
                    <el-button @click="backToList" link type="info" style="margin-right: 12px;">
                        返回列表
                    </el-button>
                    <el-text>
                        <el-text tag="b">V{{ selectedOldVersion?.versionNumber }}</el-text>
                        <el-text type="info"> → </el-text>
                        <el-text tag="b">V{{ selectedNewVersion?.versionNumber }}</el-text>
                        对比
                    </el-text>
                </div>
                <div v-loading="diffLoading" style="min-height: 200px;">
                    <QuestionDiffViewer v-if="oldQuestionData && newQuestionData"
                                        :old-question="oldQuestionData"
                                        :new-question="newQuestionData"/>
                </div>
            </template>
        </div>

        <template #footer>
            <el-button @click="dialogVisible = false">关闭</el-button>
        </template>
    </el-dialog>
</template>

<style scoped>
.compare-hint {
    display: flex;
    align-items: center;
    padding: 8px 12px;
    margin-bottom: 12px;
    background: var(--el-fill-color-lighter);
    border-radius: 6px;
    border: 1px solid var(--el-border-color-lighter);
}

.version-list {
    display: flex;
    flex-direction: column;
}

.version-item {
    display: flex;
    gap: 12px;
    padding: 8px 12px;
    cursor: pointer;
    border-radius: 6px;
    transition: background 0.2s;
}

.version-item:hover {
    background: var(--el-fill-color-lighter);
}

.version-item.version-selected {
    background: rgba(64, 158, 255, 0.08);
    outline: 1px solid var(--el-color-primary-light-5);
}

.version-indicator {
    display: flex;
    flex-direction: column;
    align-items: center;
    width: 16px;
    flex-shrink: 0;
    padding-top: 6px;
}

.version-dot {
    width: 10px;
    height: 10px;
    border-radius: 5px;
    flex-shrink: 0;
}

.version-line {
    width: 2px;
    flex: 1;
    background: var(--el-border-color-lighter);
    margin-top: 4px;
}

.version-content {
    flex: 1;
    min-width: 0;
}

.version-header {
    display: flex;
    flex-direction: column;
    gap: 2px;
}

.version-title-row {
    display: flex;
    align-items: center;
    gap: 6px;
    flex-wrap: wrap;
}

.version-meta {
    display: flex;
    align-items: center;
    flex-wrap: wrap;
}

.version-preview {
    margin-top: 4px;
}

.version-preview-text {
    display: -webkit-box;
    -webkit-line-clamp: 2;
    -webkit-box-orient: vertical;
    overflow: hidden;
    text-overflow: ellipsis;
}

.version-description {
    margin-top: 2px;
}

.diff-header {
    display: flex;
    align-items: center;
    margin-bottom: 16px;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
