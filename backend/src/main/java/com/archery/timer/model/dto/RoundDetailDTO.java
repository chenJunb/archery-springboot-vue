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
     * 轮次
     */
    private int round;
    /**
     * 轮次中的序号 从0开始 方便后续获取下一位轮次key
     */
    private int noByRound;

    /**
     * 次数，用于记录第X轮第X次
     */
    private int session;

    private String launchRoundCurrentKey;

    /**
     * 当前轮次文案描述
     * 当设定次数为1的时候 默认不展示次数
     * "第"+round+"轮"+launchRoundCurrentkey
     * 当设定次数>1的时候
     * "第"+round+"轮"+session+"次"+launchRoundCurrentkey
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
