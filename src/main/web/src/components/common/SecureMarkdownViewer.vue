<script setup>
import {ref, watch, onMounted, onUnmounted} from 'vue';
import {MdPreview} from 'md-editor-v3';
import {sanitizeHtml, checkHtmlSafety} from '@/utils/SecureHtmlSanitizer.js';
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
    },
    showSecurityIndicator: {
        type: Boolean,
        default: false
    }
});

const iframeRef = ref(null);
const previewRef = ref(null);
const renderedHtml = ref('');
const securityStatus = ref({ isSafe: true, threats: [], threatCount: 0 });
const isProcessing = ref(false);

// 当 MdPreview 解析 Markdown 为 HTML 后触发
const handleHtmlChanged = (html) => {
    // 检查原始 HTML 的安全性
    securityStatus.value = checkHtmlSafety(html);

    // 使用基于 DOMParser 的消毒器处理 HTML
    isProcessing.value = true;
    try {
        renderedHtml.value = sanitizeHtml(html);
    } catch (error) {
        console.error('Sanitization error:', error);
        // 出错时显示原始内容的安全转义版本
        renderedHtml.value = escapeHtmlOnly(html);
    } finally {
        isProcessing.value = false;
    }

    updateIframe();
};

/**
 * 紧急备用：纯 HTML 转义（当主消毒器失败时使用）
 */
function escapeHtmlOnly(html) {
    if (!html) return '';
    return html
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#x27;');
}

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
            --md-link-color: ${props.theme === 'dark' ? '#58a6ff' : '#0969da'};
            --md-accent-color: ${UIMeta.color.primary};
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
        h3 { font-size: 1.25em; }
        h4 { font-size: 1em; }
        h5 { font-size: .875em; }
        h6 { font-size: .85em; color: var(--md-blockquote-color); }
        p { margin-top: 0; margin-bottom: 16px; }

        /* 列表 */
        ul, ol { padding-left: 2em; margin-top: 0; margin-bottom: 16px; }
        li { margin-top: .25em; }
        ul ul, ul ol, ol ol, ol ul { margin-top: 0; margin-bottom: 0; }

        /* 引用 */
        blockquote { padding: 0 1em; color: var(--md-blockquote-color); border-left: .25em solid var(--md-border-color); margin: 0 0 16px 0; }
        blockquote > :first-child { margin-top: 0; }
        blockquote > :last-child { margin-bottom: 0; }

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
        table th { font-weight: 600; background-color: ${props.theme === 'dark' ? 'rgba(48, 54, 61, 0.8)' : '#f6f8fa'}; }

        /* 图片与媒体 */
        img { max-width: 100%; box-sizing: content-box; background-color: transparent; border-radius: 4px; }
        video, audio { max-width: 100%; }
        hr { height: .25em; padding: 0; margin: 24px 0; background-color: var(--md-border-color); border: 0; }

        /* 链接 */
        a {
            color: var(--md-link-color);
            text-decoration: none;
            background-color: transparent;
        }
        a:hover { text-decoration: underline; }
        a:active, a:hover { outline-width: 0; }

        /* 任务列表 */
        input[type="checkbox"] {
            margin-right: 0.5em;
            vertical-align: middle;
        }

        /* 定义列表 */
        dl { margin-top: 0; margin-bottom: 16px; }
        dt { margin-top: 16px; font-weight: 600; }
        dd { margin-left: 0; margin-bottom: 16px; }

        /* 上标下标 */
        sub, sup { font-size: 75%; line-height: 0; position: relative; vertical-align: baseline; }
        sub { bottom: -0.25em; }
        sup { top: -0.5em; }

        /* 标记 */
        mark { background-color: ${props.theme === 'dark' ? 'rgba(187, 128, 9, 0.3)' : '#fff8c5'}; color: inherit; padding: .2em; }

        /* 折叠内容 */
        details { margin-bottom: 16px; }
        summary { cursor: pointer; font-weight: 600; }

        /* XSS 转义内容样式 */
        .xss-escaped-content {
            display: block !important;
            background-color: rgba(255, 193, 7, 0.15) !important;
            border: 1px dashed #ffc107 !important;
            border-radius: 3px !important;
            padding: 8px 12px !important;
            font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace !important;
            font-size: 0.9em !important;
            color: #856404 !important;
            white-space: pre-wrap !important;
            word-wrap: break-word !important;
            margin: 8px 0 !important;
            overflow: auto !important;
        }

        .xss-escaped-attr {
            display: inline !important;
            background-color: rgba(220, 53, 69, 0.1) !important;
            border: 1px dashed #dc3545 !important;
            border-radius: 3px !important;
            padding: 1px 3px !important;
            font-family: ui-monospace, SFMono-Regular, monospace !important;
            font-size: 0.85em !important;
            color: #721c24 !important;
            word-break: break-all !important;
        }

        /* 数学公式样式 */
        math { display: inline; font-family: 'Cambria Math', 'Latin Modern Math', serif; }
        mrow { display: inline; }

        /* 打印样式 */
        @media print {
            body { font-size: 12pt; }
            pre { white-space: pre-wrap; word-wrap: break-word; }
        }

        /* 暗色主题调整 */
        ${props.theme === 'dark' ? `
        .xss-escaped-content {
            background-color: rgba(255, 193, 7, 0.2) !important;
            color: #ffe066 !important;
            border-color: #ffc107 !important;
        }
        .xss-escaped-attr {
            background-color: rgba(220, 53, 69, 0.2) !important;
            color: #f5c6cb !important;
            border-color: #dc3545 !important;
        }
        ` : ''}
    `;

    return `
        <!DOCTYPE html>
        <html>
        <head>
            <meta charset="UTF-8">
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <!-- 强化的 Content-Security-Policy -->
            <meta http-equiv="Content-Security-Policy" content="
                default-src 'none';
                style-src 'unsafe-inline';
                img-src * blob: data:;
                media-src * blob: data:;
                connect-src 'none';
                script-src 'none';
                object-src 'none';
                frame-src 'none';
                base-uri 'none';
                form-action 'none';
            ">
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
    const oldUrl = iframe.src;
    iframe.src = URL.createObjectURL(blob);

    // 清理旧的 blob URL
    if (oldUrl && oldUrl.startsWith('blob:')) {
        URL.revokeObjectURL(oldUrl);
    }

    iframe.onload = () => {
        try {
            const doc = iframe.contentWindow?.document;
            if (!doc) return;

            // 自动调整高度
            const resize = () => {
                if (iframe && doc.body) {
                    const height = Math.max(doc.body.scrollHeight + 20, 50);
                    iframe.style.height = height + 'px';
                }
            };
            resize();

            // 处理图片加载后的高度变化
            doc.querySelectorAll('img').forEach(img => {
                img.onload = resize;
                img.onerror = resize;
            });

            // 处理视频/音频加载
            doc.querySelectorAll('video, audio').forEach(media => {
                media.onload = resize;
                media.onloadedmetadata = resize;
            });
        } catch (e) {
            console.warn('Iframe resize error:', e);
        }
    };
};

watch(() => [props.modelValue, props.theme], () => {
    // modelValue 改变会触发 MdPreview 重新解析，进而触发 handleHtmlChanged
}, { immediate: true });

onMounted(() => {
    updateIframe();
});

// 组件卸载时清理
onUnmounted(() => {
    if (iframeRef.value && iframeRef.value.src?.startsWith('blob:')) {
        URL.revokeObjectURL(iframeRef.value.src);
    }
});

</script>

<template>
    <div class="secure-markdown-viewer">
        <!-- 安全状态指示器（可选） -->
        <div v-if="showSecurityIndicator && securityStatus.threatCount > 0" class="security-warning">
            <span class="warning-icon">⚠️</span>
            <span class="warning-text">
                检测到 {{ securityStatus.threatCount }} 个潜在安全风险，已安全处理
            </span>
        </div>

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
    position: relative;
}

.security-warning {
    background-color: rgba(255, 193, 7, 0.1);
    border: 1px solid #ffc107;
    border-radius: 4px;
    padding: 8px 12px;
    margin-bottom: 8px;
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 13px;
    color: #856404;
}

.security-warning .warning-icon {
    font-size: 14px;
}

.security-warning .warning-text {
    flex: 1;
}

/* 暗色主题 */
@media (prefers-color-scheme: dark) {
    .security-warning {
        background-color: rgba(255, 193, 7, 0.15);
        color: #ffe066;
        border-color: #ffc107;
    }
}
</style>
