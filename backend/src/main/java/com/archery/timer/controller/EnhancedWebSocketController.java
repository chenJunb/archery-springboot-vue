package com.archery.timer.controller;

import com.archery.timer.model.dto.EnhancedMatchTypeDTO;
import com.archery.timer.model.dto.TimerStateDTO;
import com.archery.timer.service.LogFileManager;
import com.archery.timer.service.MatchTypeConfigService;
import com.archery.timer.service.TimerEngine;
import com.archery.timer.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Controller
@RequiredArgsConstructor
@Slf4j
public class EnhancedWebSocketController {

    private final TimerEngine timerEngine;
    private final MatchTypeConfigService matchTypeConfigService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LogFileManager logFileManager;
    private final WebSocketService webSocketService;

    /**
     * 客户端订阅时发送初始状态
     * ✅ 改进：添加错误处理，确保异常不会中断订阅
     */
    @SubscribeMapping("/topic/timer-state")
    public TimerStateDTO handleSubscribe() {
        log.info("新客户端订阅计时器状态");
        try {
            TimerStateDTO state = timerEngine.getState();
            if (state == null) {
                log.warn("⚠️ 计时器状态为null，返回默认状态");
                return new TimerStateDTO();  // 返回空状态对象而不是null
            }
            return state;
        } catch (Exception e) {
            log.error("❌ 获取计时器状态失败", e);
            // 返回默认状态而不是让异常传播
            return new TimerStateDTO();
        }
    }

    /**
     * 注册客户端
     * ✅ 修复8.1: 建立sessionId -> clientId映射
     */
    @MessageMapping("/register")
    public void registerClient(Map<String, Object> payload, org.springframework.messaging.Message<?> message) {
        SimpMessageHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
        String sessionId = headerAccessor.getSessionId();
        String clientType = (String) payload.get("clientType");
        String clientName = (String) payload.get("clientName");

        // ✅ 生成clientId
        String clientId = UUID.randomUUID().toString();

        try {
            // ✅ 注册会话映射
            webSocketService.registerSessionIdMapping(sessionId, clientId);
            // ✅ 注册客户端
            webSocketService.registerClient(clientId, clientType, clientName);

            log.info("✅ 客户端注册成功 - sessionId: {}, clientId: {}, type: {}, name: {}",
                sessionId, clientId, clientType, clientName);

            // 记录客户端连接日志
            logFileManager.logWebSocketConnection(sessionId, clientId, "REGISTERED",
                "客户端已注册 - 类型: " + clientType + ", 名称: " + clientName);

            // 发送注册成功消息给客户端
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("clientId", clientId);
            response.put("message", "客户端注册成功");

            messagingTemplate.convertAndSendToUser(sessionId, "/queue/messages",
                Map.of("type", "client_registered", "data", response));
        } catch (Exception e) {
            log.error("❌ 客户端注册失败 - sessionId: {}", sessionId, e);
            logFileManager.logError(sessionId, "system", "CLIENT_REGISTER",
                "客户端注册失败: " + e.getMessage(), null);

            // 发送错误消息给客户端
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "客户端注册失败: " + e.getMessage());

            try {
                messagingTemplate.convertAndSendToUser(sessionId, "/queue/messages",
                    Map.of("type", "error", "data", errorResponse));
            } catch (Exception ex) {
                log.error("❌ 发送错误消息失败", ex);
            }
        }
    }

    /**
     * 选择比赛类型
     */
    @MessageMapping("/timer/select-match-type")
    @SendTo("/topic/timer-state")
    public TimerStateDTO selectMatchType(Map<String, Object> payload) {
        String matchTypeId = (String) payload.get("matchTypeId");
        String clientId = (String) payload.get("clientId");

        log.info("收到选择比赛类型请求: {}, 客户端: {}", matchTypeId, clientId);

        // 记录操作日志
        logFileManager.logClientAction(clientId, "control", "SELECT_MATCH_TYPE", matchTypeId);

        if (matchTypeId == null) {
            log.warn("比赛类型ID不能为空");
            logFileManager.logError(clientId, "control", "SELECT_MATCH_TYPE",
                "比赛类型ID不能为空", null);
            return timerEngine.getState();
        }

        // 选择比赛类型
        timerEngine.selectMatchType(matchTypeId);

        // 获取比赛类型的详细配置并广播
        broadcastMatchTypeDetails(matchTypeId, clientId);

        return timerEngine.getState();
    }

    /**
     * 开始计时
     */
    @MessageMapping("/timer/start")
    @SendTo("/topic/timer-state")
    public TimerStateDTO startTimer(Map<String, Object> payload) {
        String clientId = (String) payload.get("clientId");
        log.info("收到开始计时请求，客户端: {}", clientId);
        timerEngine.startTimer(clientId);
        return timerEngine.getState();
    }

    /**
     * 暂停计时
     */
    @MessageMapping("/timer/pause")
    @SendTo("/topic/timer-state")
    public TimerStateDTO pauseTimer() {
        log.info("收到暂停计时请求");
        timerEngine.pauseTimer();
        return timerEngine.getState();
    }

    /**
     * 重置计时
     */
    @MessageMapping("/timer/reset")
    @SendTo("/topic/timer-state")
    public TimerStateDTO resetTimer() {
        log.info("收到重置计时请求");
        timerEngine.resetTimer();
        return timerEngine.getState();
    }

    /**
     * 设置AB屏模式
     */
    @MessageMapping("/timer/set-ab-mode")
    @SendTo("/topic/timer-state")
    public TimerStateDTO setABMode(Map<String, Object> payload) {
        String mode = (String) payload.get("mode");
        log.info("收到设置AB屏模式请求: {}", mode);

        if (mode != null) {
            timerEngine.setABMode(mode);
        }

        return timerEngine.getState();
    }

    /**
     * 切换AB屏
     * ✅ 改进：添加权限检查和防护，确保原子性
     */
    @MessageMapping("/timer/toggle-ab-screen")
    @SendTo("/topic/timer-state")
    public TimerStateDTO toggleABScreen(Map<String, Object> payload) {
        String clientId = (String) payload.get("clientId");
        log.info("收到切换AB屏请求，客户端: {}", clientId);

        // ✅ 验证clientId不为空
        if (clientId == null || clientId.isEmpty()) {
            log.error("❌ 无效的客户端ID，无法切换AB屏");
            return timerEngine.getState();
        }

        // ✅ 权限检查：只有控制端可以切换屏幕
        if (!webSocketService.isControlClient(clientId)) {
            log.warn("⚠️ 非控制端尝试切换AB屏: {}", clientId);
            logFileManager.logError(clientId, "display", "TOGGLE_AB_SCREEN",
                "权限不足：只有控制端可以切换AB屏", null);
            return timerEngine.getState();  // 返回当前状态，不做任何改变
        }

        // ✅ 执行切换操作，toggleABScreen()本身是synchronized的，保证原子性
        try {
            log.info("✅ 控制端切换AB屏 - clientId: {}", clientId);
            timerEngine.toggleABScreen();
            logFileManager.logClientAction(clientId, "control", "TOGGLE_AB_SCREEN", "success");
            return timerEngine.getState();
        } catch (Exception e) {
            log.error("❌ 切换AB屏失败", e);
            logFileManager.logError(clientId, "control", "TOGGLE_AB_SCREEN", "切换失败: " + e.getMessage(), null);
            return timerEngine.getState();
        }
    }

    /**
     * 设置屏幕启用状态
     */
    @MessageMapping("/timer/set-screen-enabled")
    @SendTo("/topic/timer-state")
    public TimerStateDTO setScreenEnabled(Map<String, Object> payload) {
        String screen = (String) payload.get("screen");
        Boolean enabled = (Boolean) payload.get("enabled");

        if (screen != null && enabled != null) {
            log.info("收到设置屏幕启用状态请求: {} = {}", screen, enabled);
            timerEngine.setScreenEnabled(screen, enabled);
        }

        return timerEngine.getState();
    }

    /**
     * 设置提示文案
     */
    @MessageMapping("/timer/set-prompt")
    @SendTo("/topic/timer-state")
    public TimerStateDTO setPrompt(Map<String, Object> payload) {
        String screen = (String) payload.get("screen");
        String prompt = (String) payload.get("prompt");

        if (screen != null && prompt != null) {
            log.info("收到设置提示文案请求: {}屏 = {}", screen, prompt);
            timerEngine.setPrompt(screen, prompt);
        }

        return timerEngine.getState();
    }

    /**
     * 设置时间配置
     * ✅ 改进：复制状态后再修改，不影响TimerEngine的活跃状态
     */
    @MessageMapping("/timer/set-time-config")
    @SendTo("/topic/timer-state")
    public TimerStateDTO setTimeConfig(Map<String, Object> payload) {
        Integer preparation = (Integer) payload.get("preparation");
        Integer competition = (Integer) payload.get("competition");
        Integer yellowLight = (Integer) payload.get("yellowLight");

        log.info("收到设置时间配置请求: 准备={}s, 比赛={}s, 黄灯={}s", preparation, competition, yellowLight);

        // ✅ 获取当前状态但不直接修改
        TimerStateDTO state = timerEngine.getState();

        // ✅ 创建一个新的状态副本进行修改
        TimerStateDTO updatedState = new TimerStateDTO();

        // 复制现有状态
        try {
            // 通过getter/setter复制所有字段
            updatedState.setStatus(state.getStatus());
            updatedState.setTotalRemaining(state.getTotalRemaining());
            updatedState.setCurrentStageIndex(state.getCurrentStageIndex());
            updatedState.setCurrentStageName(state.getCurrentStageName());
            updatedState.setCurrentStageColor(state.getCurrentStageColor());
            updatedState.setActiveScreen(state.getActiveScreen());
            updatedState.setAbMode(state.getAbMode());
            updatedState.setTimestamp(System.currentTimeMillis());

            // ✅ 在副本上更新时间配置
            if (preparation != null) {
                updatedState.setPreparationTime(Math.max(0, preparation));
            } else {
                updatedState.setPreparationTime(state.getPreparationTime());
            }

            if (competition != null) {
                updatedState.setCompetitionTime(Math.max(0, competition));
            } else {
                updatedState.setCompetitionTime(state.getCompetitionTime());
            }

            if (yellowLight != null) {
                updatedState.setYellowLightTime(Math.max(0, yellowLight));
            } else {
                updatedState.setYellowLightTime(state.getYellowLightTime());
            }

            // 重新计算总时间（黄灯是比赛时间的一部分，不单独加入）
            int totalTime = (updatedState.getPreparationTime() != null ? updatedState.getPreparationTime() : 0) +
                           (updatedState.getCompetitionTime() != null ? updatedState.getCompetitionTime() : 0);
            updatedState.setTotalRemaining(totalTime);

            // ✅ 广播更新
            messagingTemplate.convertAndSend("/topic/timer-state", updatedState);
            log.info("✅ 时间配置已更新并广播");
            return updatedState;
        } catch (Exception e) {
            log.error("设置时间配置失败", e);
            return state;  // 发生错误时返回原状态
        }
    }

    /**
     * 设置声音开关
     */
    @MessageMapping("/timer/set-sound-enabled")
    @SendTo("/topic/timer-state")
    public TimerStateDTO setSoundEnabled(Map<String, Object> payload) {
        Boolean enabled = (Boolean) payload.get("enabled");
        if (enabled != null) {
            log.info("收到设置声音开关请求: {}", enabled);
            timerEngine.setSoundEnabled(enabled);
        }
        return timerEngine.getState();
    }

    /**
     * 设置音量
     */
    @MessageMapping("/timer/set-volume")
    @SendTo("/topic/timer-state")
    public TimerStateDTO setVolume(Map<String, Object> payload) {
        Integer volume = (Integer) payload.get("volume");
        if (volume != null) {
            log.info("收到设置音量请求: {}", volume);
            timerEngine.setVolume(volume);
        }
        return timerEngine.getState();
    }

    /**
     * 手动鸣笛
     * ✅ 改进：验证buzzer类型，只允许合法值
     */
    @MessageMapping("/timer/manual-buzzer")
    @SendTo("/topic/buzzer")
    public Map<String, Object> manualBuzzer(Map<String, Object> payload) {
        String type = (String) payload.get("type");
        log.info("收到手动鸣笛请求: {}", type);

        Map<String, Object> response = new HashMap<>();

        // ✅ 验证buzzer类型
        if (type == null || type.isEmpty()) {
            log.warn("⚠️ 无效的鸣笛类型: 为空");
            response.put("type", "manualBuzzer");
            response.put("buzzerType", type);
            response.put("timestamp", System.currentTimeMillis());
            response.put("success", false);
            response.put("error", "鸣笛类型不能为空");
            return response;
        }

        // ✅ 只允许特定的鸣笛类型
        String[] allowedTypes = {"buzz1", "buzz2", "buzz3", "countdown", "manual"};
        boolean isValid = false;
        for (String allowed : allowedTypes) {
            if (allowed.equals(type)) {
                isValid = true;
                break;
            }
        }

        if (!isValid) {
            log.warn("⚠️ 无效的鸣笛类型: {}", type);
            response.put("type", "manualBuzzer");
            response.put("buzzerType", type);
            response.put("timestamp", System.currentTimeMillis());
            response.put("success", false);
            response.put("error", "不支持的鸣笛类型: " + type);
            return response;
        }

        // ✅ 类型验证通过，返回成功响应
        response.put("type", "manualBuzzer");
        response.put("buzzerType", type);
        response.put("timestamp", System.currentTimeMillis());
        response.put("success", true);

        log.info("✅ 鸣笛请求已广播: {}", type);
        return response;
    }

    /**
     * 获取所有比赛类型
     */
    @MessageMapping("/match-types/get-all")
    @SendTo("/topic/match-types")
    public Map<String, Object> getAllMatchTypes() {
        log.info("收到获取所有比赛类型请求");

        Map<String, Object> response = new HashMap<>();
        try {
            List<EnhancedMatchTypeDTO> allTypes = matchTypeConfigService.getAllMatchTypes();
            response.put("type", "matchTypes");
            response.put("success", true);
            response.put("data", allTypes);
            response.put("count", allTypes.size());
        } catch (Exception e) {
            log.error("获取比赛类型失败", e);
            response.put("type", "matchTypes");
            response.put("success", false);
            response.put("message", "获取比赛类型失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 获取AB屏模式配置
     */
    @MessageMapping("/match-types/screen-mode")
    @SendTo("/topic/match-type-screen-mode")
    public Map<String, Object> getScreenModeConfig(Map<String, Object> payload) {
        String matchTypeId = (String) payload.get("matchTypeId");
        log.info("收到获取AB屏模式配置请求: {}", matchTypeId);

        Map<String, Object> response = new HashMap<>();
        try {
            EnhancedMatchTypeDTO matchType = matchTypeConfigService.getMatchType(matchTypeId);

            if (matchType != null) {
                MatchTypeConfigService.ScreenModeConfig config = matchTypeConfigService.getScreenModeConfig(matchTypeId, null);
                response.put("type", "screenModeConfig");
                response.put("success", true);
                response.put("data", config);
                response.put("defaultAPrompt", matchType.getDefaultAPrompt());
                response.put("defaultBPrompt", matchType.getDefaultBPrompt());
            } else {
                response.put("type", "screenModeConfig");
                response.put("success", false);
                response.put("message", "比赛类型不存在: " + matchTypeId);
            }
        } catch (Exception e) {
            log.error("获取AB屏模式配置失败", e);
            response.put("type", "screenModeConfig");
            response.put("success", false);
            response.put("message", "获取AB屏模式配置失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 获取时间配置
     */
    @MessageMapping("/match-types/time-config")
    @SendTo("/topic/match-type-time-config")
    public Map<String, Object> getTimeConfig(Map<String, Object> payload) {
        String matchTypeId = (String) payload.get("matchTypeId");

        Map<String, Object> response = new HashMap<>();
        try {
            EnhancedMatchTypeDTO matchType = matchTypeConfigService.getMatchType(matchTypeId);

            if (matchType != null) {
                Map<String, Integer> timeConfig = new HashMap<>();
                timeConfig.put("preparation", matchType.getPreparationTime());
                timeConfig.put("competition", matchType.getCompetitionTime());
                timeConfig.put("yellowLight", matchType.getYellowLightTime());
                timeConfig.put("total", matchType.getTotalTime());

                response.put("type", "timeConfig");
                response.put("success", true);
                response.put("data", timeConfig);
            } else {
                response.put("type", "timeConfig");
                response.put("success", false);
                response.put("message", "比赛类型不存在: " + matchTypeId);
            }
        } catch (Exception e) {
            log.error("获取时间配置失败", e);
            response.put("type", "timeConfig");
            response.put("success", false);
            response.put("message", "获取时间配置失败: " + e.getMessage());
        }
        return response;
    }

    /**
     * 广播比赛类型详细信息
     */
    private void broadcastMatchTypeDetails(String matchTypeId, String clientId) {
        try {
            EnhancedMatchTypeDTO matchType = matchTypeConfigService.getMatchType(matchTypeId);
            if (matchType != null) {
                Map<String, Object> message = new HashMap<>();
                message.put("type", "matchTypeDetails");
                message.put("matchTypeId", matchTypeId);
                message.put("matchTypeName", matchType.getChineseName());
                message.put("category", matchType.getCategory());
                message.put("alternateType", matchType.getAlternateType());
                message.put("defaultAPrompt", matchType.getDefaultAPrompt());
                message.put("defaultBPrompt", matchType.getDefaultBPrompt());
                message.put("supportABAlternate", matchType.getSupportABAlternate());
                message.put("resetOnSwitchIndividual", matchType.getResetOnSwitchIndividual());
                message.put("pauseOnSwitchTeam", matchType.getPauseOnSwitchTeam());
                message.put("clientId", clientId);
                message.put("timestamp", System.currentTimeMillis());

                messagingTemplate.convertAndSend("/topic/match-type-details", message);
                log.info("广播比赛类型详细信息: {}", matchTypeId);
            }
        } catch (Exception e) {
            log.error("广播比赛类型详细信息失败", e);
        }
    }

    /**
     * 定期广播计时器状态（由TimerEngine调用）
     */
    public void broadcastTimerState(TimerStateDTO state) {
        messagingTemplate.convertAndSend("/topic/timer-state", state);
    }

    /**
     * 手动广播当前状态
     */
    @MessageMapping("/timer/broadcast-state")
    @SendTo("/topic/timer-state")
    public TimerStateDTO broadcastState() {
        log.info("收到广播状态请求");
        TimerStateDTO state = timerEngine.getState();
        messagingTemplate.convertAndSend("/topic/timer-state", state);
        return state;
    }
}