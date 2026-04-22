package com.archery.timer.controller;

import com.archery.timer.model.dto.EnhancedMatchTypeDTO;
import com.archery.timer.model.dto.TimerStateDTO;
import com.archery.timer.service.LogFileManager;
import com.archery.timer.service.MatchTypeConfigService;
import com.archery.timer.service.TimerEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequiredArgsConstructor
@Slf4j
public class EnhancedWebSocketController {

    private final TimerEngine timerEngine;
    private final MatchTypeConfigService matchTypeConfigService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LogFileManager logFileManager;

    /**
     * 客户端订阅时发送初始状态
     */
    @SubscribeMapping("/topic/timer-state")
    public TimerStateDTO handleSubscribe() {
        log.info("新客户端订阅计时器状态");
        return timerEngine.getState();
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
     */
    @MessageMapping("/timer/toggle-ab-screen")
    @SendTo("/topic/timer-state")
    public TimerStateDTO toggleABScreen(Map<String, Object> payload) {
        String clientId = (String) payload.get("clientId");
        log.info("收到切换AB屏请求，客户端: {}", clientId);
        timerEngine.toggleABScreen();
        return timerEngine.getState();
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
     */
    @MessageMapping("/timer/set-time-config")
    @SendTo("/topic/timer-state")
    public TimerStateDTO setTimeConfig(Map<String, Object> payload) {
        Integer preparation = (Integer) payload.get("preparation");
        Integer competition = (Integer) payload.get("competition");
        Integer yellowLight = (Integer) payload.get("yellowLight");

        log.info("收到设置时间配置请求: 准备={}s, 比赛={}s, 黄灯={}s", preparation, competition, yellowLight);

        TimerStateDTO state = timerEngine.getState();

        // 更新时间配置
        if (preparation != null) {
            state.setPreparationTime(Math.max(0, preparation));
        }
        if (competition != null) {
            state.setCompetitionTime(Math.max(0, competition));
        }
        if (yellowLight != null) {
            state.setYellowLightTime(Math.max(0, yellowLight));
        }

        // 重新计算总时间
        int totalTime = (state.getPreparationTime() != null ? state.getPreparationTime() : 0) +
                       (state.getCompetitionTime() != null ? state.getCompetitionTime() : 0) +
                       (state.getYellowLightTime() != null ? state.getYellowLightTime() : 0);
        state.setTotalRemaining(totalTime);

        // 广播更新
        messagingTemplate.convertAndSend("/topic/timer-state", state);
        return state;
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
     */
    @MessageMapping("/timer/manual-buzzer")
    @SendTo("/topic/buzzer")
    public Map<String, Object> manualBuzzer(Map<String, Object> payload) {
        String type = (String) payload.get("type");
        log.info("收到手动鸣笛请求: {}", type);

        Map<String, Object> response = new HashMap<>();
        response.put("type", "manualBuzzer");
        response.put("buzzerType", type);
        response.put("timestamp", System.currentTimeMillis());
        response.put("success", true);

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