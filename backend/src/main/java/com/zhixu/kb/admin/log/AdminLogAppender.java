package com.zhixu.kb.admin.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.zhixu.kb.security.MaskingMessageConverter;

/**
 * 管理端日志 Appender：每条日志输出时同步写入内存环形缓冲，
 * 消息复用 %maskedMsg 同一套脱敏规则（邮箱/手机号/身份证/长 token），
 * 保证管理页展示的内容不比文件日志泄露更多。
 * <p>
 * 由 logback-spring.xml 注册到 root logger。
 */
public class AdminLogAppender extends AppenderBase<ILoggingEvent> {

    private final MaskingMessageConverter masker = new MaskingMessageConverter();

    public AdminLogAppender() {
        masker.start();
    }

    @Override
    protected void append(ILoggingEvent event) {
        String message = masker.convert(event);
        String traceId = event.getMDCPropertyMap() == null ? "" : event.getMDCPropertyMap().get("traceId");
        SystemLogRingBuffer.append(
                event.getLevel() == null ? "INFO" : event.getLevel().toString(),
                event.getLoggerName(),
                event.getThreadName(),
                traceId,
                message);
    }
}
