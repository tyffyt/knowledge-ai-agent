<template>
	<view class="page">
		<!-- 顶部工具栏：返回 / 历史 / 新对话 / 标题 -->
		<view class="toolbar">
			<view class="tool-btn icon-btn" @tap="goHome">
				<image class="tool-icon" src="/static/icons/back.svg" mode="aspectFit" />
			</view>
			<view class="tool-btn icon-btn" @tap="openHistory">
				<image class="tool-icon" src="/static/icons/history.svg" mode="aspectFit" />
			</view>
			<view class="tool-btn icon-btn" @tap="newChat">
				<image class="tool-icon" src="/static/icons/plus.svg" mode="aspectFit" />
			</view>
			<view class="tool-title">{{ currentTitle }}</view>
		</view>

		<!-- 消息列表 -->
		<scroll-view v-if="messages.length" class="msg-list" scroll-y :scroll-into-view="scrollInto" scroll-with-animation>
			<view v-for="msg in messages" :key="msg.id">
				<chat-bubble :msg="msg" />
			</view>
			<!-- 底部锚点：始终置于列表末尾，scroll-into-view 滚到锚点即滚到最底（长消息也不漏） -->
			<view id="bottom-anchor" class="bottom-anchor"></view>
		</scroll-view>
		<!-- 空状态：垂直居中 -->
		<view v-else class="empty-area">
			<view class="empty-tip">
				<view class="empty-icon-wrap">
					<image class="empty-icon" src="/static/icons/book.svg" mode="aspectFit" />
				</view>
				<text class="empty-title">向你的个人知识库提问</text>
				<text class="empty-sub">支持 RAG 检索引用 · 语音输入 · PDF 下载</text>
			</view>
		</view>

		<!-- 输入区：四个控件都在输入框内——左下角 RAG 与模型切换并列，右下角语音/发送 -->
		<view class="input-area">
			<view class="chat-input" :class="{ focusing: inputFocused }">
				<textarea
					class="chat-textarea"
					v-model="input"
					placeholder="输入问题..."
					placeholder-style="color:#94A3B8"
					:disabled="sending"
					auto-height
					confirm-type="send"
					@focus="inputFocused = true"
					@blur="inputFocused = false"
					@confirm="send"
				/>
				<view class="input-bottom">
					<view class="input-tools">
						<view class="rag-toggle" :class="{ on: ragEnabled }" @tap="toggleRag">
							<image class="pill-icon" src="/static/icons/search.svg" mode="aspectFit" />
							<text class="rag-text">RAG</text>
							<view class="rag-switch">
								<view class="rag-knob"></view>
							</view>
						</view>
						<view class="model-pill" @tap="openModelPicker">
							<image class="pill-icon" src="/static/icons/cpu.svg" mode="aspectFit" />
							<!-- 用 view 而非 text：小程序里 text 上的 text-overflow 省略号不生效，名字太长会把整行撑宽 -->
							<view class="model-name">{{ currentModelLabel }}</view>
							<image class="model-caret" src="/static/icons/chevron-down.svg" mode="aspectFit" />
						</view>
					</view>
					<view class="mic-btn" :class="{ recording: recording }" @tap="toggleRecord">
						<image class="btn-icon" :src="recording ? '/static/icons/mic-white.svg' : '/static/icons/mic.svg'" mode="aspectFit" />
					</view>
					<view class="send-btn" :class="{ sending: sending }" @tap="sending ? stop() : send()">
						<image class="btn-icon" :src="sending ? '/static/icons/stop-white.svg' : '/static/icons/send-green.svg'" mode="aspectFit" />
					</view>
				</view>
			</view>
		</view>

		<!-- 大模型选择弹层 -->
		<model-picker :visible="modelPickerVisible" @close="modelPickerVisible = false" />

		<!-- 历史会话弹层 -->
		<history-panel
			:visible="historyVisible"
			:current-chat-id="chatId"
			@close="historyVisible = false"
			@select="onSelectChat"
			@deleted-current="onDeletedCurrent"
		/>
	</view>
</template>

<script setup>
/**
 * 知识聊天页：流式聊天（RAG 开关）、语音输入、历史会话管理、新建对话
 */
import { ref, computed, nextTick } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { isLoggedIn } from '../../utils/auth'
import { generateChatId, generateMsgId, parseRagReferences, mapHistoryMessages } from '../../utils/chat'
import { streamKnowledgeChat, streamKnowledgeChatRag, fetchChatDetail, fetchHistory } from '../../utils/api'
import { currentModel, fetchModels } from '../../utils/model'
import { startRecording, stopRecording, isRecording } from '../../utils/stt'
import { stopSpeech } from '../../utils/tts'
import chatBubble from '../../components/chat-bubble.vue'
import historyPanel from '../../components/history-panel.vue'
import modelPicker from '../../components/model-picker.vue'

const RAG_REFS_MARK = '<!--RAG_REFS-->'

const messages = ref([])
const input = ref('')
const chatId = ref('')
const ragEnabled = ref(true)
const sending = ref(false)
const historyVisible = ref(false)
const recording = ref(false)
const currentTitle = ref('新对话')
const scrollInto = ref('')
const historyList = ref([])
/** 模型选择弹层与输入框聚焦态 */
const modelPickerVisible = ref(false)
const inputFocused = ref(false)
/** 当前生效模型名：清单未加载完成时显示占位文案 */
const currentModelLabel = computed(() => (currentModel.value ? currentModel.value.displayName : '大模型'))

let streamTask = null
let sentChatId = ''
let scrollTimer = null

onLoad(() => {
	if (!isLoggedIn()) {
		uni.reLaunch({ url: '/pages/login/login' })
		return
	}
	chatId.value = generateChatId()
	// 预取模型清单：输入区显示当前模型名；失败不阻塞聊天
	fetchModels().catch(() => {})
})

onShow(() => {
	if (!isLoggedIn()) {
		uni.reLaunch({ url: '/pages/login/login' })
		return
	}
})

onUnload(() => {
	stopStream()
	if (isRecording()) {
		stopRecording()
	}
	stopSpeech()
})

/** 返回应用中心首页 */
function goHome() {
	stopStream()
	stopSpeech()
	uni.reLaunch({ url: '/pages/index/index' })
}

/** 新建对话：停流 + 新会话 ID + 清空消息 */
function newChat() {
	stopStream()
	if (isRecording()) {
		stopRecording()
	}
	stopSpeech()
	chatId.value = generateChatId()
	messages.value = []
	input.value = ''
	currentTitle.value = '新对话'
	scrollInto.value = ''
	historyVisible.value = false
	uni.showToast({ title: '已开启新对话', icon: 'none' })
}

function toggleRag() {
	ragEnabled.value = !ragEnabled.value
}

/** 打开模型选择弹层；清单未加载或上次失败时重新拉取（成功后有缓存，不会重复请求） */
function openModelPicker() {
	modelPickerVisible.value = true
	fetchModels().catch(() => {})
}

function openHistory() {
	historyVisible.value = true
}

/**
 * 滚动到底部：用固定的底部锚点（bottom-anchor），scroll-into-view 始终滚到锚点，
 * 避免目标为长消息时只滚到消息顶部（最新内容在屏幕下方）。
 * 锚点 id 恒定不触发滚动 → 先清空再设置；流式高频调用节流 150ms
 */
function scrollToBottom() {
	if (!messages.value.length) return
	if (scrollTimer) return
	scrollTimer = setTimeout(() => {
		scrollTimer = null
		nextTick(() => {
			scrollInto.value = ''
			setTimeout(() => {
				scrollInto.value = 'bottom-anchor'
			}, 80)
		})
	}, 150)
}

async function send() {
	const text = input.value.trim()
	if (!text || sending.value) return
	// 后端流式接口是 GET，超长文本会命中 Tomcat 8KB 请求头上限
	if (text.length > 4000) {
		uni.showToast({ title: '问题过长，请分段提问（≤4000字）', icon: 'none' })
		return
	}
	input.value = ''
	messages.value.push({ id: generateMsgId(), role: 'user', content: text, references: [], loading: false })
	messages.value.push({ id: generateMsgId(), role: 'assistant', content: '', references: [], loading: true })
	sending.value = true
	sentChatId = chatId.value
	scrollToBottom()

	const handlers = {
		onChunk: (chunk) => {
			if (chatId.value !== sentChatId) return
			const last = messages.value[messages.value.length - 1]
			if (!last) return
			last.content = appendChunk(last.content, chunk)
			scrollToBottom()
		},
		onDone: (fullText) => {
			finishSend()
			// 流归属校验：期间切换了会话则丢弃旧流结果
			if (chatId.value !== sentChatId) return
			const last = messages.value[messages.value.length - 1]
			if (last) {
				const parsed = parseRagReferences(fullText || last.content)
				last.content = parsed.displayContent
				last.references = parsed.references
				last.loading = false
				// 发送后立即停止产生的空气泡：无内容则移除
				if (!last.content) {
					messages.value.pop()
				}
			}
		},
		onError: (err) => {
			finishSend()
			if (chatId.value !== sentChatId) return
			const last = messages.value[messages.value.length - 1]
			if (last) {
				last.content = '回复失败：' + ((err && err.message) || '网络错误')
				last.loading = false
			}
		}
	}

	try {
		// 当前生效模型：清单未加载完成为空，由后端使用默认模型
		const model = currentModel.value ? currentModel.value.key : ''
		streamTask = ragEnabled.value
			? streamKnowledgeChatRag(text, chatId.value, handlers, model)
			: streamKnowledgeChat(text, chatId.value, handlers, model)
	} catch (e) {
		handlers.onError(e)
	}
}

/** 流式 chunk 追加：出现 RAG 引用标记后不再追加（标记后是引用 JSON） */
function appendChunk(content, chunk) {
	const merged = content + chunk
	const idx = merged.indexOf(RAG_REFS_MARK)
	return idx >= 0 ? merged.slice(0, idx) : merged
}

function finishSend() {
	sending.value = false
	streamTask = null
	refreshHistoryTitle()
}

function stop() {
	stopStream()
}

function stopStream() {
	if (streamTask) {
		try {
			streamTask.abort()
		} catch (e) {
			// 已结束则忽略
		}
		streamTask = null
	}
}

/** 流式结束后刷新历史标题 */
async function refreshHistoryTitle() {
	try {
		const list = await fetchHistory()
		historyList.value = list
		const mine = list.find((c) => c.chatId === chatId.value)
		if (mine && mine.title) {
			currentTitle.value = mine.title
		}
	} catch (e) {
		// 标题刷新失败不阻塞
	}
}

/** 切换历史会话：先中止进行中的流式，避免旧流写入新会话 */
async function onSelectChat(targetId) {
	historyVisible.value = false
	stopStream()
	if (targetId === chatId.value && messages.value.length) return
	try {
		const detail = await fetchChatDetail(targetId)
		messages.value = mapHistoryMessages(detail)
		chatId.value = targetId
		currentTitle.value = '加载中...'
		refreshHistoryTitle()
		scrollToBottom()
	} catch (e) {
		uni.showToast({ title: (e && e.message) || '加载会话失败', icon: 'none' })
	}
}

/** 当前会话被删除：新建会话 */
function onDeletedCurrent() {
	chatId.value = generateChatId()
	messages.value = []
	currentTitle.value = '新对话'
	scrollInto.value = ''
}

/** 语音输入：点击切换录音/停止，识别结果回填输入框 */
function toggleRecord() {
	if (recording.value) {
		stopRecording()
		// 识别结果回调里置 false
		return
	}
	startRecording(
		() => {
			recording.value = true
		},
		(text) => {
			recording.value = false
			if (text) {
				input.value = (input.value ? input.value + ' ' : '') + text
			} else {
				uni.showToast({ title: '未识别到内容', icon: 'none' })
			}
		},
		(errMsg) => {
			recording.value = false
			uni.showToast({ title: errMsg || '语音识别失败', icon: 'none' })
		}
	)
}
</script>

<style scoped>
.page {
	height: 100vh;
	height: 100dvh; /* 真机浏览器动态视口，避免底部被系统条遮挡 */
	overflow: hidden;
	display: flex;
	flex-direction: column;
	background: #ECFDF5;
}

.toolbar {
	display: flex;
	align-items: center;
	padding: 16rpx 20rpx;
	background: rgba(255, 255, 255, 0.85);
	border-bottom: 1rpx solid rgba(16, 185, 129, 0.15);
}

.tool-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	height: 88rpx;
	min-width: 88rpx;
	background: rgba(255, 255, 255, 0.75);
	border: 1rpx solid rgba(16, 185, 129, 0.3);
	border-radius: 24rpx;
	margin-right: 16rpx;
	flex-shrink: 0;
}

.tool-icon {
	width: 40rpx;
	height: 40rpx;
}

.tool-title {
	flex: 1;
	min-width: 0;
	text-align: center;
	font-size: 28rpx;
	font-weight: 600;
	color: #064E3B;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
	padding: 0 12rpx;
}

.msg-list {
	flex: 1;
	min-height: 0;
	padding: 16rpx 0 8rpx;
	box-sizing: border-box;
}

/* 滚动到底部的锚点占位 */
.bottom-anchor {
	height: 16rpx;
}

/* 空状态：垂直水平居中 */
.empty-area {
	flex: 1;
	min-height: 0;
	display: flex;
	align-items: center;
	justify-content: center;
}

.empty-tip {
	display: flex;
	flex-direction: column;
	align-items: center;
	color: #94A3B8;
}

.empty-icon-wrap {
	width: 128rpx;
	height: 128rpx;
	border-radius: 50%;
	background: rgba(16, 185, 129, 0.08);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-bottom: 32rpx;
}

.empty-icon {
	width: 64rpx;
	height: 64rpx;
	opacity: 0.6;
}

.empty-title {
	font-size: 30rpx;
	color: #94A3B8;
}

.empty-sub {
	font-size: 24rpx;
	margin-top: 16rpx;
	color: #CBD5E1;
}

.input-area {
	padding: 16rpx 20rpx calc(20rpx + env(safe-area-inset-bottom));
	background: rgba(255, 255, 255, 0.9);
	border-top: 1rpx solid rgba(16, 185, 129, 0.15);
}

/* 输入框容器：输入框在上、底部一排控件在下，聚焦时整框高亮 */
.chat-input {
	display: flex;
	flex-direction: column;
	border-radius: 28rpx;
	border: 1rpx solid #E2E8F0;
	background: #F8FAFC;
	transition: border-color 0.2s ease;
}

.chat-input.focusing {
	border-color: #34D399;
}

.chat-textarea {
	width: 100%;
	min-height: 44rpx;
	padding: 24rpx 24rpx 8rpx;
	box-sizing: border-box;
	font-size: 30rpx;
	line-height: 44rpx;
	max-height: 200rpx;
}

/* 底部一排：左下角 RAG 与模型切换并列，右下角语音/发送 */
.input-bottom {
	display: flex;
	align-items: center;
	padding: 0 16rpx 16rpx;
}

.input-tools {
	display: flex;
	align-items: center;
	/* 允许收缩，配合模型名的省略号，避免整行超出把语音/发送挤出输入框 */
	flex: 0 1 auto;
	min-width: 0;
	margin-right: auto;
}

.rag-toggle,
.model-pill {
	display: flex;
	align-items: center;
	min-height: 72rpx;
	padding: 0 16rpx;
	border-radius: 16rpx;
	background: #F8FAFC;
}

.rag-toggle {
	flex-shrink: 0;
	margin-right: 8rpx;
}

.model-pill {
	/* 空间不足时收缩并省略模型名 */
	flex: 0 1 auto;
	min-width: 0;
	max-width: 100%;
}

.pill-icon {
	width: 28rpx;
	height: 28rpx;
	flex-shrink: 0;
}

.rag-text {
	font-size: 26rpx;
	color: #065F46;
	margin-left: 8rpx;
}

.rag-switch {
	width: 64rpx;
	height: 36rpx;
	border-radius: 999rpx;
	background: #CBD5E1;
	margin-left: 12rpx;
	position: relative;
	flex-shrink: 0;
	transition: background 0.25s ease;
}

.rag-toggle.on .rag-switch {
	background: #10B981;
}

.rag-knob {
	width: 28rpx;
	height: 28rpx;
	border-radius: 50%;
	background: #FFFFFF;
	position: absolute;
	top: 4rpx;
	left: 4rpx;
	transition: transform 0.25s ease;
	box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.15);
}

.rag-toggle.on .rag-knob {
	transform: translateX(28rpx);
}

.model-name {
	font-size: 26rpx;
	color: #065F46;
	margin-left: 8rpx;
	min-width: 0;
	max-width: 300rpx;
	overflow: hidden;
	text-overflow: ellipsis;
	white-space: nowrap;
}

.model-caret {
	width: 24rpx;
	height: 24rpx;
	flex-shrink: 0;
	margin-left: 4rpx;
	opacity: 0.7;
}

/* 语音/发送：去边框、与输入框同底色（录音/终止时用实色底 + 白图标便于分辨） */
.mic-btn {
	width: 72rpx;
	height: 72rpx;
	border-radius: 16rpx;
	background: #F8FAFC;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-left: 12rpx;
	flex-shrink: 0;
	transition: all 0.2s ease;
}

.mic-btn.recording {
	background: #EF4444;
	animation: pulse 1s infinite;
}

@keyframes pulse {
	0%, 100% {
		box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.4);
	}
	50% {
		box-shadow: 0 0 0 16rpx rgba(239, 68, 68, 0);
	}
}

.send-btn {
	width: 72rpx;
	height: 72rpx;
	border-radius: 16rpx;
	background: #F8FAFC;
	display: flex;
	align-items: center;
	justify-content: center;
	margin-left: 12rpx;
	flex-shrink: 0;
	transition: all 0.2s ease;
}

.send-btn.sending {
	background: #EF4444;
}

.btn-icon {
	width: 44rpx;
	height: 44rpx;
}
</style>
