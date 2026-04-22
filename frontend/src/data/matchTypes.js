/**
 * 射箭比赛计时系统 - 9种比赛类型定义
 */

export const MATCH_TYPES = [
    // 个人赛
    {
        id: 'personal_ranking',
        name: '个人排名',
        category: 'personal',
        order: 1,
        description: '10秒准备 + 180秒射击'
    },
    {
        id: 'personal_ranking_duel',
        name: '个人排名对决',
        category: 'personal',
        order: 2,
        description: '10秒准备 + 30秒×2射击'
    },
    {
        id: 'personal_elimination_unified',
        name: '个人淘汰（统一）',
        category: 'personal',
        order: 3,
        description: '10秒准备 + 90秒射击 + 30秒结束'
    },
    {
        id: 'personal_elimination_alternate',
        name: '个人淘汰（AB交替）',
        category: 'personal',
        order: 4,
        description: '10秒准备 + 20秒×2交替'
    },
    // 团队赛
    {
        id: 'team_unified',
        name: '团队（统一）',
        category: 'team',
        order: 5,
        description: '10秒准备 + 120秒'
    },
    {
        id: 'team_final_alternate',
        name: '团队决赛（AB交替）',
        category: 'team',
        order: 6,
        description: '10秒准备 + 120秒交替'
    },
    {
        id: 'team_duel_alternate',
        name: '团队对决（AB交替）',
        category: 'team',
        order: 7,
        description: '10秒准备 + 60秒交替'
    },
    // 混团赛
    {
        id: 'mixed_team_unified',
        name: '混团（统一）',
        category: 'mixed',
        order: 8,
        description: '10秒准备 + 80秒'
    },
    {
        id: 'mixed_team_final_alternate',
        name: '混团决赛（AB交替）',
        category: 'mixed',
        order: 9,
        description: '10秒准备 + 80秒交替'
    }
]

export const DEFAULT_PARAMS = {
    personal_ranking: { prep: 10, comp: 180, yellow: 30, mode: 'sync' },
    personal_ranking_duel: { prep: 10, comp: 30, yellow: 30, mode: 'alternate' },
    personal_elimination_unified: { prep: 10, comp: 90, yellow: 30, mode: 'sync' },
    personal_elimination_alternate: { prep: 10, comp: 20, yellow: 0, mode: 'alternate' },
    team_unified: { prep: 10, comp: 120, yellow: 30, mode: 'sync' },
    team_final_alternate: { prep: 10, comp: 120, yellow: 30, mode: 'alternate' },
    team_duel_alternate: { prep: 10, comp: 60, yellow: 30, mode: 'alternate' },
    mixed_team_unified: { prep: 10, comp: 80, yellow: 30, mode: 'sync' },
    mixed_team_final_alternate: { prep: 10, comp: 80, yellow: 30, mode: 'alternate' }
}

export function getDefaultParams(matchTypeId) {
    return DEFAULT_PARAMS[matchTypeId] || { prep: 10, comp: 180, yellow: 30, mode: 'sync' }
}

export function getMatchType(matchTypeId) {
    return MATCH_TYPES.find(t => t.id === matchTypeId)
}

export function getCategoryName(category) {
    const categoryMap = {
        personal: '个人赛',
        team: '团队赛',
        mixed: '混团赛'
    }
    return categoryMap[category] || '未知'
}

export function getMatchTypesByCategory(category) {
    return MATCH_TYPES.filter(t => t.category === category).sort((a, b) => a.order - b.order)
}

export function getAllCategories() {
    const categories = new Set(MATCH_TYPES.map(t => t.category))
    return Array.from(categories)
}
