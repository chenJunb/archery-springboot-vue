package com.archery.timer.model.dto;

import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebSocketMessageDTO {
    private String type; // timer_state, control_command, client_registered, client_disconnected, error
    private Object data;
    private Long timestamp;
    private String clientId; // 发送消息的客户端ID

    public static WebSocketMessageDTO timerState(TimerStateDTO state, String clientId) {
        return new WebSocketMessageDTO("timer_state", state, System.currentTimeMillis(), clientId);
    }

    public static WebSocketMessageDTO controlCommand(String command, Object data, String clientId) {
        return new WebSocketMessageDTO("control_command", new ControlCommandDTO(command, data),
                System.currentTimeMillis(), clientId);
    }

    public static WebSocketMessageDTO clientRegistered(String clientId, String clientType) {
        ClientInfoDTO info = new ClientInfoDTO(clientId, clientType, "connected");
        return new WebSocketMessageDTO("client_registered", info, System.currentTimeMillis(), clientId);
    }

    public static WebSocketMessageDTO clientDisconnected(String clientId) {
        return new WebSocketMessageDTO("client_disconnected", clientId, System.currentTimeMillis(), clientId);
    }

    public static WebSocketMessageDTO error(String message, String clientId) {
        return new WebSocketMessageDTO("error", message, System.currentTimeMillis(), clientId);
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ControlCommandDTO {
        private String command; // start, pause, reset, select_match, set_ab_mode, toggle_screen
        private Object data;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClientInfoDTO {
        private String clientId;
        private String clientType; // control, display_a, display_b
        private String status;
    }
}