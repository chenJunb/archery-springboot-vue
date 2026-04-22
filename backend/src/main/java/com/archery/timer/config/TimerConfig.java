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
        TimerEngine timerEngine = new TimerEngine(matchTypeConfigService, logFileManager);

        // 设置状态变更回调，用于WebSocket广播
        timerEngine.setStateChangeCallback(state -> {
            // 广播状态更新到所有客户端
            broadcastTimerState(state);
        });

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
                log.debug("广播计时器状态: {}, 剩余时间: {}秒",
                    state.getStatus(), state.getTotalRemaining());
            }
        } catch (Exception e) {
            log.error("广播计时器状态失败: {}", e.getMessage(), e);
        }
    }
}