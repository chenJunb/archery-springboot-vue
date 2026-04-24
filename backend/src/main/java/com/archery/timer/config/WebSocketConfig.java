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
        // ✅ 改进：处理origins，支持正则和简单URL
        String[] origins = allowedOrigins.split(",");

        // ✅ 修复6.2: trim()去除空格
        String[] trimmedOrigins = new String[origins.length];
        for (int i = 0; i < origins.length; i++) {
            trimmedOrigins[i] = origins[i].trim();
        }

        // ✅ 修复6.1: 将简单URL转换为regex模式
        String[] patterns = new String[trimmedOrigins.length];
        for (int i = 0; i < trimmedOrigins.length; i++) {
            String origin = trimmedOrigins[i];
            // 将简单的URL转换为regex模式（例如 "http://localhost:3000" -> "http://localhost:3000")
            // 或者使用通配符模式 "http.*://localhost:3000"
            if (origin.contains("*")) {
                // 已经是通配符模式，直接使用
                patterns[i] = origin;
            } else {
                // 转义特殊字符并转换为regex
                patterns[i] = origin.replaceAll("\\.", "\\\\.").replaceAll(":", "\\\\:");
            }
        }

        registry.addEndpoint("/ws-archery-timer")
                .setAllowedOriginPatterns(patterns)
                .withSockJS();
    }
}