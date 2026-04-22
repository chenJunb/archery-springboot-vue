/**
 * localStorage 持久化 composable
 * 用于保存和恢复应用状态
 */

import { ref, watch } from 'vue'
import { logService } from '@/services/logService'

const STORAGE_KEY_PREFIX = 'archery-timer_'

export function useLocalStorage() {
    /**
     * 保存值到 localStorage
     */
    function setItem(key, value) {
        try {
            const storageKey = STORAGE_KEY_PREFIX + key
            const serialized = JSON.stringify(value)
            localStorage.setItem(storageKey, serialized)
            logService.debug('localStorage 已保存', { key, size: serialized.length })
        } catch (error) {
            logService.error('localStorage 保存失败', { key, error: error.message })
        }
    }

    /**
     * 从 localStorage 获取值
     */
    function getItem(key, defaultValue = null) {
        try {
            const storageKey = STORAGE_KEY_PREFIX + key
            const serialized = localStorage.getItem(storageKey)
            if (serialized === null) {
                return defaultValue
            }
            const value = JSON.parse(serialized)
            logService.debug('localStorage 已读取', { key })
            return value
        } catch (error) {
            logService.error('localStorage 读取失败', { key, error: error.message })
            return defaultValue
        }
    }

    /**
     * 从 localStorage 删除值
     */
    function removeItem(key) {
        try {
            const storageKey = STORAGE_KEY_PREFIX + key
            localStorage.removeItem(storageKey)
            logService.debug('localStorage 项已删除', { key })
        } catch (error) {
            logService.error('localStorage 删除失败', { key, error: error.message })
        }
    }

    /**
     * 清空所有应用相关的 localStorage
     */
    function clear() {
        try {
            const keysToDelete = []
            for (let i = 0; i < localStorage.length; i++) {
                const key = localStorage.key(i)
                if (key && key.startsWith(STORAGE_KEY_PREFIX)) {
                    keysToDelete.push(key)
                }
            }
            keysToDelete.forEach(key => localStorage.removeItem(key))
            logService.info('localStorage 已清空', { itemsCleared: keysToDelete.length })
        } catch (error) {
            logService.error('localStorage 清空失败', { error: error.message })
        }
    }

    /**
     * 创建响应式的 localStorage 值
     */
    function useStorageRef(key, defaultValue = null, watchOptions = {}) {
        const value = ref(getItem(key, defaultValue))

        watch(
            value,
            (newValue) => {
                setItem(key, newValue)
            },
            { ...watchOptions, deep: true }
        )

        return value
    }

    /**
     * 保存计时器状态
     */
    function saveTimerState(state) {
        const timerState = {
            matchTypeId: state.matchTypeId,
            abMode: state.abMode,
            preparationTime: state.preparationTime,
            competitionTime: state.competitionTime,
            yellowLightTime: state.yellowLightTime,
            aPrompt: state.aPrompt,
            bPrompt: state.bPrompt,
            screenAEnabled: state.screenAEnabled,
            screenBEnabled: state.screenBEnabled,
            timestamp: Date.now()
        }
        setItem('timerState', timerState)
        logService.event('TIMER_STATE_SAVED', { matchTypeId: state.matchTypeId })
    }

    /**
     * 恢复计时器状态
     */
    function restoreTimerState() {
        const state = getItem('timerState', null)
        if (state) {
            logService.event('TIMER_STATE_RESTORED', { matchTypeId: state.matchTypeId, age: Date.now() - state.timestamp })
        }
        return state
    }

    /**
     * 保存用户偏好设置
     */
    function savePreferences(preferences) {
        const prefs = {
            isMuted: preferences.isMuted,
            volume: preferences.volume,
            language: preferences.language,
            theme: preferences.theme,
            timestamp: Date.now()
        }
        setItem('preferences', prefs)
        logService.event('PREFERENCES_SAVED', { theme: preferences.theme })
    }

    /**
     * 恢复用户偏好设置
     */
    function restorePreferences() {
        const prefs = getItem('preferences', {
            isMuted: false,
            volume: 1.0,
            language: 'zh-CN',
            theme: 'light'
        })
        logService.event('PREFERENCES_RESTORED', { theme: prefs.theme })
        return prefs
    }

    /**
     * 获取 localStorage 使用情况
     */
    function getStorageInfo() {
        let totalSize = 0
        let itemCount = 0
        const items = []

        for (let i = 0; i < localStorage.length; i++) {
            const key = localStorage.key(i)
            if (key && key.startsWith(STORAGE_KEY_PREFIX)) {
                const value = localStorage.getItem(key)
                const size = value ? value.length : 0
                totalSize += size
                itemCount++
                items.push({
                    key: key.replace(STORAGE_KEY_PREFIX, ''),
                    size: size
                })
            }
        }

        return {
            totalSize: totalSize,
            itemCount: itemCount,
            items: items,
            maxSize: 5 * 1024 * 1024 // 假设 5MB 限制
        }
    }

    return {
        setItem,
        getItem,
        removeItem,
        clear,
        useStorageRef,
        saveTimerState,
        restoreTimerState,
        savePreferences,
        restorePreferences,
        getStorageInfo
    }
}

export default useLocalStorage
