package com.archery.timer.service;

import com.archery.timer.model.dto.EnhancedMatchTypeDTO;
import com.archery.timer.model.dto.MatchTypeDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service
@Slf4j
public class MatchTypeConfigService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private Map<String, EnhancedMatchTypeDTO> matchTypes = new HashMap<>();
    private Map<String, List<EnhancedMatchTypeDTO>> matchTypesByCategory = new HashMap<>();

    // 默认配置（如果配置文件不存在则使用）
    private final Map<String, EnhancedMatchTypeDTO> defaultMatchTypes;

    public MatchTypeConfigService() {
        this.defaultMatchTypes = createDefaultMatchTypes();
    }

    @PostConstruct
    public void init() {
        loadMatchTypeConfig();
        organizeByCategory();
        log.info("加载比赛类型配置完成，共 {} 种类型", matchTypes.size());
    }

    /**
     * 从配置文件加载比赛类型配置
     * ✅ 改进：添加文件验证、配置验证和错误区分
     */
    private void loadMatchTypeConfig() {
        try {
            // 尝试从外部配置文件加载
            Path externalConfigPath = Paths.get("config/match-types.json");

            // ✅ 修复5.2: 检查是否真的是文件而不是目录
            if (Files.exists(externalConfigPath) && Files.isRegularFile(externalConfigPath)) {
                try (InputStream inputStream = Files.newInputStream(externalConfigPath)) {
                    Map<String, EnhancedMatchTypeDTO> loaded = objectMapper.readValue(
                            inputStream,
                            new TypeReference<Map<String, EnhancedMatchTypeDTO>>() {}
                    );

                    // ✅ 修复5.3: 验证加载的配置
                    if (validateMatchTypesConfig(loaded)) {
                        matchTypes = loaded;
                        log.info("✅ 从外部配置文件加载了 {} 种比赛类型", loaded.size());
                        return;
                    } else {
                        // ✅ 修复5.4: 区分配置无效和文件不存在
                        log.error("❌ 外部配置文件格式无效或必需字段缺失，将使用默认配置");
                    }
                } catch (IOException e) {
                    log.error("❌ 解析外部配置文件失败: {}", e.getMessage());
                }
            } else if (Files.exists(externalConfigPath)) {
                log.warn("⚠️ config/match-types.json 是一个目录而非文件，跳过");
            }

            // 尝试从 classpath 加载
            Resource resource = new ClassPathResource("config/match-types.json");
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    Map<String, EnhancedMatchTypeDTO> loaded = objectMapper.readValue(
                            inputStream,
                            new TypeReference<Map<String, EnhancedMatchTypeDTO>>() {}
                    );

                    // ✅ 修复5.3: 验证加载的配置
                    if (validateMatchTypesConfig(loaded)) {
                        matchTypes = loaded;
                        log.info("✅ 从 classpath 配置文件加载了 {} 种比赛类型", loaded.size());
                        return;
                    } else {
                        log.error("❌ Classpath配置文件格式无效或必需字段缺失，将使用默认配置");
                    }
                } catch (IOException e) {
                    log.error("❌ 解析 classpath 配置文件失败: {}", e.getMessage());
                }
            }

            // 如果配置文件不存在，使用默认配置
            log.info("📋 未找到配置文件，使用默认比赛类型配置");
            matchTypes = new HashMap<>(defaultMatchTypes);

        } catch (Exception e) {
            log.error("❌ 加载比赛类型配置失败，使用默认配置", e);
            matchTypes = new HashMap<>(defaultMatchTypes);
        }
    }

    /**
     * ✅ 新增：验证配置的有效性
     * 检查必需的字段和数据完整性
     */
    private boolean validateMatchTypesConfig(Map<String, EnhancedMatchTypeDTO> config) {
        if (config == null || config.isEmpty()) {
            log.warn("⚠️ 配置为空或null");
            return false;
        }

        // 验证每个比赛类型的必需字段
        for (Map.Entry<String, EnhancedMatchTypeDTO> entry : config.entrySet()) {
            String key = entry.getKey();
            EnhancedMatchTypeDTO matchType = entry.getValue();

            if (matchType == null) {
                log.warn("⚠️ 配置项 '{}' 的值为null", key);
                return false;
            }

            // 检查必需字段
            if (matchType.getId() == null || matchType.getId().isEmpty()) {
                log.warn("⚠️ 配置项 '{}' 缺少id字段", key);
                return false;
            }

            if (matchType.getChineseName() == null || matchType.getChineseName().isEmpty()) {
                log.warn("⚠️ 配置项 '{}' 缺少chineseName字段", key);
                return false;
            }

            if (matchType.getTotalTime() == null || matchType.getTotalTime() < 0) {
                log.warn("⚠️ 配置项 '{}' 的totalTime无效", key);
                return false;
            }
        }

        return true;
    }

    /**
     * 组织比赛类型按类别分组
     */
    private void organizeByCategory() {
        matchTypesByCategory.clear();

        matchTypes.values().forEach(matchType -> {
            String category = matchType.getCategory();
            matchTypesByCategory
                    .computeIfAbsent(category, k -> new ArrayList<>())
                    .add(matchType);
        });
    }

    /**
     * 获取所有比赛类型
     */
    public List<EnhancedMatchTypeDTO> getAllMatchTypes() {
        return new ArrayList<>(matchTypes.values());
    }

    /**
     * 根据ID获取比赛类型
     */
    /**
     * ✅ 改进：添加null返回警告日志
     */
    public EnhancedMatchTypeDTO getMatchType(String id) {
        EnhancedMatchTypeDTO result = matchTypes.get(id);

        // ✅ 修复5.6: Null返回时添加警告日志
        if (result == null) {
            log.warn("⚠️ 未找到比赛类型: {}", id);
        }

        return result;
    }

    /**
     * 根据类别获取比赛类型
     */
    public List<EnhancedMatchTypeDTO> getMatchTypesByCategory(String category) {
        return matchTypesByCategory.getOrDefault(category, Collections.emptyList());
    }

    /**
     * 获取所有类别
     */
    public List<String> getAllCategories() {
        return new ArrayList<>(matchTypesByCategory.keySet());
    }

    /**
     * 转换为兼容的旧格式
     */
    public MatchTypeDTO convertToLegacyFormat(EnhancedMatchTypeDTO enhanced) {
        if (enhanced == null) return null;

        MatchTypeDTO legacy = new MatchTypeDTO();
        legacy.setId(enhanced.getId());
        legacy.setName(enhanced.getName());
        legacy.setCategory(enhanced.getCategory());
        legacy.setDescription(enhanced.getDescription());
        legacy.setCustomizable(enhanced.getCustomizable());
        legacy.setTotalTime(enhanced.getTotalTime());

        // 转换阶段配置
        List<MatchTypeDTO.StageDTO> legacyStages = new ArrayList<>();

        // 准备阶段
        if (enhanced.getPreparationTime() != null && enhanced.getPreparationTime() > 0) {
            MatchTypeDTO.StageDTO prepStage = new MatchTypeDTO.StageDTO();
            prepStage.setName("准备");
            prepStage.setDuration(enhanced.getPreparationTime());
            prepStage.setColor("#FF0000");
            prepStage.setSound(enhanced.getPreparationSound() != null ? enhanced.getPreparationSound() : "prepare");
            legacyStages.add(prepStage);
        }

        // 比赛阶段
        if (enhanced.getCompetitionTime() != null && enhanced.getCompetitionTime() > 0) {
            MatchTypeDTO.StageDTO compStage = new MatchTypeDTO.StageDTO();
            compStage.setName("比赛");
            compStage.setDuration(enhanced.getCompetitionTime());
            compStage.setColor("#00FF00");
            compStage.setSound(enhanced.getCompetitionSound() != null ? enhanced.getCompetitionSound() : "shooting");
            legacyStages.add(compStage);
        }

        legacy.setStages(legacyStages);
        return legacy;
    }

    /**
     * 获取AB屏模式配置
     */
    public ScreenModeConfig getScreenModeConfig(String matchTypeId, String abMode) {
        EnhancedMatchTypeDTO matchType = getMatchType(matchTypeId);
        if (matchType == null) {
            return getDefaultScreenModeConfig();
        }

        ScreenModeConfig config = new ScreenModeConfig();
        config.setDefaultMode(matchType.getDefaultScreenMode());
        config.setSupportABAlternate(matchType.getSupportABAlternate());
        config.setAlternateType(matchType.getAlternateType());
        config.setResetOnSwitchIndividual(matchType.getResetOnSwitchIndividual());
        config.setPauseOnSwitchTeam(matchType.getPauseOnSwitchTeam());
        config.setDefaultAPrompt(matchType.getDefaultAPrompt());
        config.setDefaultBPrompt(matchType.getDefaultBPrompt());

        return config;
    }

    /**
     * 获取默认的AB屏模式配置
     */
    public ScreenModeConfig getDefaultScreenModeConfig() {
        ScreenModeConfig config = new ScreenModeConfig();
        config.setDefaultMode("sync");
        config.setSupportABAlternate(true);
        config.setAlternateType("individual_alternate");
        config.setResetOnSwitchIndividual(true);
        config.setPauseOnSwitchTeam(false);
        config.setDefaultAPrompt("A屏");
        config.setDefaultBPrompt("B屏");
        return config;
    }

    /**
     * 屏幕模式配置类
     */
    public static class ScreenModeConfig {
        private String defaultMode;
        private Boolean supportABAlternate;
        private String alternateType;
        private Boolean resetOnSwitchIndividual;
        private Boolean pauseOnSwitchTeam;
        private String defaultAPrompt;
        private String defaultBPrompt;

        public String getDefaultMode() { return defaultMode; }
        public void setDefaultMode(String defaultMode) { this.defaultMode = defaultMode; }

        public Boolean getSupportABAlternate() { return supportABAlternate; }
        public void setSupportABAlternate(Boolean supportABAlternate) { this.supportABAlternate = supportABAlternate; }

        public String getAlternateType() { return alternateType; }
        public void setAlternateType(String alternateType) { this.alternateType = alternateType; }

        public Boolean getResetOnSwitchIndividual() { return resetOnSwitchIndividual; }
        public void setResetOnSwitchIndividual(Boolean resetOnSwitchIndividual) { this.resetOnSwitchIndividual = resetOnSwitchIndividual; }

        public Boolean getPauseOnSwitchTeam() { return pauseOnSwitchTeam; }
        public void setPauseOnSwitchTeam(Boolean pauseOnSwitchTeam) { this.pauseOnSwitchTeam = pauseOnSwitchTeam; }

        public String getDefaultAPrompt() { return defaultAPrompt; }
        public void setDefaultAPrompt(String defaultAPrompt) { this.defaultAPrompt = defaultAPrompt; }

        public String getDefaultBPrompt() { return defaultBPrompt; }
        public void setDefaultBPrompt(String defaultBPrompt) { this.defaultBPrompt = defaultBPrompt; }
    }

    /**
     * 创建默认的比赛类型配置
     */
    private Map<String, EnhancedMatchTypeDTO> createDefaultMatchTypes() {
        Map<String, EnhancedMatchTypeDTO> types = new HashMap<>();

        // 1. 个人排名赛
        EnhancedMatchTypeDTO personalRanking = createMatchType(
                "personal_ranking",
                "个人排名赛",
                "个人排名赛",
                "individual",
                "ranking",
                createTimeConfig(10, 180, 30),
                createAudioConfig("prepare", "shooting", "end"),
                "sync",
                true,
                "individual_alternate",
                true,
                false,
                "选手A准备",
                "选手B准备",
                "个人、团队排名赛"
        );
        types.put(personalRanking.getId(), personalRanking);

        // 2. 个人排名对决
        EnhancedMatchTypeDTO personalRankingDuel = createMatchType(
                "personal_ranking_duel",
                "个人排名对决",
                "个人排名对决",
                "individual",
                "duel",
                createTimeConfig(10, 30, 30),
                createAudioConfig("prepare", "shooting", "end"),
                "alternate",
                true,
                "individual_alternate",
                true,
                false,
                "选手A",
                "选手B",
                "个人排名对决"
        );
        types.put(personalRankingDuel.getId(), personalRankingDuel);

        // 3. 个人淘汰赛（统一）
        EnhancedMatchTypeDTO personalEliminationUnified = createMatchType(
                "personal_elimination_unified",
                "个人淘汰赛(统一)",
                "个人淘汰赛（统一）",
                "individual",
                "elimination",
                createTimeConfig(10, 90, 30),
                createAudioConfig("prepare", "shooting", "end"),
                "sync",
                false,
                "individual_alternate",
                false,
                false,
                "淘汰赛",
                "淘汰赛",
                "个人淘汰赛统一模式"
        );
        types.put(personalEliminationUnified.getId(), personalEliminationUnified);

        // 4. 个人淘汰赛（AB交替）
        EnhancedMatchTypeDTO personalEliminationAlternate = createMatchType(
                "personal_elimination_alternate",
                "个人淘汰赛(AB交替)",
                "个人淘汰赛（AB交替）",
                "individual",
                "elimination",
                createTimeConfig(10, 20, 0),
                createAudioConfig("prepare", "shooting", null),
                "alternate",
                true,
                "individual_alternate",
                true,
                false,
                "选手A射击",
                "选手B射击",
                "个人淘汰赛AB交替模式"
        );
        types.put(personalEliminationAlternate.getId(), personalEliminationAlternate);

        // 5. 团队淘汰赛（统一）
        EnhancedMatchTypeDTO teamEliminationUnified = createMatchType(
                "team_elimination_unified",
                "团队淘汰赛(统一)",
                "团队淘汰赛（统一）",
                "team",
                "elimination",
                createTimeConfig(10, 120, 30),
                createAudioConfig("prepare", "shooting", "end"),
                "sync",
                false,
                "team_alternate",
                false,
                false,
                "团队A",
                "团队B",
                "团队淘汰赛统一模式"
        );
        types.put(teamEliminationUnified.getId(), teamEliminationUnified);

        // 6. 团队淘汰赛决赛（AB交替）
        EnhancedMatchTypeDTO teamEliminationFinalAlternate = createMatchType(
                "team_elimination_final_alternate",
                "团队淘汰赛决赛(AB交替)",
                "团队淘汰赛决赛（AB交替）",
                "team",
                "final_phase",
                createTimeConfig(10, 120, 30),
                createAudioConfig("prepare", "shooting", "end"),
                "alternate",
                true,
                "team_alternate",
                false,
                true,
                "决赛A队",
                "决赛B队",
                "团队淘汰赛决赛AB交替模式"
        );
        types.put(teamEliminationFinalAlternate.getId(), teamEliminationFinalAlternate);

        // 7. 团队淘汰赛对决（AB交替）
        EnhancedMatchTypeDTO teamEliminationDuelAlternate = createMatchType(
                "team_elimination_duel_alternate",
                "团队淘汰赛对决(AB交替)",
                "团队淘汰赛对决（AB交替）",
                "team",
                "duel",
                createTimeConfig(10, 60, 30),
                createAudioConfig("prepare", "shooting", "end"),
                "alternate",
                true,
                "team_alternate",
                false,
                true,
                "对决A队",
                "对决B队",
                "团队淘汰赛对决AB交替模式"
        );
        types.put(teamEliminationDuelAlternate.getId(), teamEliminationDuelAlternate);

        // 8. 混团淘汰赛（统一）
        EnhancedMatchTypeDTO mixedTeamEliminationUnified = createMatchType(
                "mixed_team_elimination_unified",
                "混团淘汰赛(统一)",
                "混团淘汰赛（统一）",
                "mixed_team",
                "elimination",
                createTimeConfig(10, 80, 30),
                createAudioConfig("prepare", "shooting", "end"),
                "sync",
                false,
                "mixed_team_alternate",
                false,
                false,
                "混团A",
                "混团B",
                "混团淘汰赛统一模式"
        );
        types.put(mixedTeamEliminationUnified.getId(), mixedTeamEliminationUnified);

        // 9. 混团淘汰赛决赛（AB交替）
        EnhancedMatchTypeDTO mixedTeamEliminationFinalAlternate = createMatchType(
                "mixed_team_elimination_final_alternate",
                "混团淘汰赛决赛(AB交替)",
                "混团淘汰赛决赛（AB交替）",
                "mixed_team",
                "final_phase",
                createTimeConfig(10, 80, 30),
                createAudioConfig("prepare", "shooting", "end"),
                "alternate",
                true,
                "mixed_team_alternate",
                false,
                true,
                "混团决赛A",
                "混团决赛B",
                "混团淘汰赛决赛AB交替模式"
        );
        types.put(mixedTeamEliminationFinalAlternate.getId(), mixedTeamEliminationFinalAlternate);

        // 10. 自定义类型
        EnhancedMatchTypeDTO custom = createMatchType(
                "custom",
                "自定义",
                "自定义",
                "custom",
                null,
                createTimeConfig(30, 60, 0),
                createAudioConfig("prepare", "shooting", null),
                "sync",
                true,
                "individual_alternate",
                true,
                false,
                "自定义A",
                "自定义B",
                "自定义比赛类型"
        );
        custom.setCustomizable(true);
        types.put(custom.getId(), custom);

        return types;
    }

    /**
     * 辅助方法：创建比赛类型
     */
    private EnhancedMatchTypeDTO createMatchType(
            String id, String name, String chineseName, String category, String subCategory,
            EnhancedMatchTypeDTO.TimeConfig timeConfig, EnhancedMatchTypeDTO.AudioConfig audioConfig, String defaultScreenMode,
            Boolean supportABAlternate, String alternateType, Boolean resetOnSwitchIndividual,
            Boolean pauseOnSwitchTeam, String defaultAPrompt, String defaultBPrompt, String description) {

        EnhancedMatchTypeDTO matchType = new EnhancedMatchTypeDTO();
        matchType.setId(id);
        matchType.setName(name);
        matchType.setChineseName(chineseName);
        matchType.setCategory(category);
        matchType.setSubCategory(subCategory);
        matchType.setDefaultScreenMode(defaultScreenMode);

        matchType.setPreparationTime(timeConfig.getPreparation());
        matchType.setCompetitionTime(timeConfig.getCompetition());
        matchType.setYellowLightTime(timeConfig.getYellowLight());

        matchType.setPreparationSound(audioConfig.getPreparation());
        matchType.setCompetitionSound(audioConfig.getCompetition());
        matchType.setYellowLightSound(audioConfig.getYellowLight());

        matchType.setSupportABAlternate(supportABAlternate);
        matchType.setAlternateType(alternateType);
        matchType.setResetOnSwitchIndividual(resetOnSwitchIndividual);
        matchType.setPauseOnSwitchTeam(pauseOnSwitchTeam);

        matchType.setDefaultAPrompt(defaultAPrompt);
        matchType.setDefaultBPrompt(defaultBPrompt);

        matchType.setDescription(description);
        // 修复：总时间 = 准备时间 + 比赛时间（不包括黄灯时间）
        // 黄灯时间是比赛时间的最后N秒，不应该单独加入总时间
        matchType.setTotalTime(
                (timeConfig.getPreparation() != null ? timeConfig.getPreparation() : 0) +
                (timeConfig.getCompetition() != null ? timeConfig.getCompetition() : 0)
        );
        matchType.setCustomizable(false);

        return matchType;
    }

    /**
     * 辅助方法：创建时间配置
     */
    private EnhancedMatchTypeDTO.TimeConfig createTimeConfig(Integer prep, Integer comp, Integer yellow) {
        EnhancedMatchTypeDTO.TimeConfig config = new EnhancedMatchTypeDTO.TimeConfig();
        config.setPreparation(prep);
        config.setCompetition(comp);
        config.setYellowLight(yellow);
        return config;
    }

    /**
     * 辅助方法：创建音频配置
     */
    private EnhancedMatchTypeDTO.AudioConfig createAudioConfig(String prep, String comp, String yellow) {
        EnhancedMatchTypeDTO.AudioConfig config = new EnhancedMatchTypeDTO.AudioConfig();
        config.setPreparation(prep);
        config.setCompetition(comp);
        config.setYellowLight(yellow);
        return config;
    }
}