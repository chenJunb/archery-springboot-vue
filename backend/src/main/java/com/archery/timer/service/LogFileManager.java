package com.archery.timer.service;

import javax.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Stream;

@Service
@Slf4j
public class LogFileManager {

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
    private static final DateTimeFormatter FILE_DATE_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${custom.logging.directory:logs}")
    private String logDirectory;

    @Value("${custom.logging.max-files:15}")
    private int maxLogFiles;

    private Path dailyLogFile;
    private final ReentrantLock writeLock = new ReentrantLock();

    @PostConstruct
    public void init() {
        try {
            // 创建日志目录
            Path logDir = Paths.get(logDirectory);
            if (!Files.exists(logDir)) {
                Files.createDirectories(logDir);
                log.info("创建日志目录: {}", logDir.toAbsolutePath());
            }

            // 初始化每日日志文件
            updateDailyLogFile();
        } catch (Exception e) {
            log.error("日志文件管理器初始化失败", e);
        }
    }

    /**
     * 记录操作日志
     */
    public void logOperation(String clientId, String clientType, String operation, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String logEntry = String.format("[%s] [%s] [%s] %s - %s%n",
            timestamp, clientId, clientType, operation, details);

        writeToDailyLog(logEntry);
        log.info("操作日志: {} - {} - {}", clientType, operation, details);
    }

    /**
     * 记录WebSocket连接日志
     */
    public void logWebSocketConnection(String clientId, String clientType, String action, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String logEntry = String.format("[%s] [WS] [%s] [%s] %s - %s%n",
            timestamp, clientId, clientType, action, details);

        writeToDailyLog(logEntry);
        log.info("WebSocket {}: {} - {} - {}", action, clientId, clientType, details);
    }

    /**
     * 记录错误日志
     */
    public void logError(String clientId, String clientType, String operation, String error, Throwable throwable) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String logEntry = String.format("[%s] [ERROR] [%s] [%s] %s - %s%n",
            timestamp, clientId, clientType, operation, error);

        if (throwable != null) {
            logEntry += "异常堆栈:\n";
            for (StackTraceElement element : throwable.getStackTrace()) {
                logEntry += "    " + element.toString() + "\n";
            }
        }

        writeToDailyLog(logEntry);
        log.error("错误日志: {} - {} - {}", clientType, operation, error, throwable);
    }

    /**
     * 记录客户端操作日志（从WebSocket）
     */
    public void logClientAction(String clientId, String clientType, String command, Object data) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String dataStr = data != null ? data.toString() : "null";

        String logEntry = String.format("[%s] [ACTION] [%s] [%s] 命令: %s, 数据: %s%n",
            timestamp, clientId, clientType, command, dataStr);

        writeToDailyLog(logEntry);
        log.info("客户端操作: {} - {} - {}", clientType, command, dataStr);
    }

    /**
     * 记录系统状态日志
     */
    public void logSystemStatus(String status, String details) {
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMATTER);
        String logEntry = String.format("[%s] [SYSTEM] %s - %s%n",
            timestamp, status, details);

        writeToDailyLog(logEntry);
        log.info("系统状态: {} - {}", status, details);
    }

    /**
     * 写入每日日志文件
     */
    private void writeToDailyLog(String logEntry) {
        writeLock.lock();
        try {
            // 检查是否需要更新日志文件（每天一个新文件）
            updateDailyLogFile();

            if (dailyLogFile != null && Files.exists(dailyLogFile.getParent())) {
                Files.writeString(dailyLogFile, logEntry,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            }
        } catch (Exception e) {
            log.error("写入日志文件失败", e);
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * 更新每日日志文件
     */
    private void updateDailyLogFile() {
        try {
            String today = LocalDateTime.now().format(FILE_DATE_FORMATTER);
            String filename = String.format("archery-timer_%s.log", today);
            Path newLogFile = Paths.get(logDirectory, filename);

            if (dailyLogFile == null || !dailyLogFile.equals(newLogFile)) {
                dailyLogFile = newLogFile;

                // 如果是新文件，添加文件头
                if (!Files.exists(dailyLogFile)) {
                    String fileHeader = String.format(
                        "===========================================\n" +
                        "Archery Timer System Log - %s\n" +
                        "===========================================\n" +
                        "日志开始时间: %s\n" +
                        "日志文件: %s\n" +
                        "===========================================\n\n",
                        today,
                        LocalDateTime.now().format(TIMESTAMP_FORMATTER),
                        dailyLogFile.toAbsolutePath()
                    );

                    Files.writeString(dailyLogFile, fileHeader,
                        StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                }
            }

            // 定期清理旧日志文件
            cleanupOldLogFiles();
        } catch (Exception e) {
            log.error("更新每日日志文件失败", e);
        }
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
                    .filter(path -> path.getFileName().toString().matches("archery-timer_\\d{4}-\\d{2}-\\d{2}\\.log"))) {

                // 按文件名（日期）排序，保留最新的maxLogFiles个文件
                Path[] allFiles = files.sorted(Comparator.reverseOrder())
                    .limit(maxLogFiles)
                    .toArray(Path[]::new);

                // 检查是否需要清理
                for (Path file : allFiles) {
                    if (Files.exists(file)) {
                        // 这里可以添加其他清理逻辑，比如删除超过30天的文件
                    }
                }

                log.debug("当前保留 {} 个日志文件", allFiles.length);
            }
        } catch (Exception e) {
            log.error("清理日志文件失败", e);
        }
    }

    /**
     * 获取当前日志文件名
     */
    public String getCurrentLogFileName() {
        return dailyLogFile != null ? dailyLogFile.getFileName().toString() : null;
    }

    /**
     * 获取日志目录路径
     */
    public String getLogDirectory() {
        return logDirectory;
    }

    /**
     * 获取最大日志文件数
     */
    public int getMaxLogFiles() {
        return maxLogFiles;
    }
}