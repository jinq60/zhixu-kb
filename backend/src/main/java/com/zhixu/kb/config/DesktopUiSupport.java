package com.zhixu.kb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.net.URI;

/**
 * 桌面版本机集成工具：打开浏览器、托盘图标绘制。
 */
@Slf4j
@Profile("desktop")
public final class DesktopUiSupport {

    public static final int DESKTOP_PORT = 18230;

    private DesktopUiSupport() {
    }

    public static String appUrl(String path) {
        return "http://127.0.0.1:" + DESKTOP_PORT + path;
    }

    /** 用系统默认浏览器打开应用页面（失败仅记录，不抛出） */
    public static void openBrowser(String path) {
        String url = appUrl(path);
        try {
            if (Desktop.isDesktopSupported()
                    && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
                return;
            }
        } catch (Exception ex) {
            log.warn("Open browser via Desktop failed: {}", ex.getMessage());
        }
        // 兜底：尝试 cmd start（Windows）
        try {
            Runtime.getRuntime().exec(new String[]{"cmd", "/c", "start", "", url});
        } catch (Exception ex) {
            log.warn("Open browser via cmd failed: {}", ex.getMessage());
        }
    }

    /** 程序化绘制应用图标：蓝色圆角方块 + 白色「知」，无需外部资源文件 */
    public static BufferedImage createAppIcon(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int arc = Math.max(4, size / 5);
            g.setColor(new Color(0x25, 0x63, 0xEB));
            g.fillRoundRect(0, 0, size - 1, size - 1, arc, arc);
            g.setColor(Color.WHITE);
            g.setFont(new Font("SansSerif", Font.BOLD, (int) (size * 0.58)));
            java.awt.FontMetrics metrics = g.getFontMetrics();
            String text = "知";
            int x = (size - metrics.stringWidth(text)) / 2;
            int y = (size - metrics.getHeight()) / 2 + metrics.getAscent();
            g.drawString(text, x, y);
        } finally {
            g.dispose();
        }
        return image;
    }
}
