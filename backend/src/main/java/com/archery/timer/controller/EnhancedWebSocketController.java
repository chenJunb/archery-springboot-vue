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

        // ✅ 改进：验证参数类型和内容
        String clientType = validateAndGetString(payload, "clientType", "control");
        String clientName = validateAndGetString(payload, "clientName", "unknown");

        // ✅ 添加详细调试日志
        log.info("📨 收到客户端注册请求 - sessionId: {}, clientType: {}, clientName: {}",
            sessionId, clientType, clientName);
        log.debug("📨 注册请求详情 - sessionId: {}, hasSessionId: {}, messageHeaders: {}",
            sessionId, sessionId != null, headerAccessor.getMessageHeaders());

        // ✅ 验证sessionId不为空且长度合理
        if (sessionId == null || sessionId.isEmpty() || sessionId.length() > 256) {
            log.error("❌ 客户端注册失败：sessionId无效");
            return;
        }

        // ✅ 验证clientType在允许范围内
        if (!isValidClientType(clientType)) {
            log.error("❌ 客户端注册失败：clientType不合法 - {}", clientType);
            return;
        }

        // ✅ 关键修复：检查sessionId是否已经注册过客户端
        String existingClientId = webSocketService.getClientIdBySessionId(sessionId);
        String clientId;

        if (existingClientId != null) {
            // 同一个sessionId已经注册过，使用现有的clientId
            clientId = existingClientId;
            log.info("🔄 会话已注册 - sessionId: {}, 使用现有clientId: {}", sessionId, clientId);
        } else {
            // 新的sessionId，生成新的clientId
            clientId = UUID.randomUUID().toString();
            log.info("🆕 新会话注册 - sessionId: {}, 分配新clientId: {}", sessionId, clientId);
        }

        try {
            // ✅ 关键修复：避免同一个sessionId重复注册导致创建多个client对象
            boolean alreadyRegistered = false;

            if (existingClientId != null) {
                // 检查client是否已经存在且活跃
                var existingClient = webSocketService.getClientInfo(existingClientId);
                alreadyRegistered = (existingClient != null);
                log.debug("检查重复注册 - existingClientId: {}, client不为空: {}", existingClientId, alreadyRegistered);

                if (alreadyRegistered) {
                    // sessionId已经映射到现有客户端，只需更新客户端信息（心跳）
                    webSocketService.updateHeartbeat(existingClientId);
                    log.info("🔄 客户端已存在，更新心跳 - clientId: {}, type: {}", existingClientId, clientType);
                }
            }

            if (!alreadyRegistered) {
                // ✅ 注册会话映射
                webSocketService.registerSessionIdMapping(sessionId, clientId);
                // ✅ 注册客户端
                webSocketService.registerClient(clientId, clientType, clientName);
                log.info("✅ 新客户端注册完成");
            } else {
                // 使用已存在的clientId
                clientId = existingClientId;
            }

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

            log.info("📤 发送注册成功响应 - sessionId: {}, clientId: {}, destination: /user/{}/queue/messages",
                sessionId, clientId, sessionId);

            try {
                // ✅ 关键调试：记录发送的消息内容
                Map<String, Object> wsMessage = Map.of("type", "client_registered", "data", response);
                log.info("📤 发送注册成功消息详情 - 类型: {}, 数据: {}",
                    "client_registered", response);
                log.debug("📤 完整消息结构: {}", wsMessage);

                // ✅ 使用直接路径发送（更可靠）
                String directPath = "/user/" + sessionId + "/queue/messages";
                messagingTemplate.convertAndSend(directPath, wsMessage);
                log.info("✅ 注册成功响应已发送 (通过直接路径: {})", directPath);

                // 方法2：发送到/user/queue/messages（全局用户队列）
                Map<String, Object> globalTestMessage = Map.of(
                    "type", "client_registered",
                    "data", response,
                    "timestamp", System.currentTimeMillis(),
                    "note", "通过全局用户队列"
                );
                messagingTemplate.convertAndSend("/user/queue/messages", globalTestMessage);
                log.info("✅ 注册消息已发送到全局用户队列");

                // 发送调试消息到 /topic/debug 主题
                Map<String, Object> debugMessage = Map.of(
                    "type", "client_registered_debug",
                    "sessionId", sessionId,
                    "clientId", clientId,
                    "timestamp", System.currentTimeMillis(),
                    "destination", "/user/" + sessionId + "/queue/messages"
                );
                messagingTemplate.convertAndSend("/topic/debug", debugMessage);
                log.debug("🔧 发送调试消息到 /topic/debug: sessionId={}, clientId={}", sessionId, clientId);

            } catch (Exception e) {
                log.error("❌ 发送注册成功响应失败", e);
            }
        } catch (Exception e) {
            log.error("❌ 客户端注册失败 - sessionId: {}", sessionId, e);
            logFileManager.logError(sessionId, "system", "CLIENT_REGISTER",
                "客户端注册失败: " + e.getMessage(), null);

            // 发送错误消息给客户端
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "客户端注册失败: " + e.getMessage());

            try {
                String directPath = "/user/" + sessionId + "/queue/messages";
                messagingTemplate.convertAndSend(directPath,
                    Map.of("type", "error", "data", errorResponse));
                log.info("✅ 错误消息已发送到: {}", directPath);
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

        log.info("📨 收到选择比赛类型请求: matchTypeId={}, clientId={}", matchTypeId, clientId);

        // 记录操作日志
        logFileManager.logClientAction(clientId, "control", "SELECT_MATCH_TYPE", matchTypeId);

        if (matchTypeId == null) {
            log.warn("⚠️ 比赛类型ID不能为空");
            logFileManager.logError(clientId, "control", "SELECT_MATCH_TYPE",
                "比赛类型ID不能为空", null);
            return timerEngine.getState();
        }

        // 选择比赛类型
        log.info("📋 调用 timerEngine.selectMatchType({})", matchTypeId);
        timerEngine.selectMatchType(matchTypeId);

        // 验证是否成功设置
        if (timerEngine.getCurrentState() != null && timerEngine.getCurrentState().getMatchTypeId() != null) {
            log.info("✅ 比赛类型已成功设置: {}", timerEngine.getCurrentState().getMatchTypeId());
        } else {
            log.warn("⚠️ 比赛类型设置可能失败，当前状态: {}", timerEngine.getCurrentState());
        }

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
     * ✅ 关键修复：直接修改TimerEngine的currentState，确保修改永久保存
     * ✅ 新增：验证黄灯时间不能超过比赛时间
     */
    @MessageMapping("/timer/set-time-config")
    @SendTo("/topic/timer-state")
    public TimerStateDTO setTimeConfig(Map<String, Object> payload) {
        Integer preparation = (Integer) payload.get("preparation");
        Integer competition = (Integer) payload.get("competition");
        Integer yellowLight = (Integer) payload.get("yellowLight");

        log.info("收到设置时间配置请求: 准备={}s, 比赛={}s, 黄灯={}s", preparation, competition, yellowLight);

        // ✅ 关键修复：直接修改TimerEngine中的currentState
        // 这样可以确保修改被永久保存，不会被后续操作覆盖
        if (preparation != null && preparation >= 0) {
            timerEngine.getCurrentState().setPreparationTime(Math.max(0, preparation));
            log.info("✅ 准备时间已更新: {}s", preparation);
        }

        if (competition != null && competition >= 0) {
            timerEngine.getCurrentState().setCompetitionTime(Math.max(0, competition));
            log.info("✅ 比赛时间已更新: {}s", competition);
        }

        if (yellowLight != null && yellowLight >= 0) {
            // ✅ 新增验证：黄灯时间不能超过比赛时间
            Integer compTime = timerEngine.getCurrentState().getCompetitionTime();
            if (compTime != null && yellowLight > compTime) {
                log.warn("⚠️ 黄灯时间({}s)不能超过比赛时间({}s)，已调整为比赛时间", yellowLight, compTime);
                yellowLight = compTime;
            }
            timerEngine.getCurrentState().setYellowLightTime(Math.max(0, yellowLight));
            log.info("✅ 黄灯时间已更新: {}s", yellowLight);
        } else {
            // ✅ 新增验证：如果只修改了比赛时间，需要检查现有的黄灯时间是否超过新的比赛时间
            if (competition != null) {
                Integer existingYellowLight = timerEngine.getCurrentState().getYellowLightTime();
                if (existingYellowLight != null && existingYellowLight > competition) {
                    log.warn("⚠️ 现有黄灯时间({}s)超过新的比赛时间({}s)，已调整为新的比赛时间", existingYellowLight, competition);
                    timerEngine.getCurrentState().setYellowLightTime(competition);
                }
            }
        }

        // ✅ 更新总时间（用于总计时）
        Integer prepTime = timerEngine.getCurrentState().getPreparationTime();
        Integer compTime = timerEngine.getCurrentState().getCompetitionTime();
        if (prepTime != null && compTime != null) {
            int totalTime = prepTime + compTime;
            timerEngine.getCurrentState().setTotalRemaining(totalTime);
            log.info("✅ 总时间已更新: {}s", totalTime);
        }

        // ✅ 如果当前是准备阶段（idle状态），也更新当前阶段的剩余时间
        if ("idle".equals(timerEngine.getCurrentState().getStatus()) &&
            (timerEngine.getCurrentState().getCurrentStageIndex() == null ||
             timerEngine.getCurrentState().getCurrentStageIndex() == 0)) {
            if (prepTime != null) {
                timerEngine.getCurrentState().setCurrentStageRemaining(prepTime);
                log.info("✅ 准备阶段剩余时间已更新: {}s", prepTime);
            }
        }

        // 获取更新后的完整状态进行广播
        TimerStateDTO updatedState = timerEngine.getState();
        updatedState.setTimestamp(System.currentTimeMillis());

        log.info("✅ 时间配置已保存并广播 - 准备: {}s, 比赛: {}s, 黄灯: {}s",
            updatedState.getPreparationTime(),
            updatedState.getCompetitionTime(),
            updatedState.getYellowLightTime());

        return updatedState;
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

    /**
     * ✅ 参数验证辅助方法：安全获取字符串
     */
    private String validateAndGetString(Map<String, Object> payload, String key, String defaultValue) {
        Object value = payload.get(key);
        if (value instanceof String) {
            String strValue = (String) value;
            // 验证长度
            if (strValue.length() > 256) {
                log.warn("⚠️ 参数 {} 超过最大长度，使用默认值", key);
                return defaultValue;
            }
            // 验证不包含危险字符
            if (strValue.contains("<") || strValue.contains(">") || strValue.contains("\"") || strValue.contains("'")) {
                log.warn("⚠️ 参数 {} 包含危险字符，使用默认值", key);
                return defaultValue;
            }
            return strValue;
        }
        log.debug("⚠️ 参数 {} 类型不正确或为null，使用默认值: {}", key, defaultValue);
        return defaultValue;
    }

    /**
     * ✅ 验证clientType是否合法
     */
    private boolean isValidClientType(String clientType) {
        if (clientType == null) {
            return false;
        }
        // 允许的客户端类型列表
        return clientType.equals("control") || clientType.equals("display-a") || clientType.equals("display-b");
    }
}