import DOMPurify from 'dompurify';

/**
 * XSS 消毒与内容安全处理方案：
 * 1. 使用 DOMPurify 进行严格过滤
 * 2. 危险标签和属性会被自动转义为文本显示
 * 3. 保留原始格式，确保内容安全
 * 
 * @param {string} html 待处理内容
 * @returns {string} 安全且格式完整的 HTML
 */
export const sanitizeHtml = (html) => {
    if (!html) return '';

    const sanitized = DOMPurify.sanitize(html, {
        USE_PROFILES: { html: true, mathMl: true, svg: true },
        ALLOWED_TAGS: [
            'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'p', 'br', 'hr', 'ul', 'ol', 'li', 'dl', 'dt', 'dd',
            'blockquote', 'pre', 'code', 'table', 'thead', 'tbody', 'tr', 'th', 'td',
            'b', 'strong', 'i', 'em', 'u', 's', 'del', 'ins', 'sub', 'sup', 'mark', 'span',
            'a', 'img', 'video', 'audio', 'source', 'details', 'summary', 'div', 'section', 'article',
            'input', 'label',
            'math', 'semantics', 'mrow', 'mstyle', 'msub', 'msup', 'mover', 'munder', 'munderover', 'mfrac', 'msqrt', 'mroot', 'mi', 'mn', 'mo', 'mtext', 'mspace', 'ms', 'annotation',
            'svg', 'path', 'g', 'line', 'rect', 'circle', 'polyline', 'polygon', 'use', 'clippath', 'defs'
        ],
        ALLOWED_ATTR: [
            'href', 'src', 'alt', 'title', 'class', 'style', 'id', 'target', 'rel',
            'width', 'height', 'controls', 'autoplay', 'loop', 'muted', 'poster',
            'type', 'checked', 'disabled', 'value', 'name', 'align',
            'viewbox', 'd', 'fill', 'stroke', 'stroke-width', 'encoding', 'aria-hidden', 'points', 'cx', 'cy', 'r', 'x', 'y', 'x1', 'y1', 'x2', 'y2', 'transform', 'xlink:href'
        ],
        FORBID_TAGS: ['script', 'iframe', 'object', 'embed', 'form', 'base', 'link', 'style', 'meta', 'html', 'head', 'body'],
        FORBID_ATTR: ['on*']
    });

    return sanitized;
};


export default sanitizeHtml;
