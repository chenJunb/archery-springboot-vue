package com.archery.timer.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MatchTypeDTO {
    private String id;                      // 唯一标识
    private String name;                    // 比赛类型名称
    private String category;                // 类别（personal/team/mixed）
    private Integer preparationTime;        // 准备时间（秒）
    private Integer competitionTime;        // 比赛时间（秒）
    private Integer yellowLightTime;        // 黄灯时间（秒）
    private String defaultScreenMode;       // 默认屏幕模式（sync/alternate/only_a/only_b）
    private Integer displayOrder;           // 显示顺序
    private Boolean enabled;                // 是否启用
    private String description;             // 描述

    // 新增字段，向后兼容
    private List<StageDTO> stages;          // 阶段定义（用于向后兼容）
    private Integer totalTime;              // 总时间（秒）
    private Boolean customizable;           // 是否可自定义

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StageDTO {
        private String name;
        private Integer duration;           // 秒
        private String color;
        private String sound;               // prepare, shooting, end
    }
}