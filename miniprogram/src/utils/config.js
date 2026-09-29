/**
 * 全局配置常量
 *
 * 服务器地址两层解析（见 request.js getBaseURL）：
 *   baseURL = storage(base_url) || DEFAULT_BASE_URL
 * 开发期 DEFAULT_BASE_URL 留空，靠登录页"服务器设置"弹窗配置；
 * 上线转正式服务器时：只需把 DEFAULT_BASE_URL 改为正式 HTTPS 域名，
 * 并在登录页去掉设置入口，地址解析机制无需任何改动。
 */
export const DEFAULT_BASE_URL = ''

export const STORAGE_BASE_URL = 'base_url'
export const STORAGE_TOKEN = 'ai_agent_token'
export const STORAGE_USERNAME = 'ai_agent_username'
/** 当前选择的大模型标识（与 Web 端同键名） */
export const STORAGE_MODEL = 'ai_agent_model'

/** 服务器设置弹窗里的示例引导 */
export const BASE_URL_EXAMPLE = 'http://192.168.1.100:8123/api'

/** 流式聊天最长等待（毫秒），超长任务由用户手动停止 */
export const STREAM_TIMEOUT = 300000
