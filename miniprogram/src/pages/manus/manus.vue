<template>
	<view class="page">
		<view class="toolbar">
			<view class="tool-btn icon-btn" @tap="goHome">
				<image class="tool-icon" src="/static/icons/back.svg" mode="aspectFit" />
			</view>
			<view class="tool-title">AI 超级智能体</view>
			<view class="tool-btn icon-btn" @tap="openHistory">
				<image class="tool-icon" src="/static/icons/history.svg" mode="aspectFit" />
			</view>
		</view>

		<!-- 任务视图：轮次对话流（用户问题 → 执行过程 → 任务报告 → 交付物） -->
		<scroll-view v-if="active" class="task-scroll" scroll-y :scroll-into-view="scrollInto" scroll-with-animation>
			<view class="task-view">
				<view v-for="(round, ri) in rounds" :key="ri" class="round">
					<!-- 用户问题 -->
					<view class="question-row">
						<view class="question-bubble">{{ round.question }}</view>
					</view>

					<!-- 执行过程折叠行（执行中常驻） -->
					<view
						v-if="round.items.length || (round.plan && round.plan.length) || isRoundRunning(ri)"
						class="process-toggle"
						@tap="toggleRound(ri)"
					>
						<image class="process-icon" :class="{ spinning: isRoundRunning(ri) }" src="/static/icons/list-checks.svg" mode="aspectFit" />
						<text class="process-title">{{ roundTitle(ri) }}</text>
						<view v-if="roundThinking(ri)" class="thinking-dots">
							<view class="dot"></view>
							<view class="dot"></view>
							<view class="dot"></view>
						</view>
						<image class="chevron" :class="{ expanded: !isRoundCollapsed(ri) }" src="/static/icons/chevron-down.svg" mode="aspectFit" />
					</view>
					<view v-if="!isRoundCollapsed(ri)" class="process-detail">
						<!-- 执行计划 -->
						<view v-if="round.plan && round.plan.length" class="plan-panel">
							<view class="panel-head">
								<image class="panel-icon" src="/static/icons/list-checks.svg" mode="aspectFit" />
								<text class="panel-title">执行计划</text>
							</view>
							<view v-for="step in round.plan" :key="step.index" class="plan-step">
								<view class="step-icon" :class="step.status">
									<image v-if="step.status === 'done'" class="step-icon-img" src="/static/icons/check.svg" mode="aspectFit" />
									<view v-else-if="step.status === 'in_progress'" class="step-spinner"></view>
									<view v-else class="step-circle"></view>
								</view>
								<text class="step-index">{{ step.index }}.</text>
								<view class="step-body">
									<text class="step-content" :class="{ dim: step.status === 'skipped' }">{{ step.content }}</text>
									<text v-if="step.note" class="step-note">{{ step.note }}</text>
								</view>
							</view>
						</view>
						<!-- 事件列表（工具调用与结果合并一行，点按展开详情） -->
						<view v-for="(item, i) in round.items" :key="i" class="event-card" :class="{ thinking: item.kind === 'single' && item.ev.type === 'think' }">
							<view class="event-head" @tap="onEventHeadTap(item, ri, i)">
								<view class="event-dot" :class="item.kind === 'tool' ? (item.result ? 'dot-done' : 'dot-running') : 'dot-think'"></view>
								<view class="event-main">
									<view class="event-title-row">
										<text class="event-title">{{ item.kind === 'tool' ? eventTitle(item.call) : eventTitle(item.ev) }}</text>
										<image
											v-if="item.kind === 'tool' && item.result"
											class="event-chev" :class="{ expanded: expandedSet.has(ri + '-' + i) }"
											src="/static/icons/chevron-down.svg" mode="aspectFit"
										/>
									</view>
									<text v-if="item.kind === 'tool' && item.result" class="event-brief">{{ briefOf(item.result) }}</text>
									<text v-else-if="item.kind === 'single' && (item.ev.type === 'think' || item.ev.type === 'tool_result') && !expandedSet.has(ri + '-' + i)" class="event-brief">{{ briefOf(item.ev) }}</text>
								</view>
							</view>
							<view v-if="isEventExpanded(item, ri, i)" class="event-detail">
								<template v-if="item.kind === 'tool'">
									<view v-if="item.call.toolArgs" class="detail-block">{{ formatArgs(item.call.toolArgs) }}</view>
									<view v-if="item.result && item.result.toolResult" class="detail-block">{{ item.result.toolResult }}</view>
								</template>
								<view v-else-if="item.ev.toolResult || item.ev.content" class="detail-block">{{ item.ev.toolResult || item.ev.content }}</view>
							</view>
						</view>
					</view>

					<!-- 任务报告（复用消息气泡：marked+mp-html 渲染、图片代理、TTS 播报） -->
					<chat-bubble
					v-if="round.report"
					:key="'report-' + ri + '-' + round.report.length"
					:msg="reportBubble(round)"
					:hide-avatar="true"
					:compact="true"
				/>

					<!-- 交付物行（下载后打开；图片可预览） -->
					<view v-for="d in round.deliverables" :key="d.index" class="deliverable" @tap="openDeliverable(d)">
						<view class="deliverable-info">
							<text class="deliverable-name">{{ d.name }}</text>
							<text class="deliverable-type">{{ typeLabel(d.type) }}</text>
						</view>
						<view class="deliverable-btn" @tap.stop="openDeliverable(d)">
							<text class="deliverable-btn-text">{{ d.type === 'image' ? '查看' : '打开' }}</text>
						</view>
					</view>
				</view>
				<view id="bottom-anchor" class="bottom-anchor"></view>
			</view>
		</scroll-view>
		<!-- 空状态 -->
		<view v-else class="empty-area">
			<view class="empty-tip">
				<view class="empty-icon-wrap">
					<image class="empty-icon" src="/static/icons/robot.svg" mode="aspectFit" />
				</view>
				<text class="empty-title">AI 超级智能体</text>
				<text class="empty-sub">描述任务，自动规划步骤并逐步执行（搜索、文件、PDF 等）</text>
			</view>
		</view>

		<view class="input-area">
			<view class="input-row">
				<view class="input-wrap chat-input">
					<textarea
						class="chat-textarea"
						v-model="input"
						:placeholder="followUpMode ? '继续追问或下达新指令...' : '描述你的任务...'"
						placeholder-style="color:#94A3B8"
						:disabled="creating"
						auto-height
						confirm-type="send"
						@confirm="send"
					/>
				</view>
				<view class="send-btn" :class="{ stop: running }" @tap="running ? stop() : send()">
					<image class="btn-icon" :src="running ? '/static/icons/stop-white.svg' : '/static/icons/send-white.svg'" mode="aspectFit" />
				</view>
			</view>
		</view>

		<!-- 任务记录弹层 -->
		<view v-if="historyOpen" class="mask" @tap="historyOpen = false">
			<view class="history-sheet" @tap.stop>
				<view class="sheet-head">
					<text class="sheet-title">任务记录</text>
					<view class="sheet-head-actions"><view class="new-task-btn" @tap="startNewTask"><image class="new-task-icon" src="/static/icons/plus.svg" mode="aspectFit" /><text class="new-task-text">新建任务</text></view><view class="sheet-close" @tap="historyOpen = false">
						<image class="sheet-close-icon" src="/static/icons/close.svg" mode="aspectFit" />
					</view>
				</view></view>
				<scroll-view class="history-list" scroll-y>
					<view v-if="!history.length && !historyLoading" class="history-empty">暂无任务记录</view>
					<view v-if="historyLoading" class="history-empty">加载中...</view>
					<view
						v-for="item in history"
						:key="item.id"
						class="history-item"
						:class="{ current: active && active.id === item.id }"
						@tap="openDetail(item.id)"
					>
						<view class="history-main">
							<text class="history-title">{{ item.title || '未命名任务' }}</text>
							<view class="history-meta">
								<view class="status-dot" :class="statusKey(item.status)"></view>
								<text class="status-text">{{ statusLabel(item.status) }}</text>
								<text class="history-time">{{ formatTime(item.updatedAt || item.createdAt) }}</text>
							</view>
						</view>
						<view class="history-more" @tap.stop="showTaskActions(item)">
							<image class="history-more-icon" src="/static/icons/more.svg" mode="aspectFit" />
						</view>
					</view>
				</scroll-view>
			</view>
		</view>
	</view>
</template>

<script setup>
/**
 * Manus 任务页：任务制轮次对话（对齐 Web 端）
 * 创建任务 → 事件流实时渲染（计划/事件/报告/交付物）→ 追问续轮 → 任务列表管理
 * 旧会话式接口 /ai/manus/chat 保留但本页不再使用
 */
import { ref, computed, reactive, nextTick } from 'vue'
import { onLoad, onShow, onUnload } from '@dcloudio/uni-app'
import { isLoggedIn, getToken } from '../../utils/auth'
import { getServerRoot } from '../../utils/request'
import {
	createManusTask,
	fetchManusTask,
	fetchManusTaskList,
	stopManusTask,
	sendManusFollowUp,
	renameManusTask,
	deleteManusTask,
	streamManusTaskEvents,
	buildDeliverableDownloadUrl
} from '../../utils/api'
import { stopSpeech } from '../../utils/tts'
import chatBubble from '../../components/chat-bubble.vue'

const STATUS_LABELS = {
	PENDING: '排队中',
	RUNNING: '执行中',
	COMPLETED: '已完成',
	STOPPED: '已停止',
	ERROR: '失败'
}

const active = ref(null)
const input = ref('')
const creating = ref(false)
const running = ref(false)
const stopping = ref(false)
const history = ref([])
const historyLoading = ref(false)
const historyOpen = ref(false)
const scrollInto = ref('')
const roundCollapsed = reactive({})
const expandedSet = reactive(new Set())

let streamTask = null
let scrollTimer = null

onLoad(() => {
	if (!isLoggedIn()) {
		uni.reLaunch({ url: '/pages/login/login' })
	}
})

onShow(() => {
	if (!isLoggedIn()) {
		uni.reLaunch({ url: '/pages/login/login' })
		return
	}
	loadHistory()
})

onUnload(() => {
	abortStream()
	stopSpeech()
})

/** 返回应用中心首页 */
function goHome() {
	abortStream()
	stopSpeech()
	uni.reLaunch({ url: '/pages/index/index' })
}

// ---- 轮次切分：按 user_message 事件拆轮，工具调用与结果合并为一行 ----
const rounds = computed(() => {
	if (!active.value) return []
	const list = []
	let current = { question: active.value.task, items: [], plan: null, report: null, deliverables: [] }
	let deliverableSeq = 0
	for (const ev of (active.value.events || [])) {
		if (ev.type === 'user_message') {
			list.push(current)
			current = { question: ev.content || '', items: [], plan: null, report: null, deliverables: [] }
		} else if (ev.type === 'final') {
			current.report = ev.content || current.report
		} else if (ev.type === 'deliverable' && ev.deliverable) {
			deliverableSeq += 1
			current.deliverables.push({ ...ev.deliverable, index: deliverableSeq })
		} else if (ev.type === 'tool_result') {
			const open = [...current.items].reverse().find(
				(item) => item.kind === 'tool' && item.call.toolName === ev.toolName && !item.result)
			if (open) {
				open.result = ev
			} else {
				current.items.push({ kind: 'single', ev })
			}
		} else if (ev.type === 'tool_call') {
			current.items.push({ kind: 'tool', call: ev, result: null })
		} else if (ev.type === 'plan_updated') {
			if (ev.steps) current.plan = ev.steps
			current.items.push({ kind: 'single', ev })
		} else {
			current.items.push({ kind: 'single', ev })
		}
	}
	list.push(current)
	return list
})

const followUpMode = computed(() =>
	active.value && ['COMPLETED', 'STOPPED', 'ERROR'].includes(active.value.status))

function isRoundRunning(ri) {
	return running.value && ri === rounds.value.length - 1
}

function isRoundCollapsed(ri) {
	if (roundCollapsed[ri] !== undefined) return roundCollapsed[ri]
	return !isRoundRunning(ri)
}

function roundThinking(ri) {
	const round = rounds.value[ri]
	return isRoundRunning(ri) && round && !round.items.length
}

function roundTitle(ri) {
	if (!isRoundRunning(ri)) return '执行过程'
	return roundThinking(ri) ? '正在思考' : '正在执行'
}

function toggleRound(ri) {
	roundCollapsed[ri] = !isRoundCollapsed(ri)
}

function resetRoundState() {
	Object.keys(roundCollapsed).forEach((k) => delete roundCollapsed[k])
	expandedSet.clear()
	bubbleCache.clear()
}

function statusLabel(status) {
	return STATUS_LABELS[status] || status || '未知'
}

function statusKey(status) {
	return (status || 'PENDING').toLowerCase()
}

function formatTime(iso) {
	if (!iso) return ''
	const d = new Date(iso)
	if (Number.isNaN(d.getTime())) return ''
	const pad = (n) => String(n).padStart(2, '0')
	return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

function eventTitle(ev) {
	const prefix = ev.agent && ev.agent !== 'main' ? `【${ev.agent}】` : ''
	const base = (() => {
		switch (ev.type) {
			case 'think': return '思考'
			case 'tool_call': return `调用工具 · ${ev.toolName || '未知'}`
			case 'tool_result': return `工具返回 · ${ev.toolName || '未知'}`
			case 'plan_updated': return '计划已更新'
			case 'error': return '执行异常'
			default: return ev.type
		}
	})()
	return prefix + base
}

/** 工具结果/思考内容的一行摘要 */
function briefOf(ev) {
	const text = (ev.toolResult || ev.content || '').replace(/\s+/g, ' ').trim()
	if (!text) return ''
	return text.length > 60 ? text.slice(0, 60) + '…' : text
}

function formatArgs(argsJson) {
	try {
		const obj = JSON.parse(argsJson)
		return Object.entries(obj)
			.map(([k, v]) => `${k}: ${typeof v === 'string' ? v : JSON.stringify(v)}`)
			.join('\n')
	} catch (e) {
		return argsJson
	}
}

/** 事件行点按：工具合并行（有结果）与思考行展开/收起详情 */
function onEventHeadTap(item, ri, i) {
	const expandable = item.kind === 'tool' ? !!item.result : item.ev.type === 'think' || item.ev.type === 'tool_result'
	if (!expandable) return
	const key = ri + '-' + i
	if (expandedSet.has(key)) {
		expandedSet.delete(key)
	} else {
		expandedSet.add(key)
	}
}

function isEventExpanded(item, ri, i) {
	const key = ri + '-' + i
	if (item.kind === 'tool') {
		return !!item.result && expandedSet.has(key)
	}
	return (item.ev.type === 'think' || item.ev.type === 'tool_result') && expandedSet.has(key)
}

// 报告气泡按内容缓存：事件高频到达时避免同一报告反复触发 marked 解析与 mp-html 重设
const bubbleCache = new Map()

/** 报告复用消息气泡组件渲染（mp-html/图片代理/TTS） */
function reportBubble(round) {
	const key = round.report || ''
	let msg = bubbleCache.get(key)
	if (!msg) {
		msg = { role: 'assistant', content: key, references: [], loading: false }
		bubbleCache.set(key, msg)
	}
	return msg
}

function typeLabel(type) {
	return { pdf: 'PDF', image: '图片', text: '文本', binary: '文件' }[type] || type || '文件'
}

/** 打开交付物：下载临时文件后预览（图片 previewImage，其余 openDocument） */
let deliverableLoading = false
function openDeliverable(d) {
	if (!active.value || !d.index || deliverableLoading) return
	deliverableLoading = true
	uni.showLoading({ title: '下载中' })
	uni.downloadFile({
		url: getServerRoot() + '/api' + buildDeliverableDownloadUrl(active.value.id, d.index),
		header: { Authorization: 'Bearer ' + getToken() },
		success: (res) => {
			uni.hideLoading()
			deliverableLoading = false
			if (res.statusCode !== 200) {
				uni.showToast({ title: '下载失败(' + res.statusCode + ')', icon: 'none' })
				return
			}
			if (d.type === 'image') {
				uni.previewImage({ urls: [res.tempFilePath] })
				return
			}
			uni.openDocument({
				filePath: res.tempFilePath,
				showMenu: true,
				fail: () => uni.showToast({ title: '该文件类型不支持在线打开', icon: 'none' })
			})
		},
		fail: () => {
			uni.hideLoading()
			deliverableLoading = false
			uni.showToast({ title: '下载失败，请稍后重试', icon: 'none' })
		}
	})
}

/** 滚动到底部：底部锚点 + 节流 + 清空再设置（锚点恒定不触发，需重置） */
function scrollToBottom() {
	if (!active.value) return
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

async function loadHistory() {
	historyLoading.value = true
	try {
		const res = await fetchManusTaskList()
		history.value = res || []
	} catch (e) {
		console.warn('加载任务列表失败:', e)
	} finally {
		historyLoading.value = false
	}
}

function openHistory() {
	historyOpen.value = true
	loadHistory()
}

/** 新建任务：清空当前视图回新建态（与 Web 端侧栏「新任务」一致） */
function startNewTask() {
	historyOpen.value = false
	abortStream()
	running.value = false
	active.value = null
	input.value = ''
	resetRoundState()
}

function openDetail(taskId) {
	historyOpen.value = false
	abortStream()
	running.value = false
	fetchManusTask(taskId)
		.then((res) => {
			const d = res && res.id ? res : null
			if (!d) return
			resetRoundState()
			active.value = {
				id: d.id,
				task: d.task,
				status: d.status,
				plan: d.plan || [],
				events: d.events || [],
				finalReport: d.finalReport || '',
				deliverables: d.deliverables || []
			}
			if (d.status === 'PENDING' || d.status === 'RUNNING') {
				running.value = true
				subscribeEvents(taskId, (d.events || []).length)
			}
			scrollToBottom()
		})
		.catch((e) => {
			uni.showToast({ title: (e && e.message) || '加载任务详情失败', icon: 'none' })
		})
}

function applyEvent(ev) {
	if (!active.value || !ev || !ev.type) return
	const events = active.value.events
	if (ev.type === 'final') {
		events.push(ev)
		running.value = false
		// final 不携带终态与交付物全量：拉取详情刷新
		fetchManusTask(active.value.id)
			.then((d) => {
				if (active.value && active.value.id === d.id) {
					active.value.status = d.status
				}
			})
			.catch(() => {})
		loadHistory()
	} else {
		events.push(ev)
	}
	scrollToBottom()
}

function subscribeEvents(taskId, after) {
	abortStream()
	running.value = true
	streamTask = streamManusTaskEvents(taskId, after, {
		onEvent: applyEvent,
		onDone: () => {
			running.value = false
			streamTask = null
			loadHistory()
		},
		onError: (err) => {
			running.value = false
			streamTask = null
			if (err && err.code === 401) return
			uni.showToast({ title: '事件流中断，任务仍在后台执行，可稍后在任务记录中查看', icon: 'none' })
			loadHistory()
		}
	})
}

function send() {
	const text = input.value.trim()
	if (!text || creating.value) return
	if (followUpMode.value) {
		submitFollowUp(text)
		return
	}
	creating.value = true
	createManusTask(text)
		.then((res) => {
			input.value = ''
			resetRoundState()
			active.value = {
				id: res.id,
				task: res.task,
				status: res.status,
				plan: [],
				events: [],
				finalReport: '',
				deliverables: []
			}
			scrollToBottom()
			subscribeEvents(res.id, 0)
			loadHistory()
		})
		.catch((e) => {
			uni.showToast({ title: (e && e.message) || '任务创建失败，请稍后重试', icon: 'none' })
		})
		.finally(() => {
			creating.value = false
		})
}

function submitFollowUp(text) {
	creating.value = true
	sendManusFollowUp(active.value.id, text)
		.then((res) => {
			input.value = ''
			active.value.status = res.status
			// 展开即将到来的新一轮（下标 = 当前轮数，此刻追问事件尚未到达）
			roundCollapsed[rounds.value.length] = false
			scrollToBottom()
			subscribeEvents(active.value.id, (active.value.events || []).length)
			loadHistory()
		})
		.catch((e) => {
			uni.showToast({ title: (e && e.message) || '追问提交失败，请稍后重试', icon: 'none' })
		})
		.finally(() => {
			creating.value = false
		})
}

function stop() {
	if (!active.value || !running.value || stopping.value) return
	stopping.value = true
	stopManusTask(active.value.id)
		.catch((e) => {
			uni.showToast({ title: (e && e.message) || '停止失败，请稍后重试', icon: 'none' })
		})
		.finally(() => {
			stopping.value = false
		})
}

function abortStream() {
	if (streamTask) {
		try {
			streamTask.abort()
		} catch (e) {
			// 已结束则忽略
		}
		streamTask = null
	}
}

/** 任务记录长按菜单：改名 / 删除（运行中任务禁删） */
function showTaskActions(item) {
	const finished = ['COMPLETED', 'STOPPED', 'ERROR'].includes(item.status)
	const actions = ['修改标题']
	if (finished) actions.push('删除任务')
	uni.showActionSheet({
		itemList: actions,
		success: (res) => {
			const action = actions[res.tapIndex]
			if (action === '修改标题') {
				renameTask(item)
			} else if (action === '删除任务') {
				deleteTask(item)
			}
		}
	})
}

function renameTask(item) {
	uni.showModal({
		title: '修改标题',
		editable: true,
		placeholderText: item.title || '未命名任务',
		success: (res) => {
			if (!res.confirm) return
			const title = (res.content || '').trim()
			if (!title) {
				uni.showToast({ title: '标题不能为空', icon: 'none' })
				return
			}
			renameManusTask(item.id, title)
				.then(() => {
					uni.showToast({ title: '标题已更新', icon: 'none' })
					loadHistory()
				})
				.catch((e) => {
					uni.showToast({ title: (e && e.message) || '标题更新失败', icon: 'none' })
				})
		}
	})
}

function deleteTask(item) {
	uni.showModal({
		title: '删除任务',
		content: `确定删除「${item.title || '未命名任务'}」吗？删除后无法恢复。`,
		success: (res) => {
			if (!res.confirm) return
			deleteManusTask(item.id)
				.then(() => {
					if (active.value && active.value.id === item.id) {
						abortStream()
						active.value = null
						running.value = false
					}
					loadHistory()
					uni.showToast({ title: '任务已删除', icon: 'none' })
				})
				.catch((e) => {
					uni.showToast({ title: (e && e.message) || '删除失败，请稍后重试', icon: 'none' })
				})
		}
	})
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
	width: 88rpx;
	background: rgba(255, 255, 255, 0.75);
	border: 1rpx solid rgba(16, 185, 129, 0.3);
	border-radius: 24rpx;
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
}

.task-scroll {
	flex: 1;
	min-height: 0;
}

.task-view {
	padding: 16rpx 20rpx 8rpx;
}

.round {
	margin-bottom: 16rpx;
}

/* 用户问题：灰底气泡右对齐 */
.question-row {
	display: flex;
	justify-content: flex-end;
	margin-bottom: 12rpx;
}

.question-bubble {
	max-width: 80%;
	padding: 16rpx 24rpx;
	border-radius: 20rpx 20rpx 6rpx 20rpx;
	background: rgba(100, 116, 139, 0.12);
	color: #1E293B;
	font-size: 28rpx;
	line-height: 1.5;
	word-break: break-all;
}

/* 执行过程折叠行（透明轻量） */
.process-toggle {
	display: flex;
	align-items: center;
	gap: 10rpx;
	padding: 12rpx 16rpx;
	border-radius: 16rpx;
	margin-bottom: 12rpx;
}

.process-toggle:active {
	background: rgba(16, 185, 129, 0.07);
}

.process-icon {
	width: 30rpx;
	height: 30rpx;
}

.process-icon.spinning {
	animation: spin 1.2s linear infinite;
}

.process-title {
	font-size: 24rpx;
	font-weight: 600;
	color: #64748B;
}

.chevron {
	width: 24rpx;
	height: 24rpx;
	transition: transform 0.2s ease;
}

.chevron.expanded {
	transform: rotate(90deg);
}

.thinking-dots {
	display: flex;
	gap: 8rpx;
	margin-left: 4rpx;
}

.thinking-dots .dot {
	width: 10rpx;
	height: 10rpx;
	border-radius: 50%;
	background: #10B981;
	animation: dot-bounce 1.2s infinite ease-in-out;
}

.thinking-dots .dot:nth-child(2) { animation-delay: 0.15s; }
.thinking-dots .dot:nth-child(3) { animation-delay: 0.3s; }

@keyframes dot-bounce {
	0%, 80%, 100% { transform: scale(0.6); opacity: 0.5; }
	40% { transform: scale(1); opacity: 1; }
}

@keyframes spin {
	to { transform: rotate(360deg); }
}

.process-detail {
	margin: 0 0 12rpx 8rpx;
}

/* 计划面板 */
.plan-panel {
	padding: 20rpx 24rpx;
	border-radius: 20rpx;
	background: rgba(255, 255, 255, 0.75);
	border: 1rpx solid rgba(16, 185, 129, 0.18);
	margin-bottom: 12rpx;
}

.panel-head {
	display: flex;
	align-items: center;
	gap: 10rpx;
	margin-bottom: 12rpx;
}

.panel-icon {
	width: 30rpx;
	height: 30rpx;
}

.panel-title {
	font-size: 26rpx;
	font-weight: 700;
	color: #1E293B;
}

.plan-step {
	display: flex;
	align-items: flex-start;
	gap: 12rpx;
	padding: 8rpx 0;
}

.step-icon {
	width: 34rpx;
	height: 34rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
	margin-top: 2rpx;
}

.step-icon-img {
	width: 32rpx;
	height: 32rpx;
}

.step-spinner {
	width: 26rpx;
	height: 26rpx;
	border: 4rpx solid rgba(245, 158, 11, 0.25);
	border-top-color: #F59E0B;
	border-radius: 50%;
	animation: spin 0.9s linear infinite;
}

.step-circle {
	width: 22rpx;
	height: 22rpx;
	border: 3rpx solid #CBD5E1;
	border-radius: 50%;
	box-sizing: border-box;
}

.step-index {
	font-size: 24rpx;
	color: #94A3B8;
	margin-top: 4rpx;
	flex-shrink: 0;
}

.step-body {
	flex: 1;
	min-width: 0;
}

.step-content {
	font-size: 26rpx;
	line-height: 1.5;
	color: #1E293B;
	word-break: break-all;
}

.step-content.dim {
	color: #94A3B8;
}

.step-note {
	display: block;
	font-size: 22rpx;
	color: #94A3B8;
	margin-top: 4rpx;
}

/* 事件卡片 */
.event-card {
	border-radius: 16rpx;
	background: rgba(255, 255, 255, 0.6);
	border: 1rpx solid rgba(226, 232, 240, 0.8);
	margin-bottom: 10rpx;
	overflow: hidden;
}

.event-head {
	display: flex;
	align-items: center;
	gap: 14rpx;
	padding: 16rpx 20rpx;
}

.event-dot {
	width: 14rpx;
	height: 14rpx;
	border-radius: 50%;
	flex-shrink: 0;
}

.dot-think { background: #94A3B8; }
.dot-done { background: #10B981; }
.dot-running { background: #F59E0B; }

.event-main {
	flex: 1;
	min-width: 0;
}

.event-title-row {
	display: flex;
	align-items: center;
	gap: 8rpx;
}

.event-title {
	font-size: 24rpx;
	font-weight: 600;
	color: #1E293B;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.event-chev {
	width: 22rpx;
	height: 22rpx;
	flex-shrink: 0;
	transition: transform 0.2s ease;
}

.event-chev.expanded {
	transform: rotate(180deg);
}

.event-brief {
	display: block;
	font-size: 22rpx;
	color: #94A3B8;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
	margin-top: 4rpx;
}

.event-detail {
	padding: 0 20rpx 16rpx;
}

.detail-block {
	padding: 12rpx 16rpx;
	border-radius: 12rpx;
	background: rgba(15, 23, 42, 0.05);
	font-size: 22rpx;
	line-height: 1.5;
	color: #475569;
	word-break: break-all;
	margin-bottom: 10rpx;
	white-space: pre-wrap;
}

/* 交付物行 */
.deliverable {
	display: flex;
	align-items: center;
	justify-content: space-between;
	gap: 12rpx;
	padding: 16rpx 20rpx;
	border-radius: 16rpx;
	background: rgba(16, 185, 129, 0.07);
	border: 1rpx solid rgba(16, 185, 129, 0.25);
	margin-top: 12rpx;
}

.deliverable-info {
	flex: 1;
	min-width: 0;
	display: flex;
	align-items: center;
	gap: 10rpx;
	flex-wrap: wrap;
}

.deliverable-name {
	font-size: 26rpx;
	font-weight: 600;
	color: #1E293B;
	word-break: break-all;
}

.deliverable-type {
	font-size: 20rpx;
	padding: 2rpx 12rpx;
	border-radius: 999rpx;
	background: rgba(100, 116, 139, 0.1);
	color: #475569;
	flex-shrink: 0;
}

.deliverable-btn {
	padding: 10rpx 24rpx;
	border-radius: 999rpx;
	background: rgba(255, 255, 255, 0.9);
	border: 1rpx solid rgba(16, 185, 129, 0.4);
	flex-shrink: 0;
}

.deliverable-btn-text {
	font-size: 24rpx;
	font-weight: 600;
	color: #059669;
}

.bottom-anchor {
	height: 16rpx;
}

/* 空状态 */
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
	padding: 0 48rpx;
	text-align: center;
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

/* 输入区（执行中变红色停止） */
.input-area {
	padding: 16rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
	background: rgba(255, 255, 255, 0.9);
	border-top: 1rpx solid rgba(16, 185, 129, 0.15);
}

.input-row {
	display: flex;
	align-items: flex-end;
}

.chat-input {
	flex: 1;
	min-height: 88rpx;
	padding: 0 28rpx;
	border-radius: 28rpx;
	box-sizing: border-box;
	display: flex;
	align-items: center;
}

.chat-textarea {
	width: 100%;
	min-height: 44rpx;
	font-size: 30rpx;
	line-height: 44rpx;
	max-height: 200rpx;
}

.send-btn {
	width: 88rpx;
	height: 88rpx;
	border-radius: 24rpx;
	background: linear-gradient(135deg, #10B981, #34D399);
	display: flex;
	align-items: center;
	justify-content: center;
	margin-left: 16rpx;
	flex-shrink: 0;
	box-shadow: 0 6rpx 16rpx rgba(16, 185, 129, 0.3);
	transition: all 0.2s ease;
}

.send-btn.stop {
	background: linear-gradient(135deg, #EF4444, #F87171);
	box-shadow: 0 6rpx 16rpx rgba(239, 68, 68, 0.3);
}

.btn-icon {
	width: 44rpx;
	height: 44rpx;
}

/* 任务记录弹层 */
.mask {
	position: fixed;
	inset: 0;
	z-index: 300;
	background: rgba(15, 23, 42, 0.4);
	display: flex;
	align-items: flex-end;
}

.history-sheet {
	width: 100%;
	max-height: 75vh;
	background: #FFFFFF;
	border-radius: 32rpx 32rpx 0 0;
	display: flex;
	flex-direction: column;
	overflow: hidden;
}

.sheet-head {
	display: flex;
	align-items: center;
	justify-content: space-between;
	padding: 28rpx 32rpx 20rpx;
	border-bottom: 1rpx solid rgba(16, 185, 129, 0.12);
}

.sheet-title {
	font-size: 30rpx;
	font-weight: 700;
	color: #064E3B;
}

.sheet-head-actions {
	display: flex;
	align-items: center;
	gap: 16rpx;
}

.new-task-btn {
	display: flex;
	align-items: center;
	gap: 8rpx;
	padding: 12rpx 24rpx;
	border-radius: 999rpx;
	background: linear-gradient(135deg, #34D399, #059669);
	box-shadow: 0 4rpx 12rpx rgba(5, 150, 105, 0.3);
}

.new-task-icon {
	width: 26rpx;
	height: 26rpx;
}

.new-task-text {
	font-size: 24rpx;
	font-weight: 500;
	color: #FFFFFF;
}

.sheet-close {
	width: 64rpx;
	height: 64rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.sheet-close-icon {
	width: 32rpx;
	height: 32rpx;
}

.history-list {
	flex: 1;
	min-height: 0;
	max-height: calc(75vh - 120rpx);
	padding: 12rpx 24rpx calc(24rpx + env(safe-area-inset-bottom));
	box-sizing: border-box;
}

.history-empty {
	padding: 60rpx 0;
	text-align: center;
	font-size: 26rpx;
	color: #94A3B8;
}

.history-item {
	display: flex;
	align-items: center;
	padding: 20rpx 16rpx;
	border-radius: 16rpx;
	margin-bottom: 8rpx;
}

.history-item.current {
	background: rgba(16, 185, 129, 0.1);
}

.history-item:active {
	background: rgba(16, 185, 129, 0.07);
}

.history-main {
	flex: 1;
	min-width: 0;
}

.history-title {
	display: block;
	font-size: 27rpx;
	font-weight: 600;
	color: #1E293B;
	white-space: nowrap;
	overflow: hidden;
	text-overflow: ellipsis;
}

.history-meta {
	display: flex;
	align-items: center;
	gap: 10rpx;
	margin-top: 6rpx;
}

.status-dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	flex-shrink: 0;
}

.status-dot.completed { background: #10B981; }
.status-dot.running { background: #F59E0B; }
.status-dot.pending { background: #94A3B8; }
.status-dot.stopped { background: #F59E0B; }
.status-dot.error { background: #EF4444; }

.status-text {
	font-size: 22rpx;
	color: #94A3B8;
}

.history-time {
	font-size: 22rpx;
	color: #CBD5E1;
	margin-left: auto;
}

.history-more {
	width: 64rpx;
	height: 64rpx;
	display: flex;
	align-items: center;
	justify-content: center;
	flex-shrink: 0;
}

.history-more-icon {
	width: 34rpx;
	height: 34rpx;
}
</style>
