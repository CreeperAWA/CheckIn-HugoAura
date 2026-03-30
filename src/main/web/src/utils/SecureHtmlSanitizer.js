/**
 * 基于 DOMParser 的 XSS 安全 HTML 消毒器
 * 
 * 核心设计理念：
 * 1. 使用原生 DOMParser 解析 HTML，确保不执行任何脚本
 * 2. 递归遍历 DOM 树，识别并处理危险元素和属性
 * 3. 将危险内容转义为纯文本展示，而非直接删除，保留原始视觉呈现
 * 4. 白名单机制控制允许的标签和属性
 * 5. 深度防御：多层检查确保 XSS 无法绕过
 */

// ============ 配置常量 ============

/**
 * 允许的标签白名单
 * 包含 Markdown 常用标签和 MathML/SVG 支持
 */
const ALLOWED_TAGS = new Set([
    // 基础排版
    'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'br', 'hr',
    // 列表
    'ul', 'ol', 'li', 'dl', 'dt', 'dd',
    // 引用和代码
    'blockquote', 'pre', 'code',
    // 表格
    'table', 'thead', 'tbody', 'tfoot', 'tr', 'th', 'td', 'caption', 'col', 'colgroup',
    // 文本格式
    'b', 'strong', 'i', 'em', 'u', 's', 'del', 'ins', 'sub', 'sup', 'mark', 'small', 'span', 'font',
    // 链接和媒体
    'a', 'img', 'video', 'audio', 'source', 'track',
    // 布局
    'div', 'section', 'article', 'aside', 'header', 'footer', 'main', 'nav', 'figure', 'figcaption',
    // 交互元素
    'details', 'summary', 'button', 'input', 'label', 'textarea', 'select', 'option',
    // 头部元素
    'meta', 'title',
    // 其他
    'abbr', 'address', 'bdi', 'bdo', 'cite', 'dfn', 'kbd', 'q', 'rp', 'rt', 'ruby', 'samp', 'time', 'var', 'wbr',
    // MathML
    'math', 'semantics', 'mrow', 'mstyle', 'msub', 'msup', 'mover', 'munder', 'munderover',
    'mfrac', 'msqrt', 'mroot', 'mi', 'mn', 'mo', 'mtext', 'mspace', 'ms', 'annotation', 'annotation-xml',
    'menclose', 'mfenced', 'mpadded', 'mphantom', 'msubsup', 'mtable', 'mtd', 'mtr', 'mth',
    // SVG
    'svg', 'path', 'g', 'line', 'rect', 'circle', 'ellipse', 'polyline', 'polygon', 'use',
    'clippath', 'defs', 'marker', 'mask', 'pattern', 'switch', 'symbol', 'text', 'textpath', 'tspan',
    'animate', 'animatemotion', 'animatetransform', 'desc', 'filter', 'foreignobject', 'image',
    'lineargradient', 'radialgradient', 'stop', 'view'
]);

/**
 * 危险标签列表 - 这些标签会被转义为文本显示
 */
const DANGEROUS_TAGS = new Set([
    'script', 'style', 'iframe', 'frame', 'frameset', 'object', 'embed', 'applet',
    'form', 'input', 'textarea', 'select', 'button', 'option', 'optgroup',
    'base', 'link', 'head', 'html', 'body',
    'noscript', 'template', 'slot', 'portal', 'xmp', 'plaintext'
]);

/**
 * 允许的属性白名单
 */
const ALLOWED_ATTRS = new Set([
    // 通用属性
    'id', 'class', 'style', 'title', 'dir', 'lang', 'role', 'tabindex',
    'data-*', // 允许 data- 自定义属性
    // 链接属性
    'href', 'target', 'rel', 'download',
    // 媒体属性
    'src', 'alt', 'width', 'height', 'loading',
    'controls', 'autoplay', 'loop', 'muted', 'poster', 'preload',
    'type', 'media', 'sizes', 'srcset',
    // 表格属性
    'colspan', 'rowspan', 'headers', 'scope', 'align', 'valign', 'border', 'cellpadding', 'cellspacing',
    // 输入属性
    'checked', 'disabled', 'readonly', 'required', 'selected', 'value', 'name', 'placeholder', 'maxlength', 'min', 'max', 'step', 'pattern',
    // 头部元素属性
    'charset', 'name', 'content', 'http-equiv',
    // 其他
    'cite', 'datetime', 'open', 'reversed', 'start', 'type', 'wrap',
    // SVG 属性
    'viewbox', 'd', 'fill', 'stroke', 'stroke-width', 'stroke-linecap', 'stroke-linejoin',
    'points', 'cx', 'cy', 'r', 'rx', 'ry', 'x', 'y', 'x1', 'y1', 'x2', 'y2',
    'transform', 'opacity', 'fill-opacity', 'stroke-opacity', 'stroke-dasharray', 'stroke-dashoffset',
    'clip-path', 'clip-rule', 'fill-rule', 'filter', 'mask', 'maskunits', 'maskcontentunits',
    'gradientunits', 'gradienttransform', 'spreadmethod', 'offset', 'stop-color', 'stop-opacity',
    'text-anchor', 'dominant-baseline', 'font-family', 'font-size', 'font-weight',
    'xmlns', 'xmlns:xlink', 'xlink:href', 'xml:lang', 'xml:space',
    // MathML 属性
    'mathvariant', 'mathsize', 'mathcolor', 'mathbackground', 'displaystyle', 'scriptlevel',
    'accent', 'accentunder', 'align', 'columnalign', 'columnlines', 'columnspacing',
    'frame', 'framespacing', 'rowalign', 'rowlines', 'rowspacing', 'width', 'height',
    'lspace', 'rspace', 'stretchy', 'symmetric', 'maxsize', 'minsize', 'largeop', 'movablelimits',
    'notation', 'selection', 'separator', 'separators', 'close', 'open', 'linebreak',
    // ARIA 属性
    'aria-*'
]);

/**
 * 危险属性模式 - 事件处理器和危险 URL 方案
 */
const DANGEROUS_ATTR_PATTERNS = [
    // 事件处理器: onerror, onclick, onload 等
    /^on[a-z]+$/i,
    // 危险 URL 方案
    /^javascript:/i,
    /^data:text\/html/i,
    /^data:image\/svg\+xml/i,
    /^vbscript:/i,
    /^mocha:/i,
    /^livescript:/i,
    // 表达式和绑定
    /expression\s*\(/i,
    /url\s*\(/i,
    /behavior\s*:/i,
    /-moz-binding/i
];

/**
 * 安全的 URL 协议白名单
 */
const SAFE_URL_SCHEMES = new Set([
    'http:', 'https:', 'ftp:', 'ftps:', 'mailto:', 'tel:', 'sms:', 'callto:', 'wtai:', 'geo:', 'maps:'
]);

/**
 * 需要检查 URL 的属性
 */
const URL_ATTRS = new Set([
    'href', 'src', 'action', 'formaction', 'poster', 'cite', 'longdesc', 'profile', 'usemap', 'codebase', 'data'
]);

// ============ 工具函数 ============

/**
 * 检查属性名是否允许
 * @param {string} attrName - 属性名
 * @returns {boolean}
 */
function isAllowedAttr(attrName) {
    const lowerName = attrName.toLowerCase();

    // 检查是否是 data-* 或 aria-* 属性
    if (lowerName.startsWith('data-') || lowerName.startsWith('aria-')) {
        return true;
    }

    // 检查是否在白名单中
    if (ALLOWED_ATTRS.has(lowerName)) {
        return true;
    }

    // 检查是否匹配 MathML/SVG 命名空间属性
    if (lowerName.includes(':')) {
        const localName = lowerName.split(':').pop();
        return ALLOWED_ATTRS.has(localName);
    }

    return false;
}

/**
 * 检查属性值是否危险
 * @param {string} attrName - 属性名
 * @param {string} attrValue - 属性值
 * @returns {boolean}
 */
function isDangerousAttrValue(attrName, attrValue) {
    if (!attrValue || typeof attrValue !== 'string') {
        return false;
    }

    const lowerValue = attrValue.toLowerCase().trim();

    // 检查事件处理器
    if (/^on[a-z]+$/i.test(attrName)) {
        return true;
    }

    // 检查危险 URL 方案
    for (const pattern of DANGEROUS_ATTR_PATTERNS) {
        if (pattern.test(lowerValue)) {
            return true;
        }
    }

    // 检查 URL 属性中的危险协议
    if (URL_ATTRS.has(attrName.toLowerCase())) {
        try {
            // 解码 URL 以检测编码绕过
            const decodedValue = decodeURIComponent(lowerValue);

            // 检查各种编码形式的 javascript:
            if (/^\s*javascript:/i.test(decodedValue) ||
                /^\s*javascript:/i.test(lowerValue) ||
                /^\s*&#x6a;&#x61;&#x76;&#x61;&#x73;&#x63;&#x72;&#x69;&#x70;&#x74;:/i.test(lowerValue) ||
                /^\s*&#106;&#097;&#118;&#097;&#115;&#099;&#114;&#105;&#112;&#116;:/i.test(lowerValue)) {
                return true;
            }

            // 检查 data:text/html 和 data:image/svg+xml (可能包含脚本)
            if (/^\s*data:text\/html/i.test(decodedValue)) {
                return true;
            }

            // 检查其他危险协议
            const dangerousSchemes = ['vbscript:', 'mocha:', 'livescript:', 'about:', 'chrome:', 'resource:'];
            for (const scheme of dangerousSchemes) {
                if (decodedValue.startsWith(scheme) || lowerValue.startsWith(scheme)) {
                    return true;
                }
            }
        } catch (e) {
            // 解码失败，可能是畸形 URL，视为危险
            return true;
        }
    }

    // 检查 style 属性中的危险内容
    if (attrName.toLowerCase() === 'style') {
        const dangerousStylePatterns = [
            /expression\s*\(/i,
            /behavior\s*:/i,
            /-moz-binding/i,
            /url\s*\(\s*['"\s]*javascript:/i,
            /url\s*\(\s*['"\s]*data:/i,
            /@import/i,
            /binding/i
        ];
        for (const pattern of dangerousStylePatterns) {
            if (pattern.test(lowerValue)) {
                return true;
            }
        }
    }

    return false;
}

/**
 * 将危险字符串转义为 HTML 实体
 * 保留视觉呈现但使其无法执行
 * @param {string} str - 原始字符串
 * @returns {string}
 */
function escapeHtmlEntities(str) {
    if (!str) return '';
    return str
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        .replace(/'/g, '&#x27;')
        .replace(/\//g, '&#x2F;')
        .replace(/\n/g, '&#10;')
        .replace(/\r/g, '&#13;')
        .replace(/\t/g, '&#9;')
        .replace(/\s\s+/g, (match) => {
            // 保留连续空格
            return match.split('').map(() => '&nbsp;').join('');
        });
}

/**
 * 创建转义后的文本节点展示
 * 将危险内容包装在视觉醒目的代码块中展示
 * @param {string} content - 原始内容
 * @param {string} tagName - 原始标签名
 * @returns {string}
 */
function createEscapedDisplay(content, tagName) {
    const escaped = escapeHtmlEntities(content);
    return `<pre class="xss-escaped-content" style="
        background-color: rgba(255, 193, 7, 0.15);
        border: 1px dashed #ffc107;
        border-radius: 3px;
        padding: 8px 12px;
        font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;
        font-size: 0.9em;
        color: #856404;
        display: block;
        white-space: pre-wrap;
        word-wrap: break-word;
        margin: 8px 0;
        overflow: auto;
    " title="潜在危险内容已被安全转义">&lt;${tagName}&gt;${escaped}&lt;/${tagName}&gt;</pre>`;
}

/**
 * 创建转义后的属性展示
 * @param {string} attrName - 属性名
 * @param {string} attrValue - 属性值
 * @returns {string}
 */
function createEscapedAttrDisplay(attrName, attrValue) {
    const escapedValue = escapeHtmlEntities(attrValue);
    return `<span class="xss-escaped-attr" style="
        background-color: rgba(220, 53, 69, 0.1);
        border: 1px dashed #dc3545;
        border-radius: 3px;
        padding: 1px 3px;
        font-family: ui-monospace, SFMono-Regular, monospace;
        font-size: 0.85em;
        color: #721c24;
    " title="危险属性已被安全转义">${attrName}=&quot;${escapedValue}&quot;</span>`;
}

// ============ 核心消毒函数 ============

/**
 * 使用 DOMParser 解析 HTML
 * @param {string} html - HTML 字符串
 * @returns {Document}
 */
function parseHtml(html) {
    const parser = new DOMParser();
    const doc = parser.parseFromString(html, 'text/html');

    // 检查解析错误
    const parserError = doc.querySelector('parsererror');
    if (parserError) {
        // 解析失败，返回包含原始文本的文档
        const fallbackDoc = parser.parseFromString(`<div></div>`, 'text/html');
        fallbackDoc.body.textContent = html;
        return fallbackDoc;
    }

    return doc;
}

/**
 * 递归处理 DOM 节点
 * @param {Node} node - 当前节点
 * @param {Document} doc - 文档对象
 * @returns {Node|null} 处理后的节点或 null
 */
function sanitizeNode(node, doc) {
    // 处理文本节点 - 直接返回
    if (node.nodeType === Node.TEXT_NODE) {
        return doc.createTextNode(node.textContent);
    }

    // 处理注释节点 - 可以选择保留或删除
    if (node.nodeType === Node.COMMENT_NODE) {
        // 保留注释，但确保内容安全
        return doc.createComment(node.textContent.replace(/-->/g, '―→'));
    }

    // 只处理元素节点
    if (node.nodeType !== Node.ELEMENT_NODE) {
        return null;
    }

    const tagName = node.tagName.toLowerCase();

    // 处理危险标签 - 转义为文本展示
    if (DANGEROUS_TAGS.has(tagName)) {
        // 手动构建完整的标签，确保内容完整且不重复
        const startTag = `<${tagName}${Array.from(node.attributes).map(attr => ` ${attr.name}="${attr.value}"`).join('')}>`;
        const endTag = `</${tagName}>`;
        const innerContent = node.textContent || '';
        const content = startTag + innerContent + endTag;
        
        // 创建 pre 元素来保留格式
        const escapedWrapper = doc.createElement('pre');
        escapedWrapper.className = 'xss-escaped-content';
        escapedWrapper.style.cssText = `
            background-color: rgba(255, 193, 7, 0.15);
            border: 1px dashed #ffc107;
            border-radius: 3px;
            padding: 8px 12px;
            font-family: ui-monospace, SFMono-Regular, 'SF Mono', Menlo, Consolas, monospace;
            font-size: 0.9em;
            color: #856404;
            display: block;
            white-space: pre-wrap;
            word-wrap: break-word;
            margin: 8px 0;
            overflow: auto;
        `;
        escapedWrapper.title = '潜在危险内容已被安全转义';
        // 使用 textContent 避免HTML实体被解析，确保标签完整显示
        escapedWrapper.textContent = content;
        return escapedWrapper;
    }

    // 处理未知标签（不在白名单中的标签）
    if (!ALLOWED_TAGS.has(tagName)) {
        // 将未知标签的内容展开，保留子元素
        const fragment = doc.createDocumentFragment();
        const children = Array.from(node.childNodes);
        for (const child of children) {
            const sanitizedChild = sanitizeNode(child, doc);
            if (sanitizedChild) {
                fragment.appendChild(sanitizedChild);
            }
        }
        return fragment;
    }

    // 创建安全的元素
    let safeElement;
    try {
        safeElement = doc.createElement(tagName);
    } catch (e) {
        // 如果创建失败，使用 div 替代
        safeElement = doc.createElement('div');
    }

    // 复制安全的属性
    const attributes = Array.from(node.attributes);
    for (const attr of attributes) {
        const attrName = attr.name;
        const attrValue = attr.value;

        // 跳过不允许的属性
        if (!isAllowedAttr(attrName)) {
            continue;
        }

        // 检查属性值是否危险
        if (isDangerousAttrValue(attrName, attrValue)) {
            // 将危险属性转义后作为文本展示
            const escapedAttr = doc.createElement('span');
            escapedAttr.innerHTML = createEscapedAttrDisplay(attrName, attrValue);
            safeElement.appendChild(escapedAttr);
            continue;
        }

        // 特殊处理 href 和 src 属性
        if (URL_ATTRS.has(attrName.toLowerCase())) {
            try {
                const url = new URL(attrValue, window.location.href);
                // 只允许安全的协议
                if (!SAFE_URL_SCHEMES.has(url.protocol.toLowerCase())) {
                    continue;
                }
            } catch (e) {
                // 相对 URL 或无效 URL，检查是否以危险协议开头
                const lowerValue = attrValue.toLowerCase().trim();
                if (/^[a-z][a-z0-9+.-]*:/i.test(lowerValue)) {
                    // 是绝对 URL 但不是 http/https，跳过
                    if (!lowerValue.startsWith('http:') &&
                        !lowerValue.startsWith('https:') &&
                        !lowerValue.startsWith('mailto:') &&
                        !lowerValue.startsWith('tel:')) {
                        continue;
                    }
                }
            }
        }

        // 设置安全属性
        try {
            safeElement.setAttribute(attrName, attrValue);
        } catch (e) {
            // 某些属性可能无法设置，忽略错误
        }
    }

    // 递归处理子节点
    const children = Array.from(node.childNodes);
    for (const child of children) {
        const sanitizedChild = sanitizeNode(child, doc);
        if (sanitizedChild) {
            safeElement.appendChild(sanitizedChild);
        }
    }

    return safeElement;
}

/**
 * 后处理：清理可能的危险残留
 * @param {string} html - HTML 字符串
 * @returns {string}
 */
function postProcess(html) {
    return html
        // 移除 XML 声明
        .replace(/<\?xml[^?]*\?>/gi, '')
        // 保留 DOCTYPE
        // .replace(/<!DOCTYPE[^>]*>/gi, '')
        // 移除 CDATA
        .replace(/<!\[CDATA\[([\s\S]*?)\]\]>/g, '$1')
        // 移除条件注释
        .replace(/<!--\[if[^\]]*\]>.*?<!\[endif\]-->/gis, '')
        // 保留普通注释
        // .replace(/<!--\[.*?\]-->/gs, '')
        // 移除可能的事件处理器（双重保险）
        .replace(/\s*on\w+\s*=\s*["'][^"']*["']/gi, '')
        // 移除 javascript: 伪协议（双重保险）
        .replace(/javascript:/gi, 'javascript&#58;')
        // 移除 data:text/html
        .replace(/data:text\/html/gi, 'data&#58;text/html')
        // 移除 SVG 动画事件
        .replace(/\s*onbegin\s*=\s*["'][^"']*["']/gi, '')
        .replace(/\s*onend\s*=\s*["']*["']/gi, '')
        .replace(/\s*onrepeat\s*=\s*["'][^"']*["']/gi, '')
        // 移除 foreignObject 中的潜在危险内容
        .replace(/<foreignObject[^>]*>[\s\S]*?<\/foreignObject>/gi, (match) => {
            return match.replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '')
                .replace(/on\w+\s*=/gi, 'data-blocked-event=');
        })
        // 修复标签重复问题
        .replace(/(<([a-z][a-z0-9]*))\1/gi, '$1');
}

/**
 * 主消毒函数
 * @param {string} html - 输入的 HTML 字符串
 * @returns {string} 消毒后的安全 HTML
 */
export function sanitizeHtml(html) {
    if (!html || typeof html !== 'string') {
        return '';
    }

    // 空内容检查
    const trimmed = html.trim();
    if (!trimmed) {
        return '';
    }

    try {
        // 步骤 1: 提取 DOCTYPE
        let doctype = '';
        const doctypeMatch = trimmed.match(/<!DOCTYPE[^>]*>/i);
        if (doctypeMatch) {
            doctype = doctypeMatch[0] + '\n';
        }

        // 步骤 2: 使用 DOMParser 解析 HTML
        const doc = parseHtml(trimmed);

        // 步骤 3: 创建新的文档用于存放结果
        const resultDoc = new DOMParser().parseFromString('<html><head></head><body></body></html>', 'text/html');

        // 步骤 4: 处理 head 内容
        const head = doc.querySelector('head');
        if (head) {
            const headChildren = Array.from(head.childNodes);
            const resultHead = resultDoc.querySelector('head');
            
            headChildren.forEach(child => {
                if (child.nodeType === Node.COMMENT_NODE) {
                    // 保留注释
                    const comment = resultDoc.createComment(child.textContent);
                    resultHead.appendChild(comment);
                } else if (child.nodeType === Node.ELEMENT_NODE) {
                    // 对于危险标签，使用sanitizeNode处理
                    const sanitizedChild = sanitizeNode(child, resultDoc);
                    if (sanitizedChild) {
                        resultHead.appendChild(sanitizedChild);
                    }
                } else if (child.nodeType === Node.TEXT_NODE && child.textContent.trim()) {
                    // 保留文本节点
                    const text = resultDoc.createTextNode(child.textContent);
                    resultHead.appendChild(text);
                }
            });
        }

        // 步骤 5: 处理 body 内容
        const body = doc.querySelector('body');
        if (body) {
            const bodyChildren = Array.from(body.childNodes);
            const resultBody = resultDoc.querySelector('body');
            
            bodyChildren.forEach(child => {
                if (child.nodeType === Node.COMMENT_NODE) {
                    // 保留注释
                    const comment = resultDoc.createComment(child.textContent);
                    resultBody.appendChild(comment);
                } else if (child.nodeType === Node.ELEMENT_NODE) {
                    // 对于危险标签，使用sanitizeNode处理
                    const sanitizedChild = sanitizeNode(child, resultDoc);
                    if (sanitizedChild) {
                        resultBody.appendChild(sanitizedChild);
                    }
                } else if (child.nodeType === Node.TEXT_NODE && child.textContent.trim()) {
                    // 保留文本节点
                    const text = resultDoc.createTextNode(child.textContent);
                    resultBody.appendChild(text);
                }
            });
        }

        // 步骤 6: 序列化为字符串
        let result = resultDoc.documentElement.outerHTML;

        // 步骤 7: 后处理清理
        result = postProcess(result);

        // 步骤 8: 重新添加 DOCTYPE
        result = doctype + result;

        return result;
    } catch (error) {
        // 发生错误时，返回转义后的纯文本
        console.error('HTML sanitization error:', error);
        return escapeHtmlEntities(html);
    }
}

/**
 * 快速消毒模式 - 适用于简单内容
 * 性能更好但功能较少
 * @param {string} html - 输入的 HTML 字符串
 * @returns {string}
 */
export function sanitizeHtmlFast(html) {
    if (!html || typeof html !== 'string') {
        return '';
    }

    // 使用正则快速过滤最危险的标签
    let result = html
        // 移除 script 标签及其内容
        .replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '<span class="xss-removed">[Script Removed]</span>')
        // 移除 iframe
        .replace(/<iframe[^>]*>[\s\S]*?<\/iframe>/gi, '<span class="xss-removed">[Iframe Removed]</span>')
        // 移除事件处理器
        .replace(/\s*on\w+\s*=\s*["'][^"']*["']/gi, '')
        // 移除 javascript: 伪协议
        .replace(/href\s*=\s*["']\s*javascript:/gi, 'href="blocked:')
        .replace(/src\s*=\s*["']\s*javascript:/gi, 'src="blocked:');

    // 然后使用完整消毒
    return sanitizeHtml(result);
}

/**
 * 检查 HTML 是否包含潜在危险内容
 * @param {string} html - 输入的 HTML 字符串
 * @returns {object} 检查结果
 */
export function checkHtmlSafety(html) {
    if (!html || typeof html !== 'string') {
        return { isSafe: true, threats: [] };
    }

    const threats = [];
    const lowerHtml = html.toLowerCase();

    // 检查危险标签
    const dangerousTagPatterns = [
        { pattern: /<script[^>]*>/i, type: 'script_tag', severity: 'high' },
        { pattern: /<iframe[^>]*>/i, type: 'iframe_tag', severity: 'high' },
        { pattern: /<object[^>]*>/i, type: 'object_tag', severity: 'high' },
        { pattern: /<embed[^>]*>/i, type: 'embed_tag', severity: 'high' },
        { pattern: /<form[^>]*>/i, type: 'form_tag', severity: 'medium' },
        { pattern: /<style[^>]*>/i, type: 'style_tag', severity: 'medium' }
    ];

    for (const { pattern, type, severity } of dangerousTagPatterns) {
        if (pattern.test(html)) {
            threats.push({ type, severity, message: `Detected dangerous tag: ${type}` });
        }
    }

    // 检查事件处理器
    if (/\son\w+\s*=/i.test(html)) {
        const events = html.match(/\s(on\w+)\s*=/gi) || [];
        threats.push({
            type: 'event_handler',
            severity: 'high',
            message: `Detected event handlers: ${events.slice(0, 5).join(', ')}${events.length > 5 ? '...' : ''}`
        });
    }

    // 检查危险 URL 方案
    if (/javascript:/i.test(lowerHtml)) {
        threats.push({ type: 'javascript_protocol', severity: 'high', message: 'Detected javascript: protocol' });
    }

    if (/data:text\/html/i.test(lowerHtml)) {
        threats.push({ type: 'data_html_protocol', severity: 'high', message: 'Detected data:text/html protocol' });
    }

    // 检查可能的编码绕过
    if (/&#x6a;&#x61;&#x76;&#x61;/i.test(lowerHtml) || /&#106;&#097;&#118;/i.test(lowerHtml)) {
        threats.push({ type: 'encoded_javascript', severity: 'high', message: 'Detected encoded javascript attempt' });
    }

    return {
        isSafe: threats.length === 0,
        threats,
        threatCount: threats.length
    };
}

// 默认导出
export default sanitizeHtml;
