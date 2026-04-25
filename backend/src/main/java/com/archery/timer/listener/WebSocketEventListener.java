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
     * ✅ 修复：验证 sessionId 有效性后再设置 principal
     */
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        SimpMessageHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        if (headerAccessor == null) {
            log.warn("⚠️ WebSocket连接事件中 headerAccessor 为null");
            return;
        }

        String sessionId = headerAccessor.getSessionId();

        // ✅ 验证 sessionId 不为空
        if (sessionId == null || sessionId.isEmpty()) {
            log.warn("⚠️ WebSocket连接建立但 sessionId 为空");
            return;
        }

        log.info("✅ WebSocket连接建立 - Session ID: {}", sessionId);
        log.debug("连接详情 - 用户代理: {}, 主机: {}",
            headerAccessor.getNativeHeader("user-agent"),
            headerAccessor.getNativeHeader("host"));

        // ✅ 重要：设置用户principal，以便convertAndSendToUser能正常工作
        // 使用sessionId作为用户名创建一个简单的principal
        try {
            headerAccessor.setUser(() -> sessionId);
            log.debug("已设置用户principal: {}", sessionId);
        } catch (Exception e) {
            log.warn("⚠️ 设置用户principal失败: {}", e.getMessage());
        }

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
        if (headerAccessor == null) {
            log.warn("⚠️ WebSocket订阅事件中 headerAccessor 为null");
            return;
        }

        String sessionId = headerAccessor.getSessionId();
        String destination = headerAccessor.getDestination();

        if (sessionId == null || destination == null) {
            log.debug("⚠️ WebSocket订阅事件中缺少必要信息 - sessionId: {}, destination: {}", sessionId, destination);
            return;
        }

        log.debug("📡 客户端订阅 - Session ID: {}, 订阅主题: {}", sessionId, destination);

        // ✅ 修复：在订阅用户队列时验证并设置principal，但避免重复设置
        if (destination.contains("/user/queue")) {
            try {
                log.debug("🔧 用户队列订阅检测到，验证用户principal: {}", sessionId);
                // 注意：principal 在 handleWebSocketConnectListener 中已经设置过了
                // 这里只做日志记录，不再重复设置，避免覆盖之前的设置
            } catch (Exception e) {
                log.warn("⚠️ 处理用户队列订阅时出错: {}", e.getMessage());
            }
        }

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