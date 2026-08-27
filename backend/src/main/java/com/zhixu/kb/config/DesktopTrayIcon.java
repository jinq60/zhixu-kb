package com.zhixu.kb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.awt.AWTException;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.TrayIcon;

/**
 * 桌面版系统托盘：应用无窗口常驻，托盘提供「打开界面 / 退出」。
 * 双击托盘图标同样打开工作台。
 */
@Slf4j
@Component
@Profile("desktop")
public class DesktopTrayIcon implements ApplicationRunner {

    private TrayIcon trayIcon;

    @Override
    public void run(ApplicationArguments args) {
        try {
            if (!java.awt.SystemTray.isSupported()) {
                log.warn("System tray not supported, tray icon skipped");
                return;
            }
            PopupMenu menu = new PopupMenu();
            MenuItem openItem = new MenuItem("打开工作台");
            openItem.addActionListener(e -> DesktopUiSupport.openBrowser("/notes"));
            MenuItem exitItem = new MenuItem("退出");
            exitItem.addActionListener(e -> {
                log.info("Exit requested from tray");
                System.exit(0);
            });
            menu.add(openItem);
            menu.addSeparator();
            menu.add(exitItem);

            trayIcon = new TrayIcon(DesktopUiSupport.createAppIcon(32), "知序知识库", menu);
            trayIcon.setImageAutoSize(true);
            trayIcon.addActionListener(e -> DesktopUiSupport.openBrowser("/notes"));
            SystemTray.getSystemTray().add(trayIcon);
            log.info("Desktop tray icon installed");
        } catch (AWTException ex) {
            log.warn("Tray icon install failed: {}", ex.getMessage());
        }
    }
}
