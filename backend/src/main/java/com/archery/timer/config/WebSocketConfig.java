package com.archery.timer.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * WebSocket 配置（增强版）
 * ✅ 使用 ChannelInterceptor 增强 SimpleBroker 用户队列支持
 * ✅ 实现毫秒级消息同步
 * ✅ 无需额外依赖，零配置成本
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 使用简单代理，配合 ChannelInterceptor 增强用户队列支持
        config.enableSimpleBroker("/topic", "/queue", "/user");
        config.setUserDestinationPrefix("/user");
        config.setApplicationDestinationPrefixes("/app");

        log.info("✅ WebSocket MessageBroker 配置完成 (SimpleBroker + ChannelInterceptor)");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // 添加入站拦截器处理订阅命令
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    String sessionId = accessor.getSessionId();

                    log.debug("📡 [入站] SUBSCRIBE 命令 - sessionId: {}, destination: {}",
                             sessionId, destination);

                    // 处理用户队列订阅：确保 Principal 正确设置
                    if (destination != null && destination.contains("/user/")
                        && destination.contains("/queue")) {
                        // ✅ 关键：必须设置 User Principal，Spring WebSocket 才能正确路由用户消息
                        accessor.setUser(() -> sessionId);
                        log.info("✅ [入站] 用户队列订阅拦截处理 - sessionId: {}, destination: {}",
                                 sessionId, destination);
                    }

                    // 处理普通主题订阅
                    if (destination != null && destination.startsWith("/topic/")) {
                        log.debug("📡 [入站] 主题订阅 - sessionId: {}, destination: {}",
                                 sessionId, destination);
                    }
                }

                // ✅ 连接命令时也设置 Principal
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String sessionId = accessor.getSessionId();
                    log.debug("🔗 [入站] CONNECT 命令 - sessionId: {}", sessionId);
                    accessor.setUser(() -> sessionId);
                }

                return message;
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // 添加出站拦截器监控消息发送
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

                // ✅ 所有消息类型都可能需要 Principal 信息
                String destination = accessor.getDestination();

                if (SimpMessageType.MESSAGE.equals(accessor.getMessageType())) {
                    // 监控用户队列消息发送
                    if (destination != null && destination.startsWith("/user/")) {
                        log.debug("📤 [出站] 用户队列消息 - destination: {}, hasUser: {}",
                                 destination, accessor.getUser() != null);

                        // ✅ 确保用户消息也有 Principal
                        // 检查是否已经有User，如果没有，从路径提取
                        if (accessor.getUser() == null && destination.contains("/")) {
                            try {
                                // 从路径中提取 sessionId: /user/{sessionId}/queue/messages
                                String[] parts = destination.split("/");
                                if (parts.length > 2) {
                                    String sessionId = parts[2];
                                    accessor.setUser(() -> sessionId);
                                    log.info("🔧 [出站] 从路径提取并设置 sessionId: {} (destination: {})",
                                             sessionId, destination);
                                }
                            } catch (Exception e) {
                                log.debug("⚠️ [出站] 从路径提取 sessionId 失败: {}", destination);
                            }
                        } else if (accessor.getUser() != null) {
                            log.debug("📤 [出站] User 已设置: {}", accessor.getUser().getName());
                        }
                    }

                    // 监控广播消息发送
                    if (destination != null && destination.startsWith("/topic/")) {
                        log.debug("📤 [出站] 广播消息 - destination: {}", destination);
                    }
                }

                return message;
            }
        });
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ 简化配置：允许所有来源（开发环境）
        // 生产环境应该替换为具体的域名
        registry.addEndpoint("/ws-archery-timer")
                .setAllowedOriginPatterns("*")  // 允许所有来源
                .withSockJS();

        log.info("✅ WebSocket 端点配置完成 - /ws-archery-timer (允许所有来源)");
    }
}