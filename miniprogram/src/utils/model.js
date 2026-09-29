import { ref, computed } from 'vue'
import { STORAGE_MODEL } from './config'
import { fetchModelList } from './api'

/**
 * 大模型清单与当前选择：模块级响应式状态，页面/组件共享（逻辑与 Web 端 utils/model.js 一致）
 */
export const modelList = ref([])
export const selectedModel = ref(uni.getStorageSync(STORAGE_MODEL) || '')
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
	if (key) uni.setStorageSync(STORAGE_MODEL, key)
	else uni.removeStorageSync(STORAGE_MODEL)
}

// 清单请求缓存，一次小程序运行内只请求一次；失败时清空以便重试
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
			.then((list) => {
				const items = Array.isArray(list) ? list : []
				modelList.value = items
				// 本地记录的模型已不在清单中或当前不可用：清除，由 currentModel 回退到默认模型
				const chosen = items.find((m) => m.key === selectedModel.value)
				if (selectedModel.value && (!chosen || !chosen.available)) {
					setSelectedModel('')
				}
				return items
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
