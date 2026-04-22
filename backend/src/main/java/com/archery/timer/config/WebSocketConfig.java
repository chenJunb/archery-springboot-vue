package com.archery.timer.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Value("${archery.timer.websocket.allowed-origins:http://localhost:3000,http://localhost:8080}")
    private String allowedOrigins;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // 启用简单的内存消息代理，前缀为"/topic"
        config.enableSimpleBroker("/topic", "/queue");
        // 设置用户前缀为 "/user"
        config.setUserDestinationPrefix("/user");
        // 设置应用程序前缀为"/app"
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // 注册STOMP端点，指定允许的来源
        String[] origins = allowedOrigins.split(",");
        registry.addEndpoint("/ws-archery-timer")
                .setAllowedOriginPatterns(origins)
                .withSockJS();
    }
}