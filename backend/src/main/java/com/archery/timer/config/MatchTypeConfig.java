package com.archery.timer.config;

import com.archery.timer.model.dto.MatchTypeDTO;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Configuration
public class MatchTypeConfig {

    @Bean
    public Map<String, MatchTypeDTO> matchTypes() {
        Map<String, MatchTypeDTO> types = new HashMap<>();

        // 个人排名: 10s 180s - 个人赛
        MatchTypeDTO personalRanking = new MatchTypeDTO();
        personalRanking.setId("personal_ranking");
        personalRanking.setName("个人排名");
        personalRanking.setCategory("individual");
        personalRanking.setStages(Arrays.asList(
                createStage("准备", 10, "#FF0000", "prepare"),      // 红色
                createStage("射击", 180, "#00FF00", "shooting")    // 绿色
        ));
        personalRanking.setTotalTime(190);
        personalRanking.setDescription("个人排名赛 - 10秒准备 + 180秒射击");
        types.put(personalRanking.getId(), personalRanking);

        // 个人排名对决: 10s 30s 30s 个人赛
        MatchTypeDTO personalRankingDuel = new MatchTypeDTO();
        personalRankingDuel.setId("personal_ranking_duel");
        personalRankingDuel.setName("个人排名对决");
        personalRankingDuel.setCategory("individual");
        personalRankingDuel.setStages(Arrays.asList(
                createStage("准备", 10, "#FF0000", "prepare"),      // 红色
                createStage("射击1", 30, "#00FF00", "shooting"),   // 绿色
                createStage("射击2", 30, "#00FF00", "shooting")    // 绿色
        ));
        personalRankingDuel.setTotalTime(70);
        personalRankingDuel.setDescription("个人排名对决 - 10秒准备 + 30秒射击 + 30秒射击");
        types.put(personalRankingDuel.getId(), personalRankingDuel);

        // 个人淘汰统一: 10s 90s 30s 个人赛
        MatchTypeDTO personalEliminationUnified = new MatchTypeDTO();
        personalEliminationUnified.setId("personal_elimination_unified");
        personalEliminationUnified.setName("个人淘汰统一");
        personalEliminationUnified.setCategory("individual");
        personalEliminationUnified.setStages(Arrays.asList(
                createStage("准备", 10, "#FF0000", "prepare"),      // 红色
                createStage("射击", 90, "#00FF00", "shooting"),    // 绿色
                createStage("结束", 30, "#FFFF00", "end")          // 黄色
        ));
        personalEliminationUnified.setTotalTime(130);
        personalEliminationUnified.setDescription("个人淘汰统一赛 - 10秒准备 + 90秒射击 + 30秒结束");
        types.put(personalEliminationUnified.getId(), personalEliminationUnified);

        // 个人淘汰: 10s 20s 0s 个人赛
        MatchTypeDTO personalElimination = new MatchTypeDTO();
        personalElimination.setId("personal_elimination");
        personalElimination.setName("个人淘汰");
        personalElimination.setCategory("individual");
        personalElimination.setStages(Arrays.asList(
                createStage("准备", 10, "#FF0000", "prepare"),      // 红色
                createStage("射击", 20, "#00FF00", "shooting")     // 绿色
        ));
        personalElimination.setTotalTime(30);
        personalElimination.setDescription("个人淘汰赛 - 10秒准备 + 20秒射击");
        types.put(personalElimination.getId(), personalElimination);

        // 团队淘汰决赛: 10s 120s 30s 团队赛
        MatchTypeDTO teamEliminationFinal = new MatchTypeDTO();
        teamEliminationFinal.setId("team_elimination_final");
        teamEliminationFinal.setName("团队淘汰决赛");
        teamEliminationFinal.setCategory("team");
        teamEliminationFinal.setStages(Arrays.asList(
                createStage("准备", 10, "#FF0000", "prepare"),      // 红色
                createStage("射击", 120, "#00FF00", "shooting"),   // 绿色
                createStage("结束", 30, "#FFFF00", "end")          // 黄色
        ));
        teamEliminationFinal.setTotalTime(160);
        teamEliminationFinal.setDescription("团队淘汰决赛 - 10秒准备 + 120秒射击 + 30秒结束");
        types.put(teamEliminationFinal.getId(), teamEliminationFinal);

        // 团队淘汰对决: 10s 60s 30s 团队赛
        MatchTypeDTO teamEliminationDuel = new MatchTypeDTO();
        teamEliminationDuel.setId("team_elimination_duel");
        teamEliminationDuel.setName("团队淘汰对决");
        teamEliminationDuel.setCategory("team");
        teamEliminationDuel.setStages(Arrays.asList(
                createStage("准备", 10, "#FF0000", "prepare"),      // 红色
                createStage("射击", 60, "#00FF00", "shooting"),    // 绿色
                createStage("结束", 30, "#FFFF00", "end")          // 黄色
        ));
        teamEliminationDuel.setTotalTime(100);
        teamEliminationDuel.setDescription("团队淘汰对决 - 10秒准备 + 60秒射击 + 30秒结束");
        types.put(teamEliminationDuel.getId(), teamEliminationDuel);

        // 混队淘汰决赛统一: 10s 80s 30s 团队赛
        MatchTypeDTO mixedTeamEliminationUnified = new MatchTypeDTO();
        mixedTeamEliminationUnified.setId("mixed_team_elimination_unified");
        mixedTeamEliminationUnified.setName("混队淘汰决赛统一");
        mixedTeamEliminationUnified.setCategory("team");
        mixedTeamEliminationUnified.setStages(Arrays.asList(
                createStage("准备", 10, "#FF0000", "prepare"),      // 红色
                createStage("射击", 80, "#00FF00", "shooting"),    // 绿色
                createStage("结束", 30, "#FFFF00", "end")          // 黄色
        ));
        mixedTeamEliminationUnified.setTotalTime(120);
        mixedTeamEliminationUnified.setDescription("混队淘汰决赛统一 - 10秒准备 + 80秒射击 + 30秒结束");
        types.put(mixedTeamEliminationUnified.getId(), mixedTeamEliminationUnified);

        // 混队淘汰决赛: 10s 80s 30s 团队赛
        MatchTypeDTO mixedTeamEliminationFinal = new MatchTypeDTO();
        mixedTeamEliminationFinal.setId("mixed_team_elimination_final");
        mixedTeamEliminationFinal.setName("混队淘汰决赛");
        mixedTeamEliminationFinal.setCategory("team");
        mixedTeamEliminationFinal.setStages(Arrays.asList(
                createStage("准备", 10, "#FF0000", "prepare"),      // 红色
                createStage("射击", 80, "#00FF00", "shooting"),    // 绿色
                createStage("结束", 30, "#FFFF00", "end")          // 黄色
        ));
        mixedTeamEliminationFinal.setTotalTime(120);
        mixedTeamEliminationFinal.setDescription("混队淘汰决赛 - 10秒准备 + 80秒射击 + 30秒结束");
        types.put(mixedTeamEliminationFinal.getId(), mixedTeamEliminationFinal);

        // 自定义
        MatchTypeDTO custom = new MatchTypeDTO();
        custom.setId("custom");
        custom.setName("自定义");
        custom.setCategory("custom");
        custom.setStages(Arrays.asList(
                createStage("阶段1", 30, "#FF0000", "prepare"),     // 红色
                createStage("阶段2", 60, "#00FF00", "shooting")    // 绿色
        ));
        custom.setTotalTime(90);
        custom.setDescription("自定义比赛类型 - 用户自定义时间和阶段");
        custom.setCustomizable(true);
        types.put(custom.getId(), custom);

        return types;
    }

    @Bean
    public Map<String, List<MatchTypeDTO>> matchTypesByCategory() {
        Map<String, MatchTypeDTO> allTypes = matchTypes();
        Map<String, List<MatchTypeDTO>> byCategory = new HashMap<>();

        byCategory.put("individual", Arrays.asList(
                allTypes.get("personal_ranking"),
                allTypes.get("personal_ranking_duel"),
                allTypes.get("personal_elimination_unified"),
                allTypes.get("personal_elimination")
        ));

        byCategory.put("team", Arrays.asList(
                allTypes.get("team_elimination_final"),
                allTypes.get("team_elimination_duel"),
                allTypes.get("mixed_team_elimination_unified"),
                allTypes.get("mixed_team_elimination_final")
        ));

        byCategory.put("custom", Arrays.asList(allTypes.get("custom")));

        return byCategory;
    }

    private MatchTypeDTO.StageDTO createStage(String name, int duration, String color, String sound) {
        MatchTypeDTO.StageDTO stage = new MatchTypeDTO.StageDTO();
        stage.setName(name);
        stage.setDuration(duration);
        stage.setColor(color);
        stage.setSound(sound);
        return stage;
    }
}