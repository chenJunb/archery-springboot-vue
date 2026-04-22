package com.archery.timer.service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class WebSocketService {

    @Value("${archery.timer.websocket-timeout:86400000}")
    private long websocketTimeout = 86400000L; // 24小时

    @Value("${archery.timer.control-heartbeat-timeout:30000}")
    private long controlHeartbeatTimeout = 30000L; // 30秒

    private final Map<String, ClientInfo> clients = new ConcurrentHashMap<>();
    private ScheduledExecutorService cleanupScheduler;
    private String currentControlClientId = null;
    private long lastControlHeartbeat = 0;

    @Getter
    private final List<ClientInfo> connectedClients = Collections.synchronizedList(new ArrayList<>());

    @PostConstruct
    public void init() {
        // 启动清理任务
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor();
        cleanupScheduler.scheduleAtFixedRate(this::cleanupIdleClients, 1, 1, TimeUnit.MINUTES);
        cleanupScheduler.scheduleAtFixedRate(this::checkControlHeartbeat, 30, 30, TimeUnit.SECONDS);

        log.info("WebSocket服务初始化完成");
    }

    @PreDestroy
    public void shutdown() {
        if (cleanupScheduler != null) {
            cleanupScheduler.shutdown();
        }
        log.info("WebSocket服务关闭");
    }

    /**
     * 注册客户端
     */
    public synchronized void registerClient(String clientId, String clientType, String clientName) {
        ClientInfo client = new ClientInfo();
        client.setClientId(clientId);
        client.setClientType(clientType);
        client.setClientName(clientName);
        client.setRegisteredAt(LocalDateTime.now());
        client.setLastHeartbeat(LocalDateTime.now());
        client.setStatus("connected");

        clients.put(clientId, client);

        // 如果是控制端，且当前没有控制端，设置为控制端
        if ("control".equals(clientType) && currentControlClientId == null) {
            currentControlClientId = clientId;
            lastControlHeartbeat = System.currentTimeMillis();
            log.info("设置客户端为控制端: {}", clientId);
        }

        // 更新连接客户端列表
        updateConnectedClients();

        log.info("客户端注册成功 - ID: {}, 类型: {}, 名称: {}", clientId, clientType, clientName);
        log.info("当前连接客户端数: {}", clients.size());
    }

    /**
     * 注销客户端
     */
    public synchronized void unregisterClient(String clientId) {
        ClientInfo client = clients.remove(clientId);
        if (client != null) {
            log.info("客户端注销 - ID: {}, 类型: {}", clientId, client.getClientType());

            // 如果是控制端，需要重新选择控制端
            if (clientId.equals(currentControlClientId)) {
                selectNewControlClient();
            }
        }

        // 更新连接客户端列表
        updateConnectedClients();

        log.info("当前连接客户端数: {}", clients.size());

        // 如果没有客户端连接，清理资源
        if (clients.isEmpty()) {
            log.info("所有客户端已断开连接");
        }
    }

    /**
     * 更新客户端心跳
     */
    public void updateHeartbeat(String clientId) {
        ClientInfo client = clients.get(clientId);
        if (client != null) {
            client.setLastHeartbeat(LocalDateTime.now());

            // 如果是控制端，更新控制端心跳
            if (clientId.equals(currentControlClientId)) {
                lastControlHeartbeat = System.currentTimeMillis();
            }
        }
    }

    /**
     * 检查客户端是否有控制权限
     */
    public boolean isControlClient(String clientId) {
        return clientId != null && clientId.equals(currentControlClientId);
    }

    /**
     * 获取当前控制端ID
     */
    public String getCurrentControlClientId() {
        return currentControlClientId;
    }

    /**
     * 设置控制端（用于手动切换）
     */
    public synchronized boolean setControlClient(String clientId) {
        if (!clients.containsKey(clientId)) {
            log.warn("客户端不存在: {}", clientId);
            return false;
        }

        ClientInfo client = clients.get(clientId);
        if (!"control".equals(client.getClientType())) {
            log.warn("只有控制端类型可以设置为控制端: {}", clientId);
            return false;
        }

        currentControlClientId = clientId;
        lastControlHeartbeat = System.currentTimeMillis();
        log.info("手动切换控制端到: {}", clientId);
        return true;
    }

    /**
     * 选择新的控制端
     */
    private synchronized void selectNewControlClient() {
        currentControlClientId = null;

        // 查找第一个控制端
        for (ClientInfo client : clients.values()) {
            if ("control".equals(client.getClientType())) {
                currentControlClientId = client.getClientId();
                lastControlHeartbeat = System.currentTimeMillis();
                log.info("自动选择新的控制端: {}", currentControlClientId);
                return;
            }
        }

        log.info("没有找到可用的控制端");
    }

    /**
     * 检查控制端心跳
     */
    private synchronized void checkControlHeartbeat() {
        if (currentControlClientId == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long timeSinceHeartbeat = now - lastControlHeartbeat;

        if (timeSinceHeartbeat > controlHeartbeatTimeout) {
            log.warn("控制端心跳超时: {}ms，重新选择控制端", timeSinceHeartbeat);
            selectNewControlClient();
        }
    }

    /**
     * 清理空闲客户端
     */
    private synchronized void cleanupIdleClients() {
        LocalDateTime now = LocalDateTime.now();
        List<String> toRemove = new ArrayList<>();

        for (Map.Entry<String, ClientInfo> entry : clients.entrySet()) {
            ClientInfo client = entry.getValue();
            long lastActivityMinutes = java.time.Duration.between(client.getLastHeartbeat(), now).toMinutes();

            // 检查超时（24小时）
            if (lastActivityMinutes > (websocketTimeout / 60000)) {
                log.info("客户端超时断开 - ID: {}, 类型: {}, 最后活动: {}分钟前",
                        client.getClientId(), client.getClientType(), lastActivityMinutes);
                toRemove.add(entry.getKey());
            }
        }

        // 移除超时客户端
        for (String clientId : toRemove) {
            unregisterClient(clientId);
        }

        if (!toRemove.isEmpty()) {
            log.info("清理了 {} 个空闲客户端", toRemove.size());
        }
    }

    /**
     * 获取客户端信息
     */
    public ClientInfo getClientInfo(String clientId) {
        return clients.get(clientId);
    }

    /**
     * 获取所有客户端
     */
    public Collection<ClientInfo> getAllClients() {
        return clients.values();
    }

    /**
     * 获取客户端数量
     */
    public int getClientCount() {
        return clients.size();
    }

    /**
     * 更新连接客户端列表
     */
    private void updateConnectedClients() {
        connectedClients.clear();
        connectedClients.addAll(clients.values());
    }

    /**
     * 客户端信息类
     */
    @Getter
    @Setter
    public static class ClientInfo {
        private String clientId;
        private String clientType; // control, display_a, display_b
        private String clientName;
        private LocalDateTime registeredAt;
        private LocalDateTime lastHeartbeat;
        private String status;
    }
}