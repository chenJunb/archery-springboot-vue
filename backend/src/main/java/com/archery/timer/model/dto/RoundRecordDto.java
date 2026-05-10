package com.archery.timer.model.dto;


import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;


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
     * 轮次key集合
     */
    private List<String> launchRoundKeys;

    /**
     * 轮次明细
      */
    private List<RoundDetailDTO> details;




    /**
     * 初始化
     * @param launchRoundKeys
     * @return
     */
    public RoundRecordDto init(List<String> launchRoundKeys){
//        RoundRecordDto roundRecordDto = new RoundRecordDto();
//        roundRecordDto.setLaunchRoundKeys(getLaunchRoundKeys());
//        roundRecordDto.setRound(1);
//        roundRecordDto.setLaunchRoundCurrentKey(launchRoundKeys.get(0));

        this.round = 1;
        this.launchRoundKeys = launchRoundKeys;
        this.launchRoundCurrentKey = launchRoundKeys.get(0);

        List<RoundDetailDTO> details = new ArrayList<>();
        RoundDetailDTO detail = new RoundDetailDTO();
        detail.setNo(1);
        detail.setNoByRound(0);
        detail.setRoundDescription("第"+round+"轮"+launchRoundCurrentKey);
        detail.setStatus("not_started");
        details.add(detail);
        this.details = details;

        return this;
    }
}
