<script setup>
import {ref, watch, onMounted, onUnmounted} from 'vue';
import Editor from '@toast-ui/editor';
import 'katex/dist/katex.min.css';
import katex from 'katex';
import '@toast-ui/editor/dist/toastui-editor.css';
import UIMeta from "@/utils/UI_Meta.js";

const props = defineProps({
    modelValue: {
        type: String,
        default: ''
    },
    theme: {
        type: String,
        default: 'light'
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
            height: 'auto',
            theme: props.theme === 'dark' ? 'dark' : 'light',
            toolbarItems: [],
            plugins: [],
            markdown: {
                breaks: true,
                gfm: true,
                toc: true
            },
            customBlock: {
                latex: {
                    parser: {
                        match: /^\\(([\s\S]+?)\\)$/,
                        parse: (source) => {
                            const match = source.match(/^\\(([\s\S]+?)\\)$/);
                            return {
                                type: 'latex',
                                content: match ? match[1].trim() : ''
                            };
                        }
                    },
                    renderer: {
                        html: (node) => {
                            try {
                                return katex.renderToString(node.content, {
                                    throwOnError: false,
                                    displayMode: false
                                });
                            } catch (error) {
                                return `<span class="katex-error">${error.message}</span>`;
                            }
                        }
                    }
                }
            },
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
    <div class="toast-markdown-viewer">
        <div ref="viewerRef" class="toast-ui-editor-container"></div>
    </div>
</template>

<style scoped>
.toast-markdown-viewer {
    width: 100%;
    min-height: 50px;
    position: relative;
}

.toast-ui-editor-container {
    width: 100%;
    height: 100%;
}

/* 调整TOAST UI Editor的样式以适应我们的布局 */
:deep(.toastui-editor) {
    border: none !important;
    background: transparent !important;
}

:deep(.toastui-editor-toolbar) {
    display: none !important;
}

:deep(.toastui-editor-main) {
    min-height: 100px;
}

:deep(.toastui-editor-contents) {
    padding: 0 !important;
}

:deep(.toastui-editor-md-container) {
    display: none !important;
}

:deep(.toastui-editor-preview-container) {
    width: 100% !important;
    border-left: none !important;
}
</style>