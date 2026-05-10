package com.archery.timer.model.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TimerStateDTO {
    // 计时器状态
    private String status; // idle, running, paused, finished
    private String matchTypeId;
    private String matchTypeName;
    private String matchTypeCategory; // individual, team, mixed_team, custom
    private String matchTypeSubCategory; // ranking, elimination, final_phase, duel
    private String alternateType; // individual_alternate, team_alternate, mixed_team_alternate

    // 当前阶段信息
    private Integer currentStageIndex;
    private String currentStageName;
    private String currentStageColor;
    private Integer currentStageDuration;
    private Integer currentStageElapsed;
    private Integer currentStageRemaining;

    // 全局计时信息
    private Integer totalElapsed;
    private Integer totalRemaining;

    // AB屏模式和状态
    private String abMode; // alternate, sync, only_a, only_b
    private String activeScreen; // A, B (仅在alternate模式下有效)
    private Boolean screenAEnabled;
    private Boolean screenBEnabled;

    // AB屏详细计时信息
    private Integer screenARemaining; // A屏剩余时间
    private Integer screenBRemaining; // B屏剩余时间
    private String screenAStatus; // A屏状态: running, paused
    private String screenBStatus; // B屏状态: running, paused

    // ✅ 新增：A屏独立的阶段信息（交替模式下使用）
    private Integer screenAStageIndex;
    private String screenAStageName;
    private String screenAStageColor;
    private Integer screenAStageDuration;
    private Integer screenAStageElapsed;
    private Integer screenAStageRemaining;

    // ✅ 新增：B屏独立的阶段信息（交替模式下使用）
    private Integer screenBStageIndex;
    private String screenBStageName;
    private String screenBStageColor;
    private Integer screenBStageDuration;
    private Integer screenBStageElapsed;
    private Integer screenBStageRemaining;

    // 提示文案
    private String aPrompt;
    private String bPrompt;

    // 时间配置（可编辑）
    private Integer preparationTime;
    private Integer competitionTime;
    private Integer yellowLightTime;

    // 控制信息
    private String controlClientId; // 当前控制端ID
    private Integer connectedClients; // 已连接客户端数

    // 时间戳
    private LocalDateTime lastUpdateTime;
    private Long timestamp; // 服务器时间戳

    // 配置信息
    private Boolean soundEnabled;
    private Integer volume;


    private Boolean isRound; //是否开启比赛轮次
    /**
     * 比赛轮次
     */
    private RoundRecordDto roundRecord;

    public TimerStateDTO() {
        this.soundEnabled = true;
        this.volume = 80;
        this.screenAEnabled = true;
        this.screenBEnabled = true;
        this.abMode = "alternate";
        this.activeScreen = "A";
        this.aPrompt = "选手A准备";
        this.bPrompt = "选手B准备";
        this.screenAStatus = "paused";
        this.screenBStatus = "paused";
    }

    // 创建空闲状态
    public static TimerStateDTO idleState() {
        TimerStateDTO state = new TimerStateDTO();
        state.setStatus("idle");
        state.setAbMode("alternate");
        state.setActiveScreen("A");
        state.setScreenAEnabled(true);
        state.setScreenBEnabled(true);
        state.setConnectedClients(0);
        state.setLastUpdateTime(LocalDateTime.now());
        state.setTimestamp(System.currentTimeMillis());
        state.setIsRound(false);
        return state;
    }
}