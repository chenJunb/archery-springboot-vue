/**
 * 前端日志服务
 * 将所有操作记录到内存中
 */

class LogService {
    constructor() {
        this.logs = []
        this.maxLogs = 1000 // 内存中最多保留1000条日志
        this.sessionId = this.generateSessionId()
    }

    generateSessionId() {
        return new Date().toISOString().replace(/[:.]/g, '-').slice(0, -5)
    }

    log(level, message, data = null) {
        const timestamp = new Date().toLocaleTimeString('zh-CN', {
            hour: '2-digit',
            minute: '2-digit',
            second: '2-digit',
            hour12: false
        })

        const logEntry = {
            timestamp,
            level,
            message,
            data,
            sessionId: this.sessionId
        }

        this.logs.push(logEntry)

        // 只保留最近的日志
        if (this.logs.length > this.maxLogs) {
            this.logs.shift()
        }

        // 输出到控制台
        const emoji = {
            'INFO': 'ℹ️',
            'WARN': '⚠️',
            'ERROR': '❌',
            'EVENT': '📡',
            'DEBUG': '🐛'
        }[level] || '📝'

        console.log(`[${timestamp}] ${emoji} [${level}] ${message}`, data || '')
    }

    info(message, data) {
        this.log('INFO', message, data)
    }

    warn(message, data) {
        this.log('WARN', message, data)
    }

    error(message, data) {
        this.log('ERROR', message, data)
    }

    event(eventName, eventData) {
        this.log('EVENT', eventName, eventData)
    }

    debug(message, data) {
        // ✅ 强制在 console 中显示 debug 日志，便于排查
        console.log(`[DEBUG] ${message}`, data || '')
        this.log('DEBUG', message, data)
    }

    getLogs() {
        return this.logs
    }

    getLogsFiltered(level = null, limit = 100) {
        let filtered = this.logs
        if (level) {
            filtered = filtered.filter(log => log.level === level)
        }
        return filtered.slice(-limit)
    }

    exportLogs() {
        // 导出日志为JSON格式
        return JSON.stringify(this.logs, null, 2)
    }

    exportLogsAsCSV() {
        // 导出日志为CSV格式
        const headers = ['Timestamp', 'Level', 'Message', 'Data', 'SessionId']
        const rows = this.logs.map(log => [
            log.timestamp,
            log.level,
            log.message,
            typeof log.data === 'object' ? JSON.stringify(log.data) : log.data,
            log.sessionId
        ])

        let csv = headers.join(',') + '\n'
        rows.forEach(row => {
            csv += row.map(cell => `"${cell}"`).join(',') + '\n'
        })

        return csv
    }

    clear() {
        this.logs = []
    }

    getSessionInfo() {
        return {
            sessionId: this.sessionId,
            startTime: this.logs[0]?.timestamp || new Date().toLocaleTimeString('zh-CN'),
            totalLogs: this.logs.length,
            lastLogTime: this.logs[this.logs.length - 1]?.timestamp || 'N/A'
        }
    }
}

// 创建全局日志实例
export const logService = new LogService()

// 导出 LogService 类供其他模块使用
export default LogService
