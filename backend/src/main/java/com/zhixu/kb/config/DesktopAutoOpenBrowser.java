package com.zhixu.kb.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 桌面版启动完成：自动用系统浏览器打开应用界面
 * （exe 无窗口，双击后用户预期看到界面；具体落到激活页还是工作台由前端按状态决定）。
 */
@Slf4j
@Component
@Profile("desktop")
@RequiredArgsConstructor
public class DesktopAutoOpenBrowser {

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        // 稍等端口稳定后再打开
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                DesktopUiSupport.openBrowser("/");
                log.info("Browser opened to {}", DesktopUiSupport.appUrl("/"));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "desktop-auto-open").start();
    }
}
