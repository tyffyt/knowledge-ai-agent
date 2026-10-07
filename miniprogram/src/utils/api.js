import { request, streamRequest, getBaseURL } from './request'

/**
 * 后端接口集中定义，页面层只调这里，不直接拼 URL
 */

// ---- 认证（AuthController，无需 token）----
export const login = (username, password) =>
	request({ url: '/auth/login', method: 'POST', data: { username, password }, noAuth: true })

export const getCaptcha = () =>
	request({ url: '/auth/captcha', noAuth: true })

export const register = (data) =>
	request({ url: '/auth/register', method: 'POST', data, noAuth: true })

export const logout = () =>
	request({ url: '/auth/logout', method: 'POST', noAuth: true })

export const fetchMe = () =>
	request({ url: '/auth/me' })

export const changePassword = (oldPassword, newPassword) =>
	request({ url: '/auth/change-password', method: 'POST', data: { oldPassword, newPassword } })

// ---- 知识助手历史会话（AiController，需 token）----
export const fetchHistory = () =>
	request({ url: '/ai/knowledge/chat/history' })

export const fetchChatDetail = (chatId) =>
	request({ url: '/ai/knowledge/chat/history/' + encodeURIComponent(chatId) })

export const updateChatTitle = (chatId, title) =>
	request({ url: '/ai/knowledge/chat/history/' + encodeURIComponent(chatId) + '/title', method: 'PUT', data: { title } })

export const deleteChat = (chatId) =>
	request({ url: '/ai/knowledge/chat/history/' + encodeURIComponent(chatId), method: 'DELETE' })

export const batchDeleteChats = (chatIds) =>
	request({ url: '/ai/knowledge/chat/history/batch-delete', method: 'POST', data: { chatIds } })

// ---- 大模型清单（ChatModelController，需 token）----
export const fetchModelList = () =>
	request({ url: '/ai/model/list' })

// ---- 流式聊天（knowledge 为纯文本 chunk 流，manus 为 SSE 帧流，尾部可能带 <!--RAG_REFS-->JSON）----
export const streamKnowledgeChat = (message, chatId, handlers, model) =>
	streamRequest('/ai/knowledge/chat/stream', withModel({ message, chatId }, model), handlers)

export const streamKnowledgeChatRag = (message, chatId, handlers, model) =>
	streamRequest('/ai/knowledge/chat/rag/stream', withModel({ message, chatId }, model), handlers)

/** 模型标识为空时不下发该参数，由后端使用默认模型 */
function withModel(query, model) {
	return model ? { ...query, model } : query
}

export const streamManusChat = (message, handlers) =>
	streamRequest('/ai/manus/chat', { message }, handlers, { sse: true })

// ---- Manus 任务制（ManusController，需 token；旧会话式接口保留但页面已切换到任务制）----
export const createManusTask = (task) =>
	request({ url: '/ai/manus/task', method: 'POST', data: { task }, timeout: 120000 })

export const fetchManusTask = (taskId) =>
	request({ url: '/ai/manus/task/' + encodeURIComponent(taskId) })

export const fetchManusTaskList = () =>
	request({ url: '/ai/manus/task/list' })

export const stopManusTask = (taskId) =>
	request({ url: '/ai/manus/task/' + encodeURIComponent(taskId) + '/stop', method: 'POST' })

export const sendManusFollowUp = (taskId, message) =>
	request({ url: '/ai/manus/task/' + encodeURIComponent(taskId) + '/message', method: 'POST', data: { message }, timeout: 120000 })

export const renameManusTask = (taskId, title) =>
	request({ url: '/ai/manus/task/' + encodeURIComponent(taskId) + '/title', method: 'PUT', data: { title } })

export const deleteManusTask = (taskId) =>
	request({ url: '/ai/manus/task/' + encodeURIComponent(taskId), method: 'DELETE' })

/**
 * 任务事件流（GET SSE，每帧 data 为一个事件 JSON）
 * onEvent 收到解析后的事件对象；复用通用流式层的分帧与 UTF-8 跨块解码
 */
export const streamManusTaskEvents = (taskId, after, handlers) =>
	streamRequest('/ai/manus/task/' + encodeURIComponent(taskId) + '/stream', { after: String(after || 0) }, {
		onChunk: (text) => {
			if (!handlers.onEvent || !text) return
			try {
				handlers.onEvent(JSON.parse(text))
			} catch (e) {
				// 单帧解析失败丢弃该帧（与 Web 侧逐帧解析策略一致）
			}
		},
		onDone: handlers.onDone,
		onError: handlers.onError
	}, { sse: true })

/** 交付物下载地址（需带 token 的 downloadFile 请求） */
export const buildDeliverableDownloadUrl = (taskId, index) =>
	'/ai/manus/task/' + encodeURIComponent(taskId) + '/deliverable/' + index + '/download'

// ---- 语音（SpeechController，无需 token）----
export const fetchTts = (text) =>
	request({ url: '/speech/tts', method: 'POST', data: { text }, noAuth: true, responseType: 'arraybuffer', timeout: 120000 })

export const uploadStt = (wavPath) =>
	uploadFile('/speech/stt', wavPath)

/** multipart 文件上传（uni.uploadFile 封装） */
function uploadFile(url, filePath) {
	return new Promise((resolve, reject) => {
		uni.uploadFile({
			url: getBaseURL() + url,
			filePath,
			name: 'file',
			timeout: 90000,
			success: (res) => {
				if (res.statusCode >= 200 && res.statusCode < 300) {
					try {
						resolve(JSON.parse(res.data))
					} catch (e) {
						resolve({ text: '' })
					}
					return
				}
				reject({ code: res.statusCode, message: '上传失败(' + res.statusCode + ')' })
			},
			fail: (err) => reject({ code: -1, message: err.errMsg || '上传失败' })
		})
	})
}
