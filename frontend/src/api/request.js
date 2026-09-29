import axios from 'axios'
import { getToken, setToken, removeToken } from '../utils/auth'

const BASE_URL = ''  // 同源相对路径，生产环境 /api 由 nginx 反代（内网穿透）
// const BASE_URL = import.meta.env.DEV ? '' : 'http://localhost:8123'
// const BASE_URL = import.meta.env.DEV ? '' : 'http://192.168.198.100' // 用于服务器/虚拟机

export const request = axios.create({
  baseURL: BASE_URL + '/api',
  timeout: 60000,
  headers: { 'Content-Type': 'application/json' },
})

// 请求头带上 token
request.interceptors.request.use((config) => {
  const token = getToken()
  if (token) config.headers.Authorization = 'Bearer ' + token
  return config
})

// 401 未登录/过期：清除 token 并跳转登录页，并带上 returnUrl
request.interceptors.response.use(
  (res) => res,
  (err) => {
    if (err.response?.status === 401) {
      removeToken()
      const returnUrl = encodeURIComponent(window.location.pathname + window.location.search || '/')
      window.location.href = '/login?returnUrl=' + returnUrl
    }
    return Promise.reject(err)
  }
)

/**
 * 读取非 2xx 响应的错误信息
 * 后端统一返回 {code, message}，优先取 message，取不到时回退到通用中文提示，
 * 避免把英文 statusText 直接展示给用户
 */
async function readErrorMessage(res) {
  try {
    const data = await res.clone().json()
    if (data && data.message) return data.message
  } catch {
    // 响应体不是 JSON，走通用提示
  }
  return `请求失败（${res.status}）`
}

/**
 * 纯文本流式聊天 - 知识助手
 * 使用 /chat/stream 端点，无 SSE 包装，兼容换行符
 * 支持 AbortController 取消
 *
 * @param model 模型标识，为空时由后端使用默认模型
 */
export function streamKnowledgeChat(message, chatId, { onChunk, onDone, onError }, signal, model) {
  const url = new URL(BASE_URL + '/api/ai/knowledge/chat/stream', window.location.origin)
  url.searchParams.set('message', message)
  url.searchParams.set('chatId', chatId)
  if (model) url.searchParams.set('model', model)

  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = 'Bearer ' + token

  fetch(url.toString(), { method: 'GET', headers, signal })
    .then((res) => {
      if (res.status === 401) {
        removeToken()
        const returnUrl = encodeURIComponent(window.location.pathname + window.location.search || '/')
        window.location.href = '/login?returnUrl=' + returnUrl
        return
      }
      if (!res.ok) return readErrorMessage(res).then((msg) => Promise.reject(new Error(msg)))
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      function read() {
        reader.read().then(({ done, value }) => {
          if (done) {
            onDone?.()
            return
          }
          const raw = decoder.decode(value, { stream: true })
          if (raw) onChunk?.(raw)
          read()
        }).catch((err) => {
          // AbortError 是主动取消，不触发 onError
          if (err.name === 'AbortError') return
          onError?.(err)
        })
      }
      read()
    })
    .catch((err) => {
      if (err.name === 'AbortError') return
      onError?.(err)
    })
}

/**
 * 带引用标注的 RAG 流式聊天 - 知识助手
 * 使用 /chat/rag/stream 端点
 * 流结束后会收到 <!--RAG_REFS--> 标记 + JSON 引用数据
 * onDone(refs) 回调会传入解析后的引用数组
 *
 * @param model 模型标识，为空时由后端使用默认模型
 */
export function streamKnowledgeChatRag(message, chatId, { onChunk, onDone, onError }, signal, model) {
  const url = new URL(BASE_URL + '/api/ai/knowledge/chat/rag/stream', window.location.origin)
  url.searchParams.set('message', message)
  url.searchParams.set('chatId', chatId)
  if (model) url.searchParams.set('model', model)

  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = 'Bearer ' + token

  let fullContent = ''

  fetch(url.toString(), { method: 'GET', headers, signal })
    .then((res) => {
      if (res.status === 401) {
        removeToken()
        const returnUrl = encodeURIComponent(window.location.pathname + window.location.search || '/')
        window.location.href = '/login?returnUrl=' + returnUrl
        return
      }
      if (!res.ok) return readErrorMessage(res).then((msg) => Promise.reject(new Error(msg)))
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      function read() {
        reader.read().then(({ done, value }) => {
          if (done) {
            // 流结束，解析引用数据
            const refs = parseRagReferences(fullContent)
            onDone?.(refs)
            return
          }
          const raw = decoder.decode(value, { stream: true })
          if (raw) {
            fullContent += raw
            onChunk?.(raw)
          }
          read()
        }).catch((err) => {
          if (err.name === 'AbortError') return
          onError?.(err)
        })
      }
      read()
    })
    .catch((err) => {
      if (err.name === 'AbortError') return
      onError?.(err)
    })
}

/**
 * 从完整内容中解析 RAG 引用数据
 * 格式：文本内容...<!--RAG_REFS-->[{...}, {...}]
 * 返回 { displayContent, references }
 */
function parseRagReferences(fullContent) {
  const marker = '<!--RAG_REFS-->'
  const idx = fullContent.indexOf(marker)
  if (idx === -1) {
    return { displayContent: fullContent, references: [] }
  }
  const displayContent = fullContent.substring(0, idx)
  const jsonStr = fullContent.substring(idx + marker.length)
  try {
    const references = JSON.parse(jsonStr)
    return { displayContent, references }
  } catch (e) {
    console.warn('解析 RAG 引用数据失败:', e)
    return { displayContent: fullContent, references: [] }
  }
}

/**
 * SSE 流式请求 - 超级智能体
 * 支持 AbortController 取消
 */
export function streamManusChat(message, { onChunk, onDone, onError }, signal) {
  const url = new URL(BASE_URL + '/api/ai/manus/chat', window.location.origin)
  url.searchParams.set('message', message)

  const headers = {}
  const token = getToken()
  if (token) headers.Authorization = 'Bearer ' + token

  fetch(url.toString(), { method: 'GET', headers, signal })
    .then((res) => {
      if (res.status === 401) {
        removeToken()
        const returnUrl = encodeURIComponent(window.location.pathname + window.location.search || '/')
        window.location.href = '/login?returnUrl=' + returnUrl
        return
      }
      if (!res.ok) throw new Error(res.statusText)
      const reader = res.body.getReader()
      const decoder = new TextDecoder()
      function read() {
        reader.read().then(({ done, value }) => {
          if (done) {
            onDone?.()
            return
          }
          const raw = decoder.decode(value, { stream: true })
          if (raw) onChunk?.(raw)
          read()
        }).catch((err) => {
          if (err.name === 'AbortError') return
          onError?.(err)
        })
      }
      read()
    })
    .catch((err) => {
      if (err.name === 'AbortError') return
      onError?.(err)
    })
}

/**
 * 更新会话标题
 */
export function updateChatTitle(chatId, title) {
  return request.put(`/ai/knowledge/chat/history/${chatId}/title`, { title })
}

/**
 * 删除会话
 */
export function deleteChat(chatId) {
  return request.delete(`/ai/knowledge/chat/history/${chatId}`)
}

/**
 * 批量删除会话
 */
export function batchDeleteChats(chatIds) {
  return request.post('/ai/knowledge/chat/history/batch-delete', { chatIds })
}

/**
 * 拉取当前登录用户信息（用户名 + 管理员标识），用于刷新页面后恢复登录态
 */
export async function fetchCurrentUser() {
  const res = await request.get('/auth/me')
  const data = res.data || {}
  setToken(getToken(), data.username, data.isAdmin)
  return data
}

/**
 * 大模型清单（含参数、价格、能力与可用性标记）
 */
export function fetchModelList() {
  return request.get('/ai/model/list')
}

/**
 * 知识库文档列表（管理员）
 */
export function fetchKnowledgeDocuments({ keyword = '', page = 1, size = 10, sort = 'time_desc' } = {}) {
  const query = 'keyword=' + encodeURIComponent(keyword)
    + '&page=' + page + '&size=' + size
    + '&sort=' + encodeURIComponent(sort)
  return request.get('/ai/knowledge/document/list?' + query)
}

/**
 * 知识库文档详情（磁盘当前内容 + 分块结果）
 */
export function fetchKnowledgeDocumentContent(filename) {
  return request.get(`/ai/knowledge/document/${encodeURIComponent(filename)}/content`)
}

/**
 * 上传知识库文档（仅 .md，后台异步预处理与向量化）
 */
export function uploadKnowledgeDocument(file) {
  const formData = new FormData()
  formData.append('file', file, file.name)
  // 覆盖实例默认的 application/json，置空后浏览器自动生成 multipart/form-data 及 boundary
  return request.post('/ai/knowledge/document/upload', formData, {
    timeout: 60000,
    headers: { 'Content-Type': undefined },
  })
}

/**
 * 删除知识库文档（同时清理向量切片）
 */
export function deleteKnowledgeDocument(filename) {
  return request.delete(`/ai/knowledge/document/${encodeURIComponent(filename)}`)
}

/**
 * 批量删除知识库文档
 */
export function batchDeleteKnowledgeDocuments(filenames) {
  return request.post('/ai/knowledge/document/batch-delete', { filenames })
}

/**
 * 重新入库（清理该文档已入库切片后重跑预处理与向量化）
 */
export function reindexKnowledgeDocument(filename) {
  return request.post(`/ai/knowledge/document/${encodeURIComponent(filename)}/reindex`)
}

/**
 * 用户列表（管理员）：分页 + 用户名搜索 + 排序
 */
export function fetchUsers({ keyword = '', page = 1, size = 10, sort = 'time_desc' } = {}) {
  const query = 'keyword=' + encodeURIComponent(keyword)
    + '&page=' + page + '&size=' + size
    + '&sort=' + encodeURIComponent(sort)
  return request.get('/ai/user/list?' + query)
}

/**
 * 批量修改用户角色（管理员）：role 取值 0 普通用户 / 1 管理员
 */
export function batchUpdateUserRole(userIds, role) {
  return request.post('/ai/user/batch-role', { userIds, role })
}
