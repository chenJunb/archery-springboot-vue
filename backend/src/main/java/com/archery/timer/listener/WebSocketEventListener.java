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

    /**
     * 处理WebSocket连接建立
     * ✅ 改进：记录sessionId，但不立即注册客户端
     */
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

        // ✅ 注释：真正的客户端注册会在客户端发送 /app/register 消息时进行
        // 这里只是记录连接建立，实际的clientId由客户端提供
    }

    /**
     * 处理WebSocket断开连接
     * ✅ 改进8.1: 使用正确的sessionId->clientId映射，只卸载相关客户端
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        SimpMessageHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();

        log.info("❌ WebSocket连接断开 - Session ID: {}", sessionId);
        log.debug("断开原因 - 代码: {}", event.getCloseStatus());

        // ✅ 修复8.1: 使用sessionId -> clientId映射，只卸载相关客户端
        String clientId = webSocketService.getClientIdBySessionId(sessionId);
        if (clientId != null) {
            try {
                webSocketService.unregisterClient(clientId);
                log.info("✅ 已注销客户端 - ID: {}", clientId);
            } catch (Exception e) {
                log.error("❌ 注销客户端失败 - ID: {}", clientId, e);
            }
        } else {
            log.debug("⚠️ 未找到与会话关联的客户端 - Session ID: {}", sessionId);
        }

        // 广播客户端断开连接事件
        if (clientId != null) {
            broadcastClientStatus(clientId, "disconnected");
        }

        // 记录WebSocket断开日志
        logFileManager.logWebSocketConnection(sessionId, clientId != null ? clientId : "unknown", "DISCONNECTED",
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