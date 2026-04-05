<script setup>
import { ref, watch, onMounted, onBeforeUnmount, defineProps, defineEmits } from 'vue';
import Editor from '@toast-ui/editor';
import katexPlugin from '@techie_doubts/editor-plugin-katex';
import {KATEX_CONFIG} from '@/config/katex.js';
import 'katex/dist/katex.min.css';
import '@toast-ui/editor/dist/toastui-editor.css';

const props = defineProps({
  modelValue: {
    type: String,
    default: ''
  },
  placeholder: {
    type: String,
    default: '请输入内容'
  },
  theme: {
    type: String,
    default: 'light'
  },
  preview: {
    type: Boolean,
    default: true
  },
  toolbarsExclude: {
    type: Array,
    default: () => []
  },
  showToolbarName: {
    type: Boolean,
    default: false
  },
  footers: {
    type: Array,
    default: () => []
  }
});

const emit = defineEmits(['update:modelValue']);

const editorRef = ref(null);
let editorInstance = null;

onMounted(() => {
  if (editorRef.value) {
    editorInstance = new Editor({
      el: editorRef.value,
      initialValue: props.modelValue,
      placeholder: props.placeholder,
      previewStyle: props.preview ? 'vertical' : 'tab',
      height: '50dvh',
      minHeight: '450px',
      theme: props.theme === 'dark' ? 'dark' : 'light',
      toolbarItems: [
        ['heading', 'bold', 'italic', 'strike'],
        ['hr', 'quote'],
        ['ul', 'ol', 'task'],
        ['table', 'image', 'link'],
        ['code', 'codeblock'],
        ['scrollSync']
      ],
      plugins: [[katexPlugin, KATEX_CONFIG]],
      hooks: {
        addImageBlobHook: (blob, callback) => {
          // 暂时不处理图片上传
          return false;
        }
      }
    });

    editorInstance.on('change', () => {
      emit('update:modelValue', editorInstance.getMarkdown());
    });
  }
});

onBeforeUnmount(() => {
  if (editorInstance) {
    editorInstance.destroy();
  }
});

watch(() => props.modelValue, (newValue) => {
  if (editorInstance && newValue !== editorInstance.getMarkdown()) {
    editorInstance.setMarkdown(newValue);
  }
});

watch(() => props.theme, (newValue) => {
  if (editorInstance) {
    editorInstance.changeTheme(newValue === 'dark' ? 'dark' : 'light');
  }
});
</script>

<template>
  <div ref="editorRef" class="frontend-editor"></div>
</template>

<style scoped>
.frontend-editor {
  width: 100%;
  height: 100%;
  margin-top: 25px;
  box-sizing: border-box;
}

/* 调整TOAST UI Editor的样式 */
:deep(.toastui-editor) {
  background: transparent !important;
  border: none !important;
}

:deep(.toastui-editor-contents) {
  padding: 20px !important;
  background: transparent !important;
  word-wrap: break-word !important;
  word-break: break-word !important;
  overflow-wrap: break-word !important;
  max-width: 100% !important;
}

:deep(.toastui-editor-md-container) {
  padding: 20px !important;
  background: transparent !important;
  word-wrap: break-word !important;
  word-break: break-word !important;
  overflow-wrap: break-word !important;
  max-width: 100% !important;
}

:deep(.toastui-editor-preview-container) {
  padding: 20px !important;
  background: transparent !important;
  word-wrap: break-word !important;
  word-break: break-word !important;
  overflow-wrap: break-word !important;
  max-width: 100% !important;
  border-left: none !important;
}

:deep(.toastui-editor-main) {
  background: transparent !important;
  word-wrap: break-word !important;
  word-break: break-word !important;
  overflow-wrap: break-word !important;
  max-width: 100% !important;
}

:deep(.toastui-editor-defaultUI) {
  background: transparent !important;
}

:deep(.toastui-editor-contents-wrapper) {
  background: transparent !important;
}
</style>