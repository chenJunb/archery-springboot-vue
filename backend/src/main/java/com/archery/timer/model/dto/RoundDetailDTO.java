package com.archery.timer.model.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 轮次明细
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoundDetailDTO {

    /**
     * 序号
     */
    private int no;

    /**
     * 轮次中的序号 从0开始 方便后续获取下一位轮次key
     */
    private int noByRound;

    /**
     * 当前轮次文案描述
     * "第"+round+"轮"+launchRoundCurrentkey
     */
    private String roundDescription;

    /**
     * 轮次状态
     * not_started 未开始
     * in_progress 进行中
     * ended       已结束
     */
    private String status;

}
