package com.archery.timer.listener;

import com.archery.timer.model.dto.WebSocketMessageDTO;
import com.archery.timer.service.LogFileManager;
import com.archery.timer.service.WebSocketService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final WebSocketService webSocketService;
    private final SimpMessagingTemplate messagingTemplate;
    private final LogFileManager logFileManager;

    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("✅ WebSocket连接建立 - Session ID: {}", sessionId);
        log.debug("连接详情 - 用户代理: {}, 主机: {}",
            headerAccessor.getNativeHeader("user-agent"),
            headerAccessor.getNativeHeader("host"));

        // 记录WebSocket连接日志
        logFileManager.logWebSocketConnection(sessionId, "unknown", "CONNECTED",
            "WebSocket连接建立，用户代理: " + headerAccessor.getNativeHeader("user-agent"));
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("❌ WebSocket连接断开 - Session ID: {}", sessionId);
        log.debug("断开原因 - 代码: {}", event.getCloseStatus());

        // 从WebSocket服务中移除客户端
        WebSocketService.ClientInfo clientInfo = webSocketService.getClientInfo(sessionId);
        if (clientInfo != null) {
            log.info("移除客户端 - Type: {}, Name: {}", clientInfo.getClientType(), clientInfo.getClientName());
        }

        webSocketService.unregisterClient(sessionId);

        // 广播客户端断开连接
        broadcastClientStatus(sessionId, "disconnected");

        // 记录WebSocket断开日志
        logFileManager.logWebSocketConnection(sessionId, "unknown", "DISCONNECTED",
            "WebSocket连接断开，断开代码: " + event.getCloseStatus());
    }

    @EventListener
    public void handleWebSocketSubscribeListener(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();

        log.debug("📡 客户端订阅 - Session ID: {}, 订阅主题: {}", sessionId, destination);

        // 记录订阅日志
        logFileManager.logWebSocketConnection(sessionId, "unknown", "SUBSCRIBED",
            "订阅主题: " + destination);
    }

    /**
     * 广播客户端连接状态
     */
    private void broadcastClientStatus(String clientId, String status) {
        Map<String, Object> data = Map.of(
                "clientId", clientId,
                "status", status,
                "connectedCount", webSocketService.getConnectedClients().size()
        );
        WebSocketMessageDTO message = new WebSocketMessageDTO("client_status", data,
                System.currentTimeMillis(), "system");

        messagingTemplate.convertAndSend("/topic/clients", message);
    }
}