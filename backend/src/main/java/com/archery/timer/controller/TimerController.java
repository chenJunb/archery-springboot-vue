package com.archery.timer.controller;

import com.archery.timer.model.dto.MatchTypeDTO;
import com.archery.timer.model.dto.TimerStateDTO;
import com.archery.timer.service.TimerEngine;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/timer")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class TimerController {

    private final TimerEngine timerEngine;

    /**
     * 获取当前计时器状态
     */
    @GetMapping("/status")
    public ResponseEntity<TimerStateDTO> getStatus() {
        TimerStateDTO state = timerEngine.getState();
        return ResponseEntity.ok(state);
    }

    /**
     * 获取所有比赛类型
     */
    @GetMapping("/match-types")
    public ResponseEntity<Map<String, MatchTypeDTO>> getAllMatchTypes() {
        Map<String, MatchTypeDTO> matchTypes = timerEngine.getAllMatchTypes();
        return ResponseEntity.ok(matchTypes);
    }
}