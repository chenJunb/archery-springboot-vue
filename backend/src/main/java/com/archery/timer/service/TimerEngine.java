package com.archery.timer.service;

import com.archery.timer.model.dto.EnhancedMatchTypeDTO;
import com.archery.timer.model.dto.MatchTypeDTO;
import com.archery.timer.model.dto.TimerStateDTO;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Slf4j
@Service
public class TimerEngine {

    @Setter
    private Consumer<TimerStateDTO> stateChangeCallback;

    @Getter
    private TimerStateDTO currentState;

    @Value("${archery.timer.broadcast-interval:1000}")
    private long broadcastInterval = 1000;

    private final MatchTypeConfigService matchTypeConfigService;
    private final LogFileManager logFileManager;
    private EnhancedMatchTypeDTO currentEnhancedMatchType;
    private MatchTypeDTO currentMatchType;

    private ScheduledExecutorService timerScheduler;
    private long timerStartedAt;
    private long lastUpdateAt;
    private boolean isTimerRunning = false;
    private boolean isTimerPaused = false;

    // 用于AB交替模式的标志
    private boolean isAScreenActive = true;
    private long screenElapsedAtSwitch = 0;

    // AB交替模式下的屏幕计时器状态
    private long screenATimerPausedAt = 0;    // A屏暂停时的时间戳
    private long screenBTimerPausedAt = 0;    // B屏暂停时的时间戳
    private long screenATimerRemaining = 0;   // A屏剩余时间
    private long screenBTimerRemaining = 0;   // B屏剩余时间
    private boolean isFirstSwitch = true;     // 是否为首次切换

    // 屏幕状态记录
    private String currentScreenStatus = "A"; // 当前活动屏幕

    public TimerEngine(MatchTypeConfigService matchTypeConfigService, LogFileManager logFileManager) {
        this.matchTypeConfigService = matchTypeConfigService;
        this.logFileManager = logFileManager;
        this.currentState = TimerStateDTO.idleState();
        this.timerScheduler = Executors.newSingleThreadScheduledExecutor();

        // 初始化默认AB屏状态
        this.currentState.setScreenAEnabled(true);
        this.currentState.setScreenBEnabled(true);
        this.currentState.setAbMode("alternate");
        this.currentState.setActiveScreen("A");

        // 初始化屏幕剩余时间为默认比赛时间（如果没有选择比赛类型）
        this.screenATimerRemaining = 0;
        this.screenBTimerRemaining = 0;

        logFileManager.logSystemStatus("TimerEngine", "计时引擎已初始化");
    }

    /**
     * 选择比赛类型
     */
    public synchronized void selectMatchType(String matchTypeId) {
        EnhancedMatchTypeDTO enhancedMatchType = matchTypeConfigService.getMatchType(matchTypeId);
        if (enhancedMatchType == null) {
            log.error("比赛类型不存在: {}", matchTypeId);
            logFileManager.logError("system", "system", "SELECT_MATCH_TYPE",
                "比赛类型不存在: " + matchTypeId, null);
            return;
        }

        this.currentEnhancedMatchType = enhancedMatchType;
        this.currentMatchType = matchTypeConfigService.convertToLegacyFormat(enhancedMatchType);

        // 记录比赛类型选择
        logFileManager.logClientAction("system", "system", "SELECT_MATCH_TYPE", matchTypeId);

        // 更新基本状态
        this.currentState.setMatchTypeId(matchTypeId);
        this.currentState.setMatchTypeName(enhancedMatchType.getChineseName());
        this.currentState.setTotalRemaining(enhancedMatchType.getTotalTime());
        this.currentState.setTotalElapsed(0);

        // 应用比赛类型的默认AB屏模式
        if (enhancedMatchType.getDefaultScreenMode() != null) {
            setABMode(enhancedMatchType.getDefaultScreenMode());
        }

        // 设置默认提示文案
        if (enhancedMatchType.getDefaultAPrompt() != null) {
            this.currentState.setAPrompt(enhancedMatchType.getDefaultAPrompt());
        }
        if (enhancedMatchType.getDefaultBPrompt() != null) {
            this.currentState.setBPrompt(enhancedMatchType.getDefaultBPrompt());
        }

        // 设置时间配置
        if (enhancedMatchType.getPreparationTime() != null) {
            this.currentState.setPreparationTime(enhancedMatchType.getPreparationTime());
        }
        if (enhancedMatchType.getCompetitionTime() != null) {
            this.currentState.setCompetitionTime(enhancedMatchType.getCompetitionTime());
        }
        if (enhancedMatchType.getYellowLightTime() != null) {
            this.currentState.setYellowLightTime(enhancedMatchType.getYellowLightTime());
        } else {
            // 如果黄灯时间为null，设置为0
            this.currentState.setYellowLightTime(0);
        }

        // 重置阶段信息
        if (currentMatchType != null && currentMatchType.getStages() != null && !currentMatchType.getStages().isEmpty()) {
            this.currentState.setCurrentStageIndex(0);
            MatchTypeDTO.StageDTO firstStage = currentMatchType.getStages().get(0);
            this.currentState.setCurrentStageName(firstStage.getName());
            this.currentState.setCurrentStageColor(firstStage.getColor());
            this.currentState.setCurrentStageDuration(firstStage.getDuration());
            this.currentState.setCurrentStageElapsed(0);
            this.currentState.setCurrentStageRemaining(firstStage.getDuration());
        }

        // 初始化AB交替模式的屏幕计时器
        // 规则: 绿灯初始状态 - A屏开始倒计时，B屏暂停在初始时间
        if ("alternate".equals(this.currentState.getAbMode()) && currentState.getCompetitionTime() != null) {
            // 两个屏幕都从竞赛时间开始
            this.screenATimerRemaining = currentState.getCompetitionTime();
            this.screenBTimerRemaining = currentState.getCompetitionTime();

            // A屏正在运行，B屏暂停
            this.screenATimerPausedAt = 0;      // A屏: 0 表示正在运行
            this.screenBTimerPausedAt = System.currentTimeMillis(); // B屏: 非0表示已暂停

            this.currentScreenStatus = "A";
            this.isFirstSwitch = true;
            this.isAScreenActive = true;

            log.info("AB交替模式初始化 - A屏从{}秒开始倒计时，B屏暂停在{}秒",
                screenATimerRemaining, screenBTimerRemaining);
        }

        log.info("选择比赛类型: {} (AB模式: {})", enhancedMatchType.getChineseName(), this.currentState.getAbMode());
        notifyStateChange();
    }

    /**
     * 开始计时
     */
    public synchronized void startTimer(String controlClientId) {
        if (currentMatchType == null) {
            log.warn("请先选择比赛类型");
            logFileManager.logError(controlClientId, "control", "START_TIMER",
                "比赛类型未选择", null);
            return;
        }

        if (isTimerRunning && !isTimerPaused) {
            log.warn("计时器已经在运行");
            return;
        }

        isTimerRunning = true;
        isTimerPaused = false;
        currentState.setStatus("running");
        currentState.setControlClientId(controlClientId);

        // ✅ 改进：原子地设置时间基准，防止并发更新导致的不一致
        if (lastUpdateAt == 0 || isTimerPaused) {
            // 首次启动或从暂停恢复
            // ✅ 使用临时变量确保两个值的一致性
            long now = System.currentTimeMillis();
            timerStartedAt = now;
            lastUpdateAt = now;
            screenElapsedAtSwitch = 0;
            log.debug("✅ 时间基准已重置 - timerStartedAt: {}, lastUpdateAt: {}", timerStartedAt, lastUpdateAt);
        }

        // AB交替模式：记录切换时间
        if ("alternate".equals(currentState.getAbMode())) {
            if (currentState.getActiveScreen() != null) {
                isAScreenActive = "A".equals(currentState.getActiveScreen());
            }
            screenElapsedAtSwitch = System.currentTimeMillis();
        }

        // 开始定时任务
        startTimerTask();

        logFileManager.logClientAction(controlClientId, "control", "START_TIMER",
            currentMatchType.getName() + " | AB模式: " + currentState.getAbMode());
        log.info("开始计时 - 比赛类型: {}, 控制端: {}", currentMatchType.getName(), controlClientId);
        notifyStateChange();
    }

    /**
     * 暂停计时
     */
    public synchronized void pauseTimer() {
        if (!isTimerRunning || isTimerPaused) {
            return;
        }

        isTimerPaused = true;
        currentState.setStatus("paused");

        // 停止定时任务
        stopTimerTask();

        log.info("暂停计时");
        notifyStateChange();
    }

    /**
     * 重置计时
     */
    public synchronized void resetTimer() {
        stopTimerTask();

        isTimerRunning = false;
        isTimerPaused = false;
        timerStartedAt = 0;
        lastUpdateAt = 0;
        screenElapsedAtSwitch = 0;
        isAScreenActive = true;

        // 重置状态
        currentState.setStatus("idle");
        currentState.setControlClientId(null);

        if (currentMatchType != null) {
            currentState.setTotalRemaining(currentMatchType.getTotalTime());
            currentState.setTotalElapsed(0);

            if (!currentMatchType.getStages().isEmpty()) {
                currentState.setCurrentStageIndex(0);
                MatchTypeDTO.StageDTO firstStage = currentMatchType.getStages().get(0);
                currentState.setCurrentStageElapsed(0);
                currentState.setCurrentStageRemaining(firstStage.getDuration());
                // ✅ 修复1.13: 重置舞台颜色到第一个舞台的颜色
                currentState.setCurrentStageColor(firstStage.getColor());
            }
        }

        log.info("重置计时");
        notifyStateChange();
    }

    /**
     * 设置AB屏模式
     */
    public synchronized void setABMode(String mode) {
        if (!"alternate".equals(mode) && !"sync".equals(mode) && !"only_a".equals(mode) && !"only_b".equals(mode)) {
            log.error("无效的AB屏模式: {}", mode);
            return;
        }

        String oldMode = currentState.getAbMode();
        currentState.setAbMode(mode);

        if ("alternate".equals(mode)) {
            // 恢复当前活动屏幕状态
            if ("A".equals(currentScreenStatus)) {
                currentState.setActiveScreen("A");
                isAScreenActive = true;
            } else {
                currentState.setActiveScreen("B");
                isAScreenActive = false;
            }
            screenElapsedAtSwitch = System.currentTimeMillis();

            // 如果从其他模式切换过来，初始化AB交替状态
            if (!"alternate".equals(oldMode) && currentEnhancedMatchType != null) {
                initializeABAlternateMode();
            }
        } else if ("sync".equals(mode)) {
            currentState.setActiveScreen(null);
            // 同步模式下，两个屏幕都显示相同内容
            screenATimerPausedAt = 0;
            screenBTimerPausedAt = 0;
        } else if ("only_a".equals(mode)) {
            currentState.setActiveScreen("A");
            currentState.setScreenAEnabled(true);
            currentState.setScreenBEnabled(false);
        } else if ("only_b".equals(mode)) {
            currentState.setActiveScreen("B");
            currentState.setScreenAEnabled(false);
            currentState.setScreenBEnabled(true);
        }

        log.info("设置AB屏模式: {}", mode);
        notifyStateChange();
    }

    /**
     * 初始化AB交替模式
     */
    private void initializeABAlternateMode() {
        if (currentEnhancedMatchType == null) {
            return;
        }

        // 绿灯初始状态：A屏开始倒计时，B屏暂停在绿灯的初始配置时间上
        if (currentState.getCompetitionTime() != null) {
            this.screenATimerRemaining = currentState.getCompetitionTime();
            this.screenBTimerRemaining = currentState.getCompetitionTime();
            this.screenATimerPausedAt = 0; // A屏正在运行
            this.screenBTimerPausedAt = System.currentTimeMillis(); // B屏立刻暂停
            this.currentScreenStatus = "A";
            this.isFirstSwitch = true;

            log.info("初始化AB交替模式 - A屏从 {} 秒开始倒计时，B屏暂停保持在 {} 秒",
                screenATimerRemaining, screenBTimerRemaining);
        }
    }

    /**
     * 切换AB屏（实现详细的个人赛和团队赛规则）
     */
    /**
     * AB屏幕切换 - 遵循AB交替模式规则
     * ✅ 改进：添加多层防护，防止空指针异常
     *
     * 规则说明:
     * 1. 准备阶段(红灯): AB屏强制同步倒计时
     * 2. 绿灯初始状态: A屏开始倒计时，B屏暂停在初始时间
     * 3. 个人赛切换: 原屏清零，新屏从初始时间重新开始
     * 4. 团队赛切换: 原屏暂停保留时间，新屏继续倒计时
     */
    public synchronized void toggleABScreen() {
        // ✅ 第一层检查：模式检查
        if (!"alternate".equals(currentState.getAbMode())) {
            log.warn("只有AB交替模式可以切换屏幕");
            return;
        }

        // ✅ 第二层检查：运行状态和比赛类型检查
        if (!isTimerRunning) {
            log.warn("计时器未运行，无法切换屏幕");
            return;
        }

        if (currentEnhancedMatchType == null) {
            log.error("❌ 未选择比赛类型，无法切换屏幕");
            return;
        }

        // ✅ 第三层检查：活跃屏幕检查
        String currentScreen = currentState.getActiveScreen();
        if (currentScreen == null || currentScreen.isEmpty()) {
            log.error("❌ 活跃屏幕信息丢失，无法切换屏幕");
            return;
        }

        long now = System.currentTimeMillis();
        String newScreen = "A".equals(currentScreen) ? "B" : "A";

        log.info("AB屏切换规则执行: {} -> {}", currentScreen, newScreen);

        // 1. 保存当前屏幕的剩余时间
        if ("A".equals(currentScreen)) {
            screenATimerRemaining = calculateRemainingTimeForScreen("A", now);
            log.debug("保存A屏剩余时间: {}秒", screenATimerRemaining);
        } else {
            screenBTimerRemaining = calculateRemainingTimeForScreen("B", now);
            log.debug("保存B屏剩余时间: {}秒", screenBTimerRemaining);
        }

        // ✅ 第四层检查：比赛类型信息检查
        String category = currentEnhancedMatchType.getCategory();
        String alternateType = currentEnhancedMatchType.getAlternateType();

        if (category == null || category.isEmpty()) {
            log.error("❌ 比赛类型分类信息丢失，无法切换屏幕");
            return;
        }

        boolean isIndividual = "individual".equals(category) || "individual_alternate".equals(alternateType);

        // 3. 应用切换规则
        if (isIndividual) {
            // 个人赛规则: 原屏清零，新屏从初始时间重新开始
            if ("A".equals(newScreen)) {
                // 切换到A屏
                screenBTimerRemaining = 0;            // 原B屏清零
                screenBTimerPausedAt = now;           // B屏暂停
                screenATimerRemaining = currentState.getCompetitionTime(); // A屏重新开始
                screenATimerPausedAt = 0;             // A屏运行
                log.info("个人赛切换: B屏清零(0秒)，A屏重新开始({}秒)",
                    currentState.getCompetitionTime());
            } else {
                // 切换到B屏
                screenATimerRemaining = 0;            // 原A屏清零
                screenATimerPausedAt = now;           // A屏暂停
                screenBTimerRemaining = currentState.getCompetitionTime(); // B屏重新开始
                screenBTimerPausedAt = 0;             // B屏运行
                log.info("个人赛切换: A屏清零(0秒)，B屏重新开始({}秒)",
                    currentState.getCompetitionTime());
            }
        } else {
            // 团队赛/混团规则: 原屏暂停保留时间，新屏继续倒计时
            if ("A".equals(newScreen)) {
                // 切换到A屏
                screenATimerPausedAt = 0;             // A屏继续运行
                screenBTimerPausedAt = now;           // B屏暂停
                log.info("团队赛切换: B屏暂停(剩余{}秒)，A屏继续计时",
                    screenBTimerRemaining);
            } else {
                // 切换到B屏
                screenBTimerPausedAt = 0;             // B屏继续运行
                screenATimerPausedAt = now;           // A屏暂停
                log.info("团队赛切换: A屏暂停(剩余{}秒)，B屏继续计时",
                    screenATimerRemaining);
            }
        }

        // 4. 更新当前活动屏幕
        currentState.setActiveScreen(newScreen);
        isAScreenActive = "A".equals(newScreen);
        screenElapsedAtSwitch = now;

        log.info("屏幕切换完成 => 当前活动屏幕: {}, A剩余: {}秒, B剩余: {}秒",
            newScreen, screenATimerRemaining, screenBTimerRemaining);

        notifyStateChange();
    }

    /**
     * 启用/禁用屏幕
     */
    public synchronized void setScreenEnabled(String screen, boolean enabled) {
        if ("A".equals(screen)) {
            currentState.setScreenAEnabled(enabled);
        } else if ("B".equals(screen)) {
            currentState.setScreenBEnabled(enabled);
        }

        log.info("设置屏幕{}启用: {}", screen, enabled);
        notifyStateChange();
    }

    /**
     * 更新当前计时状态
     */
    private synchronized void updateTimerState() {
        if (!isTimerRunning || isTimerPaused) {
            return;
        }

        long now = System.currentTimeMillis();
        long elapsedSinceLast = now - lastUpdateAt;
        long totalElapsed = now - timerStartedAt;

        // ✅ 计算总时间和剩余时间 - 防止整数溢出
        long totalElapsedSeconds = totalElapsed / 1000;

        // ✅ 使用安全转换防止溢出
        int totalElapsedSecondsInt;
        try {
            totalElapsedSecondsInt = Math.toIntExact(totalElapsedSeconds);
        } catch (ArithmeticException e) {
            log.error("❌ 整数溢出：总经过时间{}秒超过int范围，重置计时器", totalElapsedSeconds);
            resetTimer();
            return;
        }

        int totalRemainingSeconds = Math.max(0, currentMatchType.getTotalTime() - totalElapsedSecondsInt);

        // 检查是否结束
        if (totalRemainingSeconds <= 0) {
            finishTimer();
            return;
        }

        // 更新阶段信息
        updateCurrentStage(totalElapsedSecondsInt);

        // AB交替模式处理
        if ("alternate".equals(currentState.getAbMode())) {
            long screenElapsed = now - screenElapsedAtSwitch;
            currentState.setCurrentStageRemaining(currentState.getCurrentStageRemaining() - (int)(elapsedSinceLast / 1000));
        }

        // 更新状态
        currentState.setTotalElapsed(totalElapsedSecondsInt);
        currentState.setTotalRemaining(totalRemainingSeconds);
        currentState.setLastUpdateTime(LocalDateTime.now());
        currentState.setTimestamp(now);

        lastUpdateAt = now;

        // 通知状态变更
        notifyStateChange();
    }

    /**
     * 更新当前阶段
     * 逻辑：阶段1=准备(RED), 阶段2=比赛(GREEN,但最后N秒变为YELLOW)
     * 黄灯时间是比赛时间的最后N秒，当比赛倒计时进入黄灯时间时，灯色变为黄色
     */
    private void updateCurrentStage(int totalElapsedSeconds) {
        if (currentMatchType == null) {
            return;
        }

        int accumulatedTime = 0;
        int currentStageIndex = -1;
        int stageRemaining = 0;
        int stageElapsed = 0;
        MatchTypeDTO.StageDTO currentStage = null;

        // 查找当前阶段
        for (int i = 0; i < currentMatchType.getStages().size(); i++) {
            MatchTypeDTO.StageDTO stage = currentMatchType.getStages().get(i);
            if (totalElapsedSeconds < accumulatedTime + stage.getDuration()) {
                currentStageIndex = i;
                currentStage = stage;
                stageElapsed = totalElapsedSeconds - accumulatedTime;
                stageRemaining = stage.getDuration() - stageElapsed;
                break;
            }
            accumulatedTime += stage.getDuration();
        }

        // 如果超出所有阶段，结束计时
        if (currentStageIndex == -1) {
            finishTimer();
            return;
        }

        // 更新阶段信息
        if (currentState.getCurrentStageIndex() != currentStageIndex) {
            // 阶段切换，这里可以触发声音提示
            log.info("切换到阶段: {}", currentStage.getName());
        }

        currentState.setCurrentStageIndex(currentStageIndex);
        currentState.setCurrentStageName(currentStage.getName());
        currentState.setCurrentStageDuration(currentStage.getDuration());
        currentState.setCurrentStageElapsed(stageElapsed);
        currentState.setCurrentStageRemaining(stageRemaining);

        // 关键逻辑：如果是比赛阶段（index=1）且进入黄灯时间，改变灯色
        String stageColor = currentStage.getColor();
        if (currentStageIndex == 1 && currentEnhancedMatchType != null) {
            // 比赛阶段：检查是否进入黄灯时间
            Integer yellowLightTime = currentEnhancedMatchType.getYellowLightTime();
            if (yellowLightTime != null && yellowLightTime > 0 && stageRemaining <= yellowLightTime) {
                // 进入黄灯阶段
                stageColor = "#FFFF00";  // 黄灯颜色
                log.info("进入黄灯阶段 - 剩余时间: {} 秒, 黄灯时间: {} 秒", stageRemaining, yellowLightTime);
            }
        }

        currentState.setCurrentStageColor(stageColor);
    }

    /**
     * 结束计时
     */
    private synchronized void finishTimer() {
        stopTimerTask();

        isTimerRunning = false;
        currentState.setStatus("finished");
        currentState.setTotalRemaining(0);
        currentState.setTotalElapsed(currentMatchType.getTotalTime());

        log.info("计时结束 - 比赛类型: {}", currentMatchType.getName());
        notifyStateChange();
    }

    /**
     * 启动定时任务
     * ✅ 改进：检查executor状态，避免在已关闭的executor上调度任务
     */
    private void startTimerTask() {
        stopTimerTask(); // 确保之前的任务已停止

        // ✅ 创建新的executor
        timerScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "TimerEngine-ScheduledTask");
            thread.setDaemon(false);
            return thread;
        });

        // ✅ 检查executor是否可用
        if (timerScheduler == null || timerScheduler.isShutdown()) {
            log.error("❌ 计时器调度器创建失败或已关闭，无法启动定时任务");
            return;
        }

        // ✅ 安全地调度任务
        try {
            timerScheduler.scheduleAtFixedRate(() -> {
                try {
                    updateTimerState();
                } catch (Exception e) {
                    log.error("定时任务执行异常", e);
                }
            }, 0, broadcastInterval, TimeUnit.MILLISECONDS);
            log.debug("✅ 定时任务已启动，广播间隔: {}ms", broadcastInterval);
        } catch (RejectedExecutionException e) {
            log.error("❌ 计时器调度器已关闭，无法调度任务", e);
        }
    }

    /**
     * 停止定时任务
     * ✅ 改进：安全地关闭executor，等待任务完成
     */
    private void stopTimerTask() {
        if (timerScheduler != null && !timerScheduler.isShutdown()) {
            try {
                // ✅ 停止接收新任务
                timerScheduler.shutdown();

                // ✅ 等待现有任务完成，最多等待500ms
                if (!timerScheduler.awaitTermination(500, TimeUnit.MILLISECONDS)) {
                    log.warn("⚠️ 定时任务未在规定时间内完成，执行强制关闭");
                    timerScheduler.shutdownNow();
                }

                log.debug("✅ 定时任务已停止");
            } catch (InterruptedException e) {
                log.error("❌ 等待定时任务完成时被中断", e);
                timerScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * 通知状态变更
     */
    private void notifyStateChange() {
        if (stateChangeCallback != null) {
            try {
                stateChangeCallback.accept(currentState);
            } catch (Exception e) {
                log.error("状态变更回调异常", e);
            }
        }
    }

    /**
     * 设置声音开关
     */
    public synchronized void setSoundEnabled(boolean enabled) {
        currentState.setSoundEnabled(enabled);
        notifyStateChange();
    }

    /**
     * 设置音量
     */
    public synchronized void setVolume(int volume) {
        currentState.setVolume(Math.max(0, Math.min(100, volume)));
        notifyStateChange();
    }

    /**
     * 计算屏幕剩余时间
     */
    private long calculateRemainingTimeForScreen(String screen, long currentTime) {
        if (currentEnhancedMatchType == null) {
            return 0;
        }

        if ("A".equals(screen)) {
            if (screenATimerPausedAt == 0) {
                // 屏幕正在运行
                long elapsed = (currentTime - screenElapsedAtSwitch) / 1000;
                return Math.max(0, screenATimerRemaining - elapsed);
            } else {
                // 屏幕已暂停
                return Math.max(0, screenATimerRemaining);
            }
        } else {
            if (screenBTimerPausedAt == 0) {
                // 屏幕正在运行
                long elapsed = (currentTime - screenElapsedAtSwitch) / 1000;
                return Math.max(0, screenBTimerRemaining - elapsed);
            } else {
                // 屏幕已暂停
                return Math.max(0, screenBTimerRemaining);
            }
        }
    }

    /**
     * 获取当前状态（增强版，包含AB屏详细信息）
     */
    public TimerStateDTO getState() {
        long now = System.currentTimeMillis();

        // 添加AB屏详细状态信息
        currentState.setScreenARemaining((int) calculateRemainingTimeForScreen("A", now));
        currentState.setScreenBRemaining((int) calculateRemainingTimeForScreen("B", now));

        // 设置屏幕状态
        currentState.setScreenAStatus(screenATimerPausedAt == 0 ? "running" : "paused");
        currentState.setScreenBStatus(screenBTimerPausedAt == 0 ? "running" : "paused");

        // 设置比赛类型详细信息
        if (currentEnhancedMatchType != null) {
            currentState.setMatchTypeCategory(currentEnhancedMatchType.getCategory());
            currentState.setMatchTypeSubCategory(currentEnhancedMatchType.getSubCategory());
            currentState.setAlternateType(currentEnhancedMatchType.getAlternateType());
        }

        return currentState;
    }

    /**
     * 获取比赛类型（兼容格式）
     */
    public MatchTypeDTO getMatchType(String matchTypeId) {
        EnhancedMatchTypeDTO enhanced = matchTypeConfigService.getMatchType(matchTypeId);
        return matchTypeConfigService.convertToLegacyFormat(enhanced);
    }

    /**
     * 获取所有比赛类型（兼容格式）
     */
    public Map<String, MatchTypeDTO> getAllMatchTypes() {
        Map<String, MatchTypeDTO> legacyTypes = new HashMap<>();
        List<EnhancedMatchTypeDTO> enhancedTypes = matchTypeConfigService.getAllMatchTypes();

        for (EnhancedMatchTypeDTO enhanced : enhancedTypes) {
            MatchTypeDTO legacy = matchTypeConfigService.convertToLegacyFormat(enhanced);
            if (legacy != null) {
                legacyTypes.put(enhanced.getId(), legacy);
            }
        }

        return legacyTypes;
    }

    /**
     * 获取所有增强的比赛类型
     */
    public List<EnhancedMatchTypeDTO> getAllEnhancedMatchTypes() {
        return matchTypeConfigService.getAllMatchTypes();
    }

    /**
     * 根据类别获取比赛类型（兼容格式）
     */
    public Map<String, MatchTypeDTO> getMatchTypesByCategory(String category) {
        Map<String, MatchTypeDTO> filtered = new HashMap<>();
        List<EnhancedMatchTypeDTO> enhancedTypes = matchTypeConfigService.getMatchTypesByCategory(category);

        for (EnhancedMatchTypeDTO enhanced : enhancedTypes) {
            MatchTypeDTO legacy = matchTypeConfigService.convertToLegacyFormat(enhanced);
            if (legacy != null) {
                filtered.put(enhanced.getId(), legacy);
            }
        }

        return filtered;
    }

    /**
     * 获取AB屏模式配置
     */
    public MatchTypeConfigService.ScreenModeConfig getScreenModeConfig(String matchTypeId) {
        return matchTypeConfigService.getScreenModeConfig(matchTypeId, null);
    }

    /**
     * 设置提示文案
     */
    public synchronized void setPrompt(String screen, String prompt) {
        if ("A".equals(screen)) {
            currentState.setAPrompt(prompt);
        } else if ("B".equals(screen)) {
            currentState.setBPrompt(prompt);
        }
        notifyStateChange();
    }

    @PreDestroy
    public void shutdown() {
        if (timerScheduler != null && !timerScheduler.isShutdown()) {
            try {
                // 停止接收新任务
                timerScheduler.shutdown();
                // 等待已有任务完成，最多等待2秒
                if (!timerScheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("计时器任务在规定时间内未完成，执行强制关闭");
                    timerScheduler.shutdownNow();
                }
                log.info("计时引擎已关闭");
            } catch (InterruptedException e) {
                log.error("等待计时器任务完成时被中断", e);
                timerScheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}