package com.archery.timer.model.enums;

import lombok.Getter;

@Getter
public enum LightColor {
    RED("RED", "#FF0000", "红灯-准备"),
    GREEN("GREEN", "#00FF00", "绿灯-比赛"),
    YELLOW("YELLOW", "#FFFF00", "黄灯-最后阶段"),
    STANDBY("STANDBY", "#CCCCCC", "待机");

    private final String code;
    private final String hexColor;
    private final String description;

    LightColor(String code, String hexColor, String description) {
        this.code = code;
        this.hexColor = hexColor;
        this.description = description;
    }
}
