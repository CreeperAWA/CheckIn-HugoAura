<script setup>
import {ref, watch, onMounted, onUnmounted, computed} from 'vue';
import Editor from '@toast-ui/editor';
import katexPlugin from '@techie_doubts/editor-plugin-katex';
import {KATEX_CONFIG} from '@/config/katex.js';
import '@/assets/styles/katex.css';
import 'katex/dist/katex.min.css';
import '@toast-ui/editor/dist/toastui-editor.css';

const props = defineProps({
    modelValue: {
        type: String,
        default: ''
    },
    theme: {
        type: String,
        default: 'light'
    },
    placeholder: {
        type: String,
        default: ''
    },
    width: {
        type: String,
        default: '100%'
    },
    maxHeight: {
        type: String,
        default: '100vh'
    }
});

const viewerRef = ref(null);
let viewerInstance = null;

onMounted(() => {
    if (viewerRef.value) {
        viewerInstance = Editor.factory({
            el: viewerRef.value,
            viewer: true,
            initialValue: props.modelValue || '',
            height: 'auto',
            theme: props.theme === 'dark' ? 'dark' : 'light',
            plugins: [[katexPlugin, KATEX_CONFIG]],
            usageStatistics: false
        });
    }
});

onUnmounted(() => {
    if (viewerInstance) {
        viewerInstance.destroy();
    }
});

watch(() => props.modelValue, () => {
    if (viewerInstance) {
        viewerInstance.setMarkdown(props.modelValue || '');
    }
}, { immediate: false });

watch(() => props.theme, () => {
    if (viewerInstance) {
        // Viewer 不支持 changeTheme，需要重新创建或忽略
        // 这里我们暂时不处理主题切换
    }
});
</script>

<template>
    <div class="frontend-markdown-viewer" :style="{ width: width, maxHeight: maxHeight }">
        <div ref="viewerRef" class="toast-ui-editor-container"></div>
    </div>
</template>

<style scoped>
.frontend-markdown-viewer {
    min-height: 50px;
    position: relative;
    overflow: auto;
    margin-top: 25px;
    box-sizing: border-box;
}

.toast-ui-editor-container {
    width: 100%;
    height: 100%;
    box-sizing: border-box;
}

:deep(.toastui-editor-contents) {
    padding: 20px !important;
    background: transparent !important;
    word-wrap: break-word !important;
    word-break: break-word !important;
    overflow-wrap: break-word !important;
    max-width: 100% !important;
}
</style>