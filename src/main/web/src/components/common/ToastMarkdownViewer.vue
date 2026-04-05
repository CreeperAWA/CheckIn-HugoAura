<script setup>
import {ref, watch, onMounted, onUnmounted, computed} from 'vue';
import Editor from '@toast-ui/editor';
import katexPlugin from '@techie_doubts/editor-plugin-katex';
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
let editorInstance = null;

onMounted(() => {
    if (viewerRef.value) {
        editorInstance = new Editor({
            el: viewerRef.value,
            initialValue: props.modelValue || '',
            previewStyle: 'vertical',
            initialEditType: 'wysiwyg',
            height: 'auto',
            theme: props.theme === 'dark' ? 'dark' : 'light',
            toolbarItems: [],
            plugins: [katexPlugin],
            hideModeSwitch: true,
            usageStatistics: false,
            readOnly: true
        });
    }
});

onUnmounted(() => {
    if (editorInstance) {
        editorInstance.destroy();
    }
});

watch(() => [props.modelValue, props.theme], () => {
    if (editorInstance) {
        editorInstance.setMarkdown(props.modelValue || '');
        editorInstance.changeTheme(props.theme === 'dark' ? 'dark' : 'light');
    }
}, { immediate: true });
</script>

<template>
    <div class="toast-markdown-viewer" :style="{ width: width, maxHeight: maxHeight }">
        <div ref="viewerRef" class="toast-ui-editor-container"></div>
    </div>
</template>

<style scoped>
.toast-markdown-viewer {
    min-height: 50px;
    position: relative;
    overflow: auto;
    margin-top: 25px;
}

.toast-ui-editor-container {
    width: 100%;
    height: 100%;
}

/* 调整TOAST UI Editor的样式以适应我们的布局 */
:deep(.toastui-editor) {
    border: none !important;
    background: #F0F5F5 !important;
}

:deep(.toastui-editor-toolbar) {
    display: none !important;
}

:deep(.toastui-editor-mode-switch) {
    display: none !important;
}

:deep(.toastui-editor-defaultUI-mode-switch) {
    display: none !important;
}

:deep(.toastui-editor-md-container) {
    display: none !important;
    background: #F0F5F5 !important;
}

:deep(.toastui-editor-markdown-mode) .toastui-editor-md-container {
    display: none !important;
}

:deep(.toastui-editor-preview-container) {
    width: 100% !important;
    border-left: none !important;
    background: #F0F5F5 !important;
}

:deep(.toastui-editor-contents) {
    padding: 20px !important;
    background: #F0F5F5 !important;
}

:deep(.toastui-editor-wysiwyg-mode) .toastui-editor-contents {
    padding: 20px !important;
    background: #F0F5F5 !important;
}

/* 确保预览模式下只显示预览内容 */
:deep(.toastui-editor-defaultUI-preview) {
    background: #F0F5F5 !important;
}

:deep(.toastui-editor-contents-wrapper) {
    background: #F0F5F5 !important;
}

:deep(.toastui-editor-main) {
    background: #F0F5F5 !important;
}

:deep(.toastui-editor-defaultUI) {
    background: #F0F5F5 !important;
}

/* 确保内容区域自适应高度 */
:deep(.toastui-editor-main) {
    min-height: 100px;
    max-height: 100%;
    overflow: auto;
}
</style>