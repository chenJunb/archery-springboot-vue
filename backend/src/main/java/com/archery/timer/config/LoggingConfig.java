package com.archery.timer.config;

import com.archery.timer.service.LogFileManager;
import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.stream.Stream;

@Configuration
@Slf4j
public class LoggingConfig {

    @Value("${custom.logging.directory:logs}")
    private String logDirectory;

    @Value("${custom.logging.max-files:15}")
    private int maxLogFiles;

    @PostConstruct
    public void init() {
        try {
            // 创建日志目录
            Path logDir = Paths.get(logDirectory);
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
                log.info("创建日志目录: {}", logDir.toAbsolutePath());
            }

            // 生成启动日志文件
            createStartupLogFile();

            // 清理旧日志文件
            cleanupOldLogFiles();
        } catch (Exception e) {
            log.error("日志配置初始化失败", e);
        }
    }

    /**
     * 创建启动日志文件
     */
    private void createStartupLogFile() throws IOException {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
        String filename = String.format("archery-timer_startup_%s.log", timestamp);
        Path logFile = Paths.get(logDirectory, filename);

        String startupInfo = generateStartupInfo();
        Files.writeString(logFile, startupInfo);

        log.info("创建启动日志文件: {}", logFile.toAbsolutePath());
        log.info("启动信息:\n{}", startupInfo);
    }

    /**
     * 生成启动信息
     */
    private String generateStartupInfo() {
        return String.format(
            "===========================================\n" +
            "Archery Timer System Startup\n" +
            "===========================================\n" +
            "启动时间: %s\n" +
            "日志目录: %s\n" +
            "最大日志文件数: %d\n" +
            "系统信息:\n" +
            "    Java版本: %s\n" +
            "    操作系统: %s %s\n" +
            "    用户: %s\n" +
            "    工作目录: %s\n" +
            "环境变量:\n" +
            "    LOGGING_FILE_DIRECTORY: %s\n" +
            "    LOGGING_FILE_MAX_FILES: %d\n" +
            "===========================================\n",
            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
            Paths.get(logDirectory).toAbsolutePath(),
            maxLogFiles,
            System.getProperty("java.version"),
            System.getProperty("os.name"),
            System.getProperty("os.version"),
            System.getProperty("user.name"),
            System.getProperty("user.dir"),
            System.getenv("LOGGING_FILE_DIRECTORY") != null ? System.getenv("LOGGING_FILE_DIRECTORY") : "未设置",
            System.getenv("LOGGING_FILE_MAX_FILES") != null ?
                Integer.parseInt(System.getenv("LOGGING_FILE_MAX_FILES")) : maxLogFiles
        );
    }

    /**
     * 清理旧日志文件
     */
    private void cleanupOldLogFiles() {
        try {
            Path logDir = Paths.get(logDirectory);
            if (!Files.exists(logDir)) {
                return;
            }

            try (Stream<Path> files = Files.list(logDir)
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".log"))) {

                // 按修改时间倒序排序，保留最新的maxLogFiles个文件
                Stream<Path> sortedFiles = files.sorted(Comparator.comparing(path -> {
                    try {
                        return Files.getLastModifiedTime(path).toMillis();
                    } catch (IOException e) {
                        return 0L;
                    }
                }, Comparator.reverseOrder()));

                // 获取所有文件列表，保留指定数量
                Path[] allFiles = sortedFiles.toArray(Path[]::new);

                if (allFiles.length > maxLogFiles) {
                    for (int i = maxLogFiles; i < allFiles.length; i++) {
                        try {
                            Files.delete(allFiles[i]);
                            log.info("删除旧日志文件: {}", allFiles[i].getFileName());
                        } catch (IOException e) {
                            log.error("删除日志文件失败: {}", allFiles[i], e);
                        }
                    }
                }
            }

            log.info("日志清理完成，保留最近 {} 个日志文件", maxLogFiles);
        } catch (Exception e) {
            log.error("清理日志文件失败", e);
        }
    }
}