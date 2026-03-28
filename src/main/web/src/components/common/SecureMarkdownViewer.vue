<script setup>
import {ref, watch, onMounted, computed} from 'vue';
import {MdPreview} from 'md-editor-v3';
import sanitizeHtml from '@/utils/Sanitize.js';
import UIMeta from "@/utils/UI_Meta.js";

const props = defineProps({
    modelValue: {
        type: String,
        default: ''
    },
    theme: {
        type: String,
        default: 'light'
    },
    previewTheme: {
        type: String,
        default: 'vuepress'
    }
});

const iframeRef = ref(null);
const previewRef = ref(null);
const renderedHtml = ref('');

// 当 MdPreview 解析 Markdown 为 HTML 后触发
const handleHtmlChanged = (html) => {
    // 此处的 html 已经是 MdPreview 生成的 HTML，我们对其进行增强消毒
    renderedHtml.value = sanitizeHtml(html);
    updateIframe();
};

// 构建 iframe 内部的 HTML 内容
const buildIframeContent = (htmlContent) => {
    const finalHtml = htmlContent;
    
    // 获取当前页面的部分样式以保持视觉一致性
    // 注意：由于 CSP sandbox，外部资源加载会受限
    const contentStyle = `
        :root {
            --md-theme-color: ${props.theme === 'dark' ? '#c9d1d9' : '#24292f'};
            --md-bg-color: transparent;
            --md-border-color: ${props.theme === 'dark' ? '#30363d' : '#d0d7de'};
            --md-code-bg-color: ${props.theme === 'dark' ? '#161b22' : '#f6f8fa'};
            --md-blockquote-color: ${props.theme === 'dark' ? '#8b949e' : '#57606a'};
        }
        body {
            margin: 0;
            padding: 0;
            font-family: "HarmonyOS Sans SC", system-ui, "微软雅黑", sans-serif;
            font-size: 16px;
            line-height: 1.6;
            color: var(--md-theme-color);
            background-color: var(--md-bg-color);
            -webkit-font-smoothing: antialiased;
            word-break: break-all;
        }
        .md-editor-preview, .markdown-body {
            box-sizing: border-box;
            min-width: 200px;
            max-width: 100%;
            margin: 0;
            padding: 0; 
            word-wrap: break-word;
        }
        
        /* 继承 md-editor-v3 的核心排版 */
        h1, h2, h3, h4, h5, h6 { margin-top: 24px; margin-bottom: 16px; font-weight: 600; line-height: 1.25; }
        h1 { font-size: 2em; padding-bottom: .3em; border-bottom: 1px solid var(--md-border-color); }
        h2 { font-size: 1.5em; padding-bottom: .3em; border-bottom: 1px solid var(--md-border-color); }
        p { margin-top: 0; margin-bottom: 16px; }
        
        /* 列表 */
        ul, ol { padding-left: 2em; margin-top: 0; margin-bottom: 16px; }
        li { margin-top: .25em; }
        
        /* 引用 */
        blockquote { padding: 0 1em; color: var(--md-blockquote-color); border-left: .25em solid var(--md-border-color); margin: 0 0 16px 0; }
        
        /* 代码 */
        pre { 
            background-color: var(--md-code-bg-color); 
            padding: 16px; 
            border-radius: 6px; 
            overflow: auto; 
            font-size: 85%;
            line-height: 1.45;
            margin-bottom: 16px;
        }
        code { 
            font-family: ui-monospace, SFMono-Regular, SF Mono, Menlo, Consolas, Liberation Mono, monospace; 
            background-color: var(--md-code-bg-color);
            padding: .2em .4em;
            border-radius: 3px;
            font-size: 85%;
        }
        pre code { padding: 0; background-color: transparent; }
        
        /* 表格 */
        table { border-spacing: 0; border-collapse: collapse; margin-top: 0; margin-bottom: 16px; width: 100%; display: block; overflow: auto; }
        table th, table td { padding: 6px 13px; border: 1px solid var(--md-border-color); }
        table tr { background-color: transparent; border-top: 1px solid var(--md-border-color); }
        table tr:nth-child(2n) { background-color: ${props.theme === 'dark' ? 'rgba(48, 54, 61, 0.5)' : '#f6f8fa'}; }
        
        /* 图片与媒体 */
        img { max-width: 100%; box-sizing: content-box; background-color: transparent; border-radius: 4px; }
        hr { height: .25em; padding: 0; margin: 24px 0; background-color: var(--md-border-color); border: 0; }
    `;

    return `
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <!-- 强化的 Content-Security-Policy -->
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'; img-src *; media-src *;">
            <style>${contentStyle}</style>
        </head>
        <body class="md-editor-preview ${props.previewTheme}-theme">
            <div id="render-target">${finalHtml}</div>
        </body>
        </html>
    `;
};

const updateIframe = () => {
    if (!iframeRef.value || !renderedHtml.value) return;
    
    const iframe = iframeRef.value;
    const content = buildIframeContent(renderedHtml.value);
    
    // 动态更新 iframe 内容
    const blob = new Blob([content], { type: 'text/html' });
    iframe.src = URL.createObjectURL(blob);

    iframe.onload = () => {
        const doc = iframe.contentWindow.document;
        // 自动调整高度
        const resize = () => {
            if (iframe) {
                iframe.style.height = (doc.body.scrollHeight + 20) + 'px';
            }
        };
        resize();
        // 处理图片加载后的高度变化
        doc.querySelectorAll('img').forEach(img => {
            img.onload = resize;
        });
    };
};

watch(() => [props.modelValue, props.theme], () => {
    // modelValue 改变会触发 MdPreview 重新解析，进而触发 handleHtmlChanged
});

onMounted(updateIframe);

</script>

<template>
    <div class="secure-markdown-viewer">
        <!-- 隐藏的渲染引擎，利用 md-editor-v3 的解析能力 -->
        <md-preview 
            ref="previewRef"
            style="position: absolute; top: -9999px; left: -9999px; visibility: hidden; pointer-events: none;" 
            :model-value="modelValue" 
            :preview-theme="previewTheme"
            @onHtmlChanged="handleHtmlChanged"
        />
        
        <!-- 沙箱展示层 -->
        <iframe
            ref="iframeRef"
            title="Secure Markdown Preview"
            sandbox="allow-popups"
            style="width: 100%; border: none; overflow: hidden; transition: height 0.2s; background: transparent;"
        ></iframe>
    </div>
</template>

<style scoped>
.secure-markdown-viewer {
    width: 100%;
    min-height: 50px;
}
</style>
