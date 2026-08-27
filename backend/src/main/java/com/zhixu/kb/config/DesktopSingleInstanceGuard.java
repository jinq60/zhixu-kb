package com.zhixu.kb.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.channels.FileLock;

/**
 * 桌面版单实例守卫：通过数据目录文件锁保证同一时刻只有一个应用实例。
 * 重复双击启动时：直接唤起浏览器打开已运行实例的界面并退出当前进程。
 */
@Slf4j
@Component
@Profile("desktop")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DesktopSingleInstanceGuard implements ApplicationRunner {

    private final String dataDir;
    private RandomAccessFile lockFile;
    private FileLock lock;

    public DesktopSingleInstanceGuard(@Value("${app.data-dir}") String dataDir) {
        this.dataDir = dataDir;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        File dir = new File(dataDir);
        dir.mkdirs();
        File file = new File(dir, "app.lock");
        lockFile = new RandomAccessFile(file, "rw");
        lock = lockFile.getChannel().tryLock();
        if (lock != null) {
            log.info("Single instance lock acquired: {}", file);
            return;
        }
        // 已有实例在运行：唤起界面后退出
        log.warn("Another instance is running, opening existing app and exiting");
        try {
            DesktopUiSupport.openBrowser("/notes");
        } catch (Exception ignored) {
        }
        System.exit(0);
    }
}
