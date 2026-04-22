/**
 * 射箭比赛计时系统 - 鸣笛/声音播放 composable
 */

import { ref } from 'vue'
import { Howl } from 'howler'
import { logService } from '@/services/logService'

export function useBuzzer() {
    const isMuted = ref(false)
    const volume = ref(1.0)

    // 预加载音频对象
    const sounds = {
        beep1: new Howl({
            src: ['/sounds/1-beep.mp3', '/sounds/1-beep.wav'],
            volume: 0.5,
            onload: () => logService.debug('1-beep 音频加载完成'),
            onerror: (id, error) => logService.error('1-beep 音频加载失败', { error: error.toString() })
        }),
        beep2: new Howl({
            src: ['/sounds/2-beep.mp3', '/sounds/2-beep.wav'],
            volume: 0.5,
            onload: () => logService.debug('2-beep 音频加载完成'),
            onerror: (id, error) => logService.error('2-beep 音频加载失败', { error: error.toString() })
        }),
        beep3: new Howl({
            src: ['/sounds/3-beep.mp3', '/sounds/3-beep.wav'],
            volume: 0.5,
            onload: () => logService.debug('3-beep 音频加载完成'),
            onerror: (id, error) => logService.error('3-beep 音频加载失败', { error: error.toString() })
        }),
        countdown: new Howl({
            src: ['/sounds/countdown_audio.mp3', '/sounds/countdown_audio.wav'],
            volume: 0.7,
            onload: () => logService.debug('countdown 音频加载完成'),
            onerror: (id, error) => logService.error('countdown 音频加载失败', { error: error.toString() })
        })
    }

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

    // 2声（进入比赛）
    function buzz2() {
        logService.event('BUZZER_2_BEEP', { reason: '进入比赛阶段' })
        playSound('beep2')
    }

    // 3声（进入黄灯）
    function buzz3() {
        logService.event('BUZZER_3_BEEP', { reason: '进入黄灯/最后阶段' })
        playSound('beep3')
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

    // 初始化音频上下文（用于某些浏览器的自动播放限制）
    function initAudioContext() {
        try {
            // 尝试加载音频以初始化上下文
            Object.values(sounds).forEach(sound => {
                if (sound && typeof sound.load === 'function') {
                    sound.load()
                }
            })
            logService.info('音频上下文已初始化')
        } catch (error) {
            logService.error('初始化音频上下文失败', { error: error.message })
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
