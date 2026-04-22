package com.archery.timer.controller;

import com.archery.timer.model.dto.EnhancedMatchTypeDTO;
import com.archery.timer.model.dto.MatchTypeDTO;
import com.archery.timer.service.MatchTypeConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/match-types")
@RequiredArgsConstructor
@Slf4j
public class MatchTypeController {

    private final MatchTypeConfigService matchTypeConfigService;

    /**
     * 获取所有比赛类型
     */
    @GetMapping
    public Map<String, Object> getAllMatchTypes() {
        Map<String, Object> response = new HashMap<>();
        List<EnhancedMatchTypeDTO> allTypes = matchTypeConfigService.getAllMatchTypes();
        response.put("success", true);
        response.put("data", allTypes);
        response.put("count", allTypes.size());
        return response;
    }

    /**
     * 根据ID获取比赛类型
     */
    @GetMapping("/{id}")
    public Map<String, Object> getMatchType(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        EnhancedMatchTypeDTO matchType = matchTypeConfigService.getMatchType(id);

        if (matchType != null) {
            response.put("success", true);
            response.put("data", matchType);
        } else {
            response.put("success", false);
            response.put("message", "比赛类型不存在: " + id);
        }

        return response;
    }

    /**
     * 根据类别获取比赛类型
     */
    @GetMapping("/category/{category}")
    public Map<String, Object> getMatchTypesByCategory(@PathVariable String category) {
        Map<String, Object> response = new HashMap<>();
        List<EnhancedMatchTypeDTO> matchTypes = matchTypeConfigService.getMatchTypesByCategory(category);

        response.put("success", true);
        response.put("data", matchTypes);
        response.put("count", matchTypes.size());

        return response;
    }

    /**
     * 获取所有类别
     */
    @GetMapping("/categories")
    public Map<String, Object> getAllCategories() {
        Map<String, Object> response = new HashMap<>();
        List<String> categories = matchTypeConfigService.getAllCategories();

        response.put("success", true);
        response.put("data", categories);
        response.put("count", categories.size());

        return response;
    }

    /**
     * 转换为兼容格式
     */
    @GetMapping("/{id}/legacy")
    public Map<String, Object> getMatchTypeLegacy(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        EnhancedMatchTypeDTO enhanced = matchTypeConfigService.getMatchType(id);

        if (enhanced != null) {
            MatchTypeDTO legacy = matchTypeConfigService.convertToLegacyFormat(enhanced);
            response.put("success", true);
            response.put("data", legacy);
        } else {
            response.put("success", false);
            response.put("message", "比赛类型不存在: " + id);
        }

        return response;
    }

    /**
     * 获取AB屏模式配置
     */
    @GetMapping("/{id}/screen-mode")
    public Map<String, Object> getScreenModeConfig(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        EnhancedMatchTypeDTO matchType = matchTypeConfigService.getMatchType(id);

        if (matchType != null) {
            MatchTypeConfigService.ScreenModeConfig config = matchTypeConfigService.getScreenModeConfig(id, null);
            response.put("success", true);
            response.put("data", config);
        } else {
            response.put("success", false);
            response.put("message", "比赛类型不存在: " + id);
        }

        return response;
    }

    /**
     * 获取默认提示文案
     */
    @GetMapping("/{id}/default-prompts")
    public Map<String, Object> getDefaultPrompts(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        EnhancedMatchTypeDTO matchType = matchTypeConfigService.getMatchType(id);

        if (matchType != null) {
            Map<String, String> prompts = new HashMap<>();
            prompts.put("a", matchType.getDefaultAPrompt());
            prompts.put("b", matchType.getDefaultBPrompt());

            response.put("success", true);
            response.put("data", prompts);
        } else {
            response.put("success", false);
            response.put("message", "比赛类型不存在: " + id);
        }

        return response;
    }

    /**
     * 获取时间配置
     */
    @GetMapping("/{id}/time-config")
    public Map<String, Object> getTimeConfig(@PathVariable String id) {
        Map<String, Object> response = new HashMap<>();
        EnhancedMatchTypeDTO matchType = matchTypeConfigService.getMatchType(id);

        if (matchType != null) {
            Map<String, Integer> timeConfig = new HashMap<>();
            timeConfig.put("preparation", matchType.getPreparationTime());
            timeConfig.put("competition", matchType.getCompetitionTime());
            timeConfig.put("yellowLight", matchType.getYellowLightTime());
            timeConfig.put("total", matchType.getTotalTime());

            response.put("success", true);
            response.put("data", timeConfig);
        } else {
            response.put("success", false);
            response.put("message", "比赛类型不存在: " + id);
        }

        return response;
    }

    // REST API版本，WebSocket功能已移到EnhancedWebSocketController
}