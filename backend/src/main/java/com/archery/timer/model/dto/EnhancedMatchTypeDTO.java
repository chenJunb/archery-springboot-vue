package com.archery.timer.model.dto;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EnhancedMatchTypeDTO {
    private String id;
    private String name;
    private String chineseName; // 中文名称，用于显示
    private String category; // individual, team, mixed_team
    private String subCategory; // ranking, elimination, final_phase, duel
    private String defaultScreenMode; // sync, alternate, only_a, only_b

    // 时间配置
    private Integer preparationTime;  // 准备时间(秒)
    private Integer competitionTime;  // 比赛时间(秒)
    private Integer yellowLightTime;  // 黄灯时间(秒)

    // 音频配置
    private String preparationSound;  // 准备阶段音频
    private String competitionSound; // 比赛阶段音频
    private String yellowLightSound; // 黄灯阶段音频

    // AB交替模式配置
    private Boolean supportABAlternate;      // 是否支持AB交替
    private String alternateType;            // individual_alternate, team_alternate, mixed_team_alternate
    private Boolean resetOnSwitchIndividual; // 个人赛切换时是否清零
    private Boolean pauseOnSwitchTeam;       // 团队赛切换时是否暂停保留

    // 阶段配置（兼容现有系统）
    private List<StageDTO> stages;
    private Integer totalTime;
    private String description;
    private Boolean customizable;

    // 显示配置
    private String defaultAPrompt;   // A屏默认提示文案
    private String defaultBPrompt;   // B屏默认提示文案
    private String backgroundColor;  // 背景颜色
    private String fontColor;        // 字体颜色

    // 比赛规则说明
    private Map<String, Object> rules; // 比赛规则描述

    @Data
    public static class StageDTO {
        private String name;
        private String displayName;
        private Integer duration; // 秒
        private String color;     // 颜色代码
        private String sound;     // prepare, shooting, end
        private Boolean isYellowLight; // 是否为黄灯阶段
        private Integer yellowLightDuration; // 黄灯持续时间(秒)
    }

    public static class TimeConfig {
        private Integer preparation;
        private Integer competition;
        private Integer yellowLight;
        private String lightPattern; // 红绿灯样式: red-green-yellow, red-green-only等

        public Integer getPreparation() { return preparation; }
        public void setPreparation(Integer preparation) { this.preparation = preparation; }
        public Integer getCompetition() { return competition; }
        public void setCompetition(Integer competition) { this.competition = competition; }
        public Integer getYellowLight() { return yellowLight; }
        public void setYellowLight(Integer yellowLight) { this.yellowLight = yellowLight; }
        public String getLightPattern() { return lightPattern; }
        public void setLightPattern(String lightPattern) { this.lightPattern = lightPattern; }
    }

    public static class AudioConfig {
        private String preparation; // 准备音频
        private String competition; // 比赛音频
        private String yellowLight; // 黄灯音频
        private String endWarning;  // 结束警告音

        public String getPreparation() { return preparation; }
        public void setPreparation(String preparation) { this.preparation = preparation; }
        public String getCompetition() { return competition; }
        public void setCompetition(String competition) { this.competition = competition; }
        public String getYellowLight() { return yellowLight; }
        public void setYellowLight(String yellowLight) { this.yellowLight = yellowLight; }
        public String getEndWarning() { return endWarning; }
        public void setEndWarning(String endWarning) { this.endWarning = endWarning; }
    }
}