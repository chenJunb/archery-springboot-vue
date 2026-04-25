package com.archery.timer.config;

import com.archery.timer.model.dto.TimerStateDTO;
import com.archery.timer.service.LogFileManager;
import com.archery.timer.service.MatchTypeConfigService;
import com.archery.timer.service.TimerEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.SimpMessagingTemplate;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TimerConfig {

    private final SimpMessagingTemplate messagingTemplate;
    private final MatchTypeConfigService matchTypeConfigService;
    private final LogFileManager logFileManager;

    @Bean
    public TimerEngine timerEngineInitializer() {
        log.info("🚀 开始创建 TimerEngine Bean...");

        if (messagingTemplate == null) {
            log.error("❌ 严重：messagingTemplate 为 null，无法创建 TimerEngine");
            throw new IllegalStateException("messagingTemplate cannot be null");
        }

        TimerEngine timerEngine = new TimerEngine(matchTypeConfigService, logFileManager);
        log.info("✅ TimerEngine 实例已创建");

        // 设置消息模板 - 首先设置这个，防止后续状态变更时 messagingTemplate 仍为 null
        timerEngine.setMessagingTemplate(messagingTemplate);
        log.info("✅ messagingTemplate 已设置到 TimerEngine");

        // 设置状态变更回调，用于WebSocket广播
        timerEngine.setStateChangeCallback(state -> {
            // 广播状态更新到所有客户端
            broadcastTimerState(state);
        });
        log.info("✅ stateChangeCallback 已设置到 TimerEngine");

        log.info("✅ TimerEngine Bean 已完全初始化");
        return timerEngine;
    }

    /**
     * 广播计时器状态到所有客户端
     */
    private void broadcastTimerState(TimerStateDTO state) {
        try {
            // 创建WebSocket消息
            com.archery.timer.model.dto.WebSocketMessageDTO message =
                    com.archery.timer.model.dto.WebSocketMessageDTO.timerState(state, "system");

            // 广播到所有订阅了/topic/timer-state的客户端
            messagingTemplate.convertAndSend("/topic/timer-state", message);

            // 记录调试信息
            if (state.getStatus() != null && state.getStatus().equals("running")) {
                log.debug("📡 广播计时器状态: {}, 总剩余: {}秒, 当前阶段: {} (索引{}), 阶段剩余: {}秒, 颜色: {}",
                    state.getStatus(), state.getTotalRemaining(),
                    state.getCurrentStageName(), state.getCurrentStageIndex(),
                    state.getCurrentStageRemaining(), state.getCurrentStageColor());
            } else {
                log.debug("📡 广播计时器状态: {}, 当前阶段: {} (索引{}), 颜色: {}",
                    state.getStatus(), state.getCurrentStageName(),
                    state.getCurrentStageIndex(), state.getCurrentStageColor());
            }
        } catch (Exception e) {
            log.error("广播计时器状态失败: {}", e.getMessage(), e);
        }
    }
}