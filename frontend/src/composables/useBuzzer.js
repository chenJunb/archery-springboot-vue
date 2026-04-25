/**
 * 射箭比赛计时系统 - 鸣笛/声音播放 composable
 * ✅ 修复：使用单例模式防止内存泄漏
 */

import { ref } from 'vue'
import { Howl } from 'howler'
import { logService } from '@/services/logService'

// ✅ 修复：全局单例 - 所有组件共享同一份音频对象
let globalSounds = null

// ✅ 修复：获取或创建全局音频对象
function getGlobalSounds() {
    if (globalSounds === null) {
        logService.debug('首次创建全局音频对象（单例）')
        globalSounds = {
            beep1: new Howl({
                src: ['/sounds/countdown_audio.mp3'],
                volume: 0.7,
                onload: () => logService.debug('蜂鸣声1音频加载完成'),
                onerror: (id, error) => logService.error('蜂鸣声1音频加载失败', { error: error.toString() })
            }),
            beep2: new Howl({
                src: ['/sounds/countdown_audio.mp3'],
                volume: 0.7,
                onload: () => logService.debug('蜂鸣声2音频加载完成'),
                onerror: (id, error) => logService.error('蜂鸣声2音频加载失败', { error: error.toString() })
            }),
            beep3: new Howl({
                src: ['/sounds/countdown_audio.mp3'],
                volume: 0.7,
                onload: () => logService.debug('蜂鸣声3音频加载完成'),
                onerror: (id, error) => logService.error('蜂鸣声3音频加载失败', { error: error.toString() })
            }),
            countdown: new Howl({
                src: ['/sounds/countdown_audio.mp3', '/sounds/countdown_audio.wav'],
                volume: 0.7,
                onload: () => logService.debug('countdown 音频加载完成'),
                onerror: (id, error) => logService.error('countdown 音频加载失败', { error: error.toString() })
            })
        }
    }
    return globalSounds
}

export function useBuzzer() {
    const isMuted = ref(false)
    const volume = ref(1.0)

    // ✅ 修复：获取全局单例音频对象而非创建新对象
    const sounds = getGlobalSounds()

    // 播放音频的通用方法
    function playSound(soundKey) {
        if (isMuted.value) {
            logService.debug('消音中，跳过音频播放', { sound: soundKey })
            return
        }

        try {
            const sound = sounds[soundKey]
            if (sound && typeof sound.play === 'function') {
                sound.volume(volume.value)
                sound.play()
                logService.event('SOUND_PLAYED', { sound: soundKey, volume: volume.value })
            } else {
                logService.warn('音频对象不存在或无法播放', { sound: soundKey })
            }
        } catch (error) {
            logService.error('播放音频失败', { sound: soundKey, error: error.message })
        }
    }

    // 1声（进入准备）
    function buzz1() {
        logService.event('BUZZER_1_BEEP', { reason: '进入准备阶段' })
        playSound('beep1')
    }

    // 2声（进入比赛）- 播放两次，间隔300ms
    function buzz2() {
        logService.event('BUZZER_2_BEEP', { reason: '进入比赛阶段' })
        playSound('beep2')
        setTimeout(() => {
            if (!isMuted.value) {
                playSound('beep2')
            }
        }, 300)
    }

    // 3声（进入黄灯）- 播放三次，间隔250ms
    function buzz3() {
        logService.event('BUZZER_3_BEEP', { reason: '进入黄灯/最后阶段' })
        playSound('beep3')
        setTimeout(() => {
            if (!isMuted.value) {
                playSound('beep3')
                setTimeout(() => {
                    if (!isMuted.value) {
                        playSound('beep3')
                    }
                }, 250)
            }
        }, 250)
    }

    // 倒计时声音
    function buzzCountdown() {
        logService.event('BUZZER_COUNTDOWN', { reason: '倒计时提醒' })
        playSound('countdown')
    }

    // 停止所有音频
    function stopAll() {
        Object.values(sounds).forEach(sound => {
            if (sound && typeof sound.stop === 'function') {
                sound.stop()
            }
        })
        logService.debug('停止所有音频播放')
    }

    // 设置音量
    function setVolume(newVolume) {
        volume.value = Math.max(0, Math.min(1, newVolume))
        Object.values(sounds).forEach(sound => {
            if (sound) {
                sound.volume(volume.value)
            }
        })
        logService.event('VOLUME_CHANGED', { volume: volume.value })
    }

    // 切换消音
    function toggleMute() {
        isMuted.value = !isMuted.value
        logService.event('MUTE_TOGGLED', { isMuted: isMuted.value })
    }

    // ✅ 修复：初始化音频上下文（用于某些浏览器的自动播放限制），返回初始化结果
    function initAudioContext() {
        try {
            // 检查Howler.js是否可用
            if (typeof Howl === 'undefined') {
                logService.error('Howler.js不可用，音频播放功能不可用')
                return false
            }

            // 尝试加载音频以初始化上下文
            let successCount = 0
            let totalCount = 0

            Object.entries(sounds).forEach(([key, sound]) => {
                totalCount++
                if (sound && typeof sound.load === 'function') {
                    try {
                        sound.load()
                        successCount++
                        logService.debug(`音频已加载: ${key}`)
                    } catch (loadError) {
                        logService.warn(`音频加载失败: ${key}`, { error: loadError.message })
                    }
                }
            })

            const allLoaded = successCount === totalCount
            if (allLoaded) {
                logService.info('✅ 音频上下文已初始化，所有音频已加载')
            } else {
                logService.warn(`⚠️ 音频部分加载失败 (成功: ${successCount}/${totalCount})`)
            }
            return allLoaded
        } catch (error) {
            logService.error('❌ 初始化音频上下文失败', { error: error.message })
            return false
        }
    }

    return {
        isMuted,
        volume,
        playSound,
        buzz1,
        buzz2,
        buzz3,
        buzzCountdown,
        stopAll,
        setVolume,
        toggleMute,
        initAudioContext
    }
}

export default useBuzzer
