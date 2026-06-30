package com.archery.timer.model.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * 比赛轮次记录
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RoundRecordDto {


    /**
     * 当前轮次key
     */
    private String launchRoundCurrentKey;

    /**
     * 当前轮次
     */
    private Integer round;

    /**
     * 当前论第几次
     */
    private Integer session;

    /**
     * 配置每轮几次
     */
    private Integer roundSession;
    /**
     * 轮次key集合
     */
    private Map<Integer, String> launchRoundKeys;

    /**
     * 轮次明细
      */
    private List<RoundDetailDTO> details;

    /**
     * 排序方式
     * ASC      升序
     * DESC     降序
     */
    private String sortMethod;


    /**
     * 初始化
     * @param launchRoundKeys
     * @return
     */
    public RoundRecordDto init(Map<Integer, String> launchRoundKeys,Integer roundSession){
//        RoundRecordDto roundRecordDto = new RoundRecordDto();
//        roundRecordDto.setLaunchRoundKeys(getLaunchRoundKeys());
//        roundRecordDto.setRound(1);
//        roundRecordDto.setLaunchRoundCurrentKey(launchRoundKeys.get(0));

        this.round = 1;
        this.session = 1;
        this.launchRoundKeys = launchRoundKeys;
        this.roundSession = roundSession;
        this.launchRoundCurrentKey = launchRoundKeys.get(0);
        this.sortMethod = "ASC"; //默认升序

        List<RoundDetailDTO> details = new ArrayList<>();
        RoundDetailDTO detail = new RoundDetailDTO();
        detail.setNo(1);
        detail.setNoByRound(0);
        detail.setSession(1); // 每轮次数默认从1开始
        detail.setRound(round);
        detail.setLaunchRoundCurrentKey(launchRoundCurrentKey);
        if(roundSession != null && roundSession > 1){
            detail.setRoundDescription("第"+round+"轮"+session+"次"+launchRoundCurrentKey);
        } else {
            detail.setRoundDescription("第"+round+"轮"+launchRoundCurrentKey);
        }

        detail.setStatus("not_started");
        details.add(detail);
        this.details = details;

        return this;
    }
}
