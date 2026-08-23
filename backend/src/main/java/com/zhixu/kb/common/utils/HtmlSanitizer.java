package com.zhixu.kb.common.utils;

import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * HTML 白名单清洗：基于 OWASP Java HTML Sanitizer。
 * <ul>
 *   <li>{@link #sanitizeRich}：保留基础排版标签（标题/列表/表格/图片/链接/代码块），
 *       移除 script/iframe/事件属性等危险内容；</li>
 *   <li>{@link #sanitizeText}：剥离全部标签，仅保留纯文本。</li>
 * </ul>
 */
@Component
public class HtmlSanitizer {

    private final PolicyFactory richPolicy;
    private final PolicyFactory textPolicy;

    public HtmlSanitizer() {
        this.richPolicy = new HtmlPolicyBuilder()
                .allowStandardUrlProtocols()
                .allowElements(
                        "p", "br", "hr", "span", "strong", "b", "em", "i", "u", "s", "del",
                        "sub", "sup", "code", "pre", "blockquote", "h1", "h2", "h3", "h4", "h5", "h6",
                        "ul", "ol", "li", "table", "thead", "tbody", "tr", "th", "td",
                        "a", "img", "figure", "figcaption")
                .allowAttributes("href", "title", "target", "rel").onElements("a")
                .allowAttributes("src", "alt", "title", "width", "height").onElements("img")
                .allowAttributes("class").onElements("span", "code", "pre", "p")
                .requireRelNofollowOnLinks()
                .toFactory();
        this.textPolicy = new HtmlPolicyBuilder().toFactory();
    }

    public String sanitizeRich(String html) {
        if (!StringUtils.hasText(html)) {
            return html == null ? null : html;
        }
        return richPolicy.sanitize(html);
    }

    public String sanitizeText(String text) {
        if (!StringUtils.hasText(text)) {
            return text == null ? null : text;
        }
        return textPolicy.sanitize(text);
    }
}
