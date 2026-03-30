<script setup>
import { ref, watch, onMounted, onBeforeUnmount, defineProps, defineEmits } from 'vue';
import Editor from '@toast-ui/editor';
import 'katex/dist/katex.min.css';
import katex from 'katex';
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
      plugins: [],
      markdown: {
        breaks: true,
        gfm: true,
        toc: true
      },
      hooks: {
        addImageBlobHook: (blob, callback) => {
          // 暂时不处理图片上传
          return false;
        },
        afterPreviewRender: (html) => {
          // 处理行内数学公式: \(...\)
          return html.replace(/\\\(([^\\)]+)\\\)/g, (match, formula) => {
            try {
              return katex.renderToString(formula, {
                throwOnError: false,
                displayMode: false
              });
            } catch (error) {
              return `<span class="katex-error">${error.message}</span>`;
            }
          });
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
  <div ref="editorRef" class="toast-editor"></div>
</template>

<style scoped>
.toast-editor {
  width: 100%;
  height: 100%;
}
</style>