import { ref, computed } from 'vue'
import { fetchModelList } from '../api/request'

const MODEL_KEY = 'ai_agent_model'

/** Reactive shared state — 组件读这里，不直接读 localStorage */
export const modelList = ref([])
export const selectedModel = ref(localStorage.getItem(MODEL_KEY) || '')
/** 清单加载状态：用于区分「加载中」与「加载失败」 */
export const modelsLoading = ref(false)
export const modelsFailed = ref(false)

/**
 * 当前生效的模型：本地记录的模型下架或不可用时回退到默认模型
 */
export const currentModel = computed(() => {
  const list = modelList.value
  if (!list.length) return null
  const chosen = list.find((m) => m.key === selectedModel.value)
  if (chosen && chosen.available) return chosen
  return list.find((m) => m.defaultModel && m.available)
    || list.find((m) => m.available)
    || null
})

/**
 * 设置当前模型并持久化到本地（全局生效，所有会话共用）
 * 传空值时清除本地记录，回到默认模型
 */
export function setSelectedModel(key) {
  selectedModel.value = key || ''
  if (key) localStorage.setItem(MODEL_KEY, key)
  else localStorage.removeItem(MODEL_KEY)
}

// 清单请求缓存，一次页面加载内只请求一次；失败时清空以便重试
let loadPromise = null

/**
 * 拉取模型清单（带缓存）
 * 失败会抛出异常，由调用方决定提示方式；并发调用共享同一个请求
 */
export function fetchModels() {
  if (!loadPromise) {
    modelsLoading.value = true
    modelsFailed.value = false
    loadPromise = fetchModelList()
      .then((res) => {
        const list = Array.isArray(res.data) ? res.data : []
        modelList.value = list
        // 本地记录的模型已不在清单中或当前不可用：清除，由 currentModel 回退到默认模型
        const chosen = list.find((m) => m.key === selectedModel.value)
        if (selectedModel.value && (!chosen || !chosen.available)) {
          setSelectedModel('')
        }
        return list
      })
      .catch((err) => {
        loadPromise = null
        modelsFailed.value = true
        throw err
      })
      .finally(() => {
        modelsLoading.value = false
      })
  }
  return loadPromise
}
