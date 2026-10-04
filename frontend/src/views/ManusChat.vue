<template>
  <div class="task-page">
    <header class="task-header">
      <router-link to="/" class="back" aria-label="返回首页">
        <ArrowLeft class="icon" size="16" />
        返回
      </router-link>
      <h1>AI 超级智能体</h1>
      <button
        class="history-toggle"
        :class="{ active: sidebarOpen }"
        @click="sidebarOpen = !sidebarOpen"
        aria-label="任务记录"
      >
        <ListTodo class="icon" size="18" />
      </button>
    </header>

    <div class="task-body">
      <!-- 任务记录侧栏 -->
      <div v-if="sidebarOpen" class="sidebar-backdrop" @click="sidebarOpen = false"></div>
      <aside class="task-sidebar" :class="{ open: sidebarOpen }">
        <div class="sidebar-head">
          <span class="sidebar-title">任务记录</span>
          <button class="new-task-btn" @click="startNewTask" aria-label="新建任务">
            <Plus class="icon" size="16" />
            新任务
          </button>
        </div>
        <div class="task-list">
          <div v-if="!history.length && !historyLoading" class="task-list-empty">暂无任务记录</div>
          <div v-if="historyLoading" class="task-list-empty">加载中…</div>
          <button
            v-for="item in history"
            :key="item.id"
            class="history-item"
            :class="{ current: active && active.id === item.id }"
            @click="openDetail(item.id)"
          >
            <span class="history-item-title">{{ item.title || '未命名任务' }}</span>
            <span class="history-item-meta">
              <span class="status-dot" :class="statusKey(item.status)"></span>
              <span class="status-text">{{ statusLabel(item.status) }}</span>
              <span v-if="item.totalSteps" class="history-progress">{{ item.doneSteps }}/{{ item.totalSteps }} 步</span>
              <span class="history-time">{{ formatTime(item.updatedAt || item.createdAt) }}</span>
            </span>
          </button>
        </div>
      </aside>

      <!-- 任务主区 -->
      <main class="task-main" ref="mainRef">
        <!-- 空状态 -->
        <div v-if="!active" class="empty-state">
          <div class="robot-avatar">
            <svg viewBox="0 0 48 48" fill="none" class="robot-svg">
              <line x1="24" y1="4" x2="24" y2="12" stroke="url(#robotGrad)" stroke-width="2.5" stroke-linecap="round" class="antenna"/>
              <circle cx="24" cy="3" r="2.5" fill="#93C5FD" class="antenna-dot"/>
              <rect x="10" y="12" width="28" height="20" rx="6" stroke="url(#robotGrad)" stroke-width="2" fill="rgba(147,197,253,0.1)"/>
              <circle cx="19" cy="22" r="3.5" fill="#93C5FD" class="eye eye-left"/>
              <circle cx="29" cy="22" r="3.5" fill="#93C5FD" class="eye eye-right"/>
              <line x1="18" y1="28" x2="30" y2="28" stroke="#BFDBFE" stroke-width="1.8" stroke-linecap="round" class="mouth"/>
              <rect x="14" y="32" width="20" height="10" rx="3" stroke="url(#robotGrad)" stroke-width="1.5" fill="rgba(147,197,253,0.06)"/>
              <circle cx="24" cy="37" r="1.5" fill="#BFDBFE" class="body-dot"/>
              <defs>
                <linearGradient id="robotGrad" x1="0" y1="0" x2="48" y2="48">
                  <stop offset="0%" stop-color="#93C5FD"/>
                  <stop offset="100%" stop-color="#DBEAFE"/>
                </linearGradient>
              </defs>
            </svg>
          </div>
          <h2 class="empty-title">把任务交给超级智能体</h2>
          <p class="empty-sub">描述你的任务，智能体会自动规划步骤、调用工具并逐步执行</p>
          <div class="suggest-list">
            <button
              v-for="s in suggestions"
              :key="s"
              class="suggest-chip"
              @click="inputText = s"
            >{{ s }}</button>
          </div>
        </div>

        <!-- 任务视图 -->
        <div v-else class="task-view">
          <div class="task-card">
            <div class="task-card-head">
              <span class="status-chip" :class="statusKey(active.status)">
                <span v-if="active.status === 'RUNNING'" class="pulse-dot"></span>
                {{ statusLabel(active.status) }}
              </span>
              <button
                v-if="running && !stopping"
                class="stop-btn"
                @click="stopTask"
                aria-label="停止任务"
              >
                <Square class="icon" size="14" />
                停止
              </button>
              <span v-if="stopping" class="stopping-hint">停止中，等待当前步骤完成…</span>
            </div>
            <p class="task-text">{{ active.task }}</p>
          </div>

          <!-- 计划进度面板 -->
          <section v-if="active.plan && active.plan.length" class="plan-panel" aria-label="任务计划">
            <div class="section-head">
              <ListChecks class="icon" size="16" />
              <span>执行计划</span>
            </div>
            <div
              v-for="step in active.plan"
              :key="step.index"
              class="plan-step"
              :class="step.status"
            >
              <span class="step-icon">
                <CircleCheck v-if="step.status === 'done'" size="16" />
                <Loader v-else-if="step.status === 'in_progress'" size="16" class="spin" />
                <Ban v-else-if="step.status === 'skipped'" size="16" />
                <CircleDashed v-else size="16" />
              </span>
              <span class="step-index">{{ step.index }}</span>
              <span class="step-content">
                {{ step.content }}
                <em v-if="step.note" class="step-note">{{ step.note }}</em>
              </span>
            </div>
          </section>

          <!-- 执行时间线 -->
          <section v-if="active.events.length" class="timeline" aria-label="执行过程">
            <div class="section-head">
              <Brain class="icon" size="16" />
              <span>执行过程</span>
            </div>
            <div
              v-for="(ev, i) in active.events"
              :key="i"
              class="event-card"
              :class="ev.type"
            >
              <div class="event-head" @click="ev.type === 'tool_result' ? toggleExpand(i) : null">
                <span class="event-icon">
                  <Brain v-if="ev.type === 'think'" size="14" />
                  <Wrench v-else-if="ev.type === 'tool_call'" size="14" />
                  <ChevronRight v-else-if="ev.type === 'tool_result'" size="14" :class="{ expanded: expandedSet.has(i) }" />
                  <ListChecks v-else-if="ev.type === 'plan_updated'" size="14" />
                  <CircleAlert v-else-if="ev.type === 'error'" size="14" />
                  <FileText v-else size="14" />
                </span>
                <span class="event-title">{{ eventTitle(ev) }}</span>
                <span class="event-time">{{ formatClock(ev.timestamp) }}</span>
              </div>
              <div
                v-if="ev.type === 'think' && ev.content"
                class="event-body markdown-body"
                v-html="renderMarkdown(ev.content)"
              ></div>
              <div v-if="ev.type === 'tool_call' && ev.toolArgs" class="event-body">
                <code class="tool-args">{{ formatArgs(ev.toolArgs) }}</code>
              </div>
              <div v-if="ev.type === 'tool_result' && expandedSet.has(i) && ev.toolResult" class="event-body">
                <pre class="tool-result">{{ ev.toolResult }}</pre>
              </div>
              <div v-if="ev.type === 'plan_updated'" class="event-body compact">
                {{ planSummary(ev.steps) }}
              </div>
            </div>
          </section>

          <!-- 最终报告 -->
          <section v-if="active.finalReport" class="report-panel" aria-label="任务报告">
            <div class="section-head">
              <FileText class="icon" size="16" />
              <span>任务报告</span>
            </div>
            <div class="report-body markdown-body" v-html="renderMarkdown(active.finalReport)"></div>
          </section>

        </div>
      </main>

      <!-- 错误横幅（固定于输入区上方，任何视图状态均可见） -->
      <div v-if="streamError" class="stream-error-float">
        <CircleAlert size="16" />
        <span>{{ streamError }}</span>
        <button class="stream-error-close" @click="streamError = ''" aria-label="关闭提示">
          <X size="14" />
        </button>
      </div>
    </div>

    <!-- 输入区 -->
    <div class="input-area">
      <textarea
        v-model="inputText"
        placeholder="描述你的任务，例如：搜索最新的大模型资讯，整理成一份摘要…"
        rows="2"
        :disabled="creating"
        @keydown.enter.exact.prevent="send"
      ></textarea>
      <button
        class="send-btn"
        :disabled="creating || !inputText.trim()"
        @click="send"
        aria-label="开始执行任务"
      >
        <span v-if="!creating" class="btn-inner">
          <ArrowRight class="icon" size="18" />
          <span class="btn-text">开始任务</span>
        </span>
        <span v-else class="btn-inner">
          <Loader class="icon spin" size="18" />
          <span class="btn-text">创建中</span>
        </span>
      </button>
    </div>

    <!-- 图片预览弹窗 -->
    <Teleport to="body">
      <div v-if="previewImage.show" class="image-preview-overlay" @click.self="closePreview">
        <button class="image-preview-close" @click="closePreview" title="关闭" aria-label="关闭预览">
          <X size="22" />
        </button>
        <img :src="previewImage.src" :alt="previewImage.alt" class="image-preview-img" @click.self="closePreview" />
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, nextTick, onMounted, onUnmounted } from 'vue'
import {
  createManusTask,
  stopManusTask,
  fetchManusTaskList,
  fetchManusTask,
  streamManusTaskEvents,
} from '../api/request'
import { linkifyHtml } from '../utils/linkify'
import { previewImage, openPreview, closePreview } from '../utils/previewImage'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  ArrowLeft, ArrowRight, Square, X,
  Brain, Wrench, ListChecks, ListTodo, Plus,
  CircleCheck, CircleDashed, CircleAlert, ChevronRight,
  Loader, FileText, Ban,
} from '@lucide/vue'

const STATUS_LABELS = {
  PENDING: '排队中',
  RUNNING: '执行中',
  COMPLETED: '已完成',
  STOPPED: '已停止',
  ERROR: '失败',
}

const suggestions = [
  '帮我查一下今天的 AI 领域新闻，整理成要点摘要',
  '搜索 Spring Boot 3 的虚拟线程特性，写一份学习笔记保存为文件',
  '分析「如何科学地做知识管理」，给出结构化建议',
]

const inputText = ref('')
const creating = ref(false)
const stopping = ref(false)
const streamError = ref('')
const active = ref(null) // 当前任务视图：{ id, task, status, plan, events, finalReport }
const history = ref([])
const historyLoading = ref(false)
const sidebarOpen = ref(false)
const expandedSet = reactive(new Set())
const mainRef = ref(null)
const abortController = ref(null)

// 是否有任务在执行/排队（用于显示停止按钮与状态刷新）
const running = ref(false)
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

function formatClock(iso) {
  if (!iso) return ''
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return ''
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`
}

/** AI 文本渲染为安全的 Markdown HTML（图片走后端代理，链接新窗口打开） */
function renderMarkdown(content) {
  if (!content) return ''
  const renderer = new marked.Renderer()
  renderer.link = ({ href, title, text }) => `<a target="_blank" href="${href}" title="${title || ''}">${text}</a>`
  renderer.heading = ({ depth, text }) => `<h${depth}>${text}</h${depth}>`
  renderer.del = ({ text }) => text
  renderer.image = ({ href, title, text }) => {
    const escapedSrc = encodeURIComponent(href)
    const escapedAlt = text ? text.replace(/"/g, '&quot;') : ''
    return `<img src="/api/image-proxy?url=${escapedSrc}" alt="${escapedAlt}" class="chat-image" loading="lazy"`
      + ` style="width:100%;height:auto;display:block;border-radius:8px;margin:4px 0;" />`
  }
  const rawHtml = marked.parse(content, { renderer, gfm: true })
  return linkifyHtml(DOMPurify.sanitize(rawHtml))
}

function eventTitle(ev) {
  switch (ev.type) {
    case 'think': return '思考'
    case 'tool_call': return `调用工具 · ${ev.toolName || '未知'}`
    case 'tool_result': return `工具返回 · ${ev.toolName || '未知'}`
    case 'plan_updated': return '计划已更新'
    case 'error': return '执行异常'
    case 'final': return '任务结束'
    default: return ev.type
  }
}

function planSummary(steps) {
  if (!steps || !steps.length) return '计划为空'
  const done = steps.filter((s) => s.status === 'done' || s.status === 'skipped').length
  return `共 ${steps.length} 步，已完成 ${done} 步`
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

function toggleExpand(index) {
  if (expandedSet.has(index)) {
    expandedSet.delete(index)
  } else {
    expandedSet.add(index)
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (mainRef.value) mainRef.value.scrollTop = mainRef.value.scrollHeight
  })
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const res = await fetchManusTaskList()
    history.value = res.data || []
  } catch (e) {
    console.warn('加载任务列表失败:', e)
  } finally {
    historyLoading.value = false
  }
}

function applyEvent(ev) {
  if (!active.value || !ev || !ev.type) return
  if (ev.type === 'plan_updated') {
    active.value.plan = ev.steps || active.value.plan
    active.value.events.push(ev)
  } else if (ev.type === 'final') {
    active.value.finalReport = ev.content || active.value.finalReport
    active.value.events.push(ev)
    running.value = false
    stopping.value = false
    // final 事件不携带终态：拉取详情刷新真实状态与报告
    fetchManusTask(active.value.id)
      .then((res) => {
        if (active.value && active.value.id === res.data.id) {
          active.value.status = res.data.status
          active.value.finalReport = res.data.finalReport || active.value.finalReport
        }
      })
      .catch(() => {})
    loadHistory()
  } else {
    active.value.events.push(ev)
  }
  scrollToBottom()
}

function send() {
  const text = inputText.value.trim()
  if (!text || creating.value) return
  creating.value = true
  streamError.value = ''
  createManusTask(text)
    .then((res) => {
      inputText.value = ''
      expandedSet.clear()
      active.value = {
        id: res.data.id,
        task: res.data.task,
        status: res.data.status,
        plan: [],
        events: [],
        finalReport: '',
      }
      running.value = true
      stopping.value = false
      sidebarOpen.value = false
      loadHistory()
      subscribeEvents(res.data.id)
      scrollToBottom()
    })
    .catch((e) => {
      streamError.value = e?.response?.data?.message || '任务创建失败，请稍后重试'
    })
    .finally(() => {
      creating.value = false
    })
}

function subscribeEvents(taskId) {
  abortController.value?.abort()
  const controller = new AbortController()
  abortController.value = controller
  streamManusTaskEvents(taskId, {
    onEvent: applyEvent,
    onDone() {
      // 连接结束：若未收到 final 事件也视为结束（保持已回放内容）
      running.value = false
      stopping.value = false
      loadHistory()
    },
    onError(err) {
      running.value = false
      stopping.value = false
      streamError.value = err?.message ? '事件流中断：' + err.message : '事件流中断，任务仍在后台执行，可稍后在任务记录中回放'
      loadHistory()
    },
  }, controller.signal)
}

async function stopTask() {
  if (!active.value || stopping.value) return
  stopping.value = true
  try {
    await stopManusTask(active.value.id)
  } catch (e) {
    stopping.value = false
    streamError.value = e?.response?.data?.message || '停止失败，请稍后重试'
  }
}

async function openDetail(taskId) {
  abortController.value?.abort()
  abortController.value = null
  running.value = false
  stopping.value = false
  streamError.value = ''
  try {
    const res = await fetchManusTask(taskId)
    expandedSet.clear()
    active.value = {
      id: res.data.id,
      task: res.data.task,
      status: res.data.status,
      plan: res.data.plan || [],
      events: res.data.events || [],
      finalReport: res.data.finalReport || '',
    }
    sidebarOpen.value = false
    // 未到终态的任务接续实时事件流，保持状态与时间线更新
    if (res.data.status === 'PENDING' || res.data.status === 'RUNNING') {
      running.value = true
      subscribeEvents(taskId)
    }
    scrollToBottom()
  } catch (e) {
    streamError.value = e?.response?.data?.message || '加载任务详情失败'
  }
}

function startNewTask() {
  abortController.value?.abort()
  abortController.value = null
  running.value = false
  stopping.value = false
  streamError.value = ''
  active.value = null
  inputText.value = ''
  expandedSet.clear()
  sidebarOpen.value = false
}

/** 任务主区点击：图片放大预览（事件委托） */
function handleMainClick(e) {
  const img = e.target?.closest?.('img.chat-image')
  if (img) {
    openPreview(img.getAttribute('src') || '', img.getAttribute('alt') || '')
  }
}

function handleKeydown(e) {
  if (e.key === 'Escape' && previewImage.value.show) {
    closePreview()
  }
}

onMounted(() => {
  loadHistory()
  document.addEventListener('keydown', handleKeydown)
  mainRef.value?.addEventListener('click', handleMainClick)
})

onUnmounted(() => {
  abortController.value?.abort()
  document.removeEventListener('keydown', handleKeydown)
  mainRef.value?.removeEventListener('click', handleMainClick)
})
</script>

<style scoped>
.task-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: var(--bg-primary, #f5f7fb);
  color: var(--text-primary, #1e293b);
  overflow: hidden;
}

/* ===== 头部 ===== */
.task-header {
  flex-shrink: 0;
  padding: 0 1.25rem;
  height: 60px;
  display: flex;
  align-items: center;
  gap: 1rem;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow:
    0 1px 4px rgba(16, 185, 129, 0.06),
    0 0 0 1px rgba(255, 255, 255, 0.4) inset;
}
.task-header h1 {
  flex: 1;
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
}
.back {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-height: 44px;
  padding: 0 0.75rem;
  border-radius: 999px;
  font-size: 0.875rem;
  color: var(--text-secondary, #475569);
  text-decoration: none;
  transition: background 200ms ease, color 200ms ease;
}
.back:hover {
  background: rgba(16, 185, 129, 0.08);
  color: var(--text-primary, #1e293b);
}
.history-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 12px;
  background: transparent;
  color: var(--text-secondary, #475569);
  cursor: pointer;
  transition: background 200ms ease;
}
.history-toggle:hover,
.history-toggle.active {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}

/* ===== 主体布局 ===== */
.task-body {
  flex: 1;
  min-height: 0;
  display: flex;
  position: relative;
}

/* ===== 侧栏 ===== */
.task-sidebar {
  width: 260px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.55);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-right: 1px solid rgba(255, 255, 255, 0.4);
}
.sidebar-head {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0.9rem 1rem;
}
.sidebar-title {
  font-size: 0.9rem;
  font-weight: 700;
}
.new-task-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.3rem;
  min-height: 36px;
  padding: 0 0.7rem;
  border: 1px solid rgba(16, 185, 129, 0.35);
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.08);
  color: #10b981;
  font-size: 0.8rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 200ms ease, box-shadow 200ms ease;
}
.new-task-btn:hover {
  background: rgba(16, 185, 129, 0.16);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1);
}
.task-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 0.6rem 1rem;
}
.task-list-empty {
  padding: 1.5rem 0.5rem;
  text-align: center;
  font-size: 0.82rem;
  color: var(--text-tertiary, #94a3b8);
}
.history-item {
  display: block;
  width: 100%;
  text-align: left;
  padding: 0.6rem 0.75rem;
  margin-bottom: 0.4rem;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  cursor: pointer;
  transition: background 200ms ease, border-color 200ms ease;
}
.history-item:hover {
  background: rgba(16, 185, 129, 0.07);
}
.history-item.current {
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.35);
}
.history-item-title {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  padding-right: 8px;
}
.history-item-meta {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-top: 0.3rem;
  font-size: 0.72rem;
  color: var(--text-tertiary, #94a3b8);
}
.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
}
.status-dot.completed { background: #10b981; }
.status-dot.running { background: #f59e0b; }
.status-dot.pending { background: #94a3b8; }
.status-dot.stopped { background: #f59e0b; }
.status-dot.error { background: #ef4444; }
.history-progress { color: #10b981; font-weight: 600; }
.history-time {
  margin-left: auto;
  white-space: nowrap;
}

/* ===== 主区 ===== */
.task-main {
  flex: 1;
  min-width: 0;
  overflow-y: auto;
  padding: 1.25rem 1.5rem 2rem;
  scroll-behavior: smooth;
}

/* 空状态 */
.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 1rem;
}
.robot-avatar { width: 96px; height: 96px; }
.robot-svg { width: 100%; height: 100%; }
.empty-title {
  margin: 1rem 0 0.4rem;
  font-size: 1.3rem;
  font-weight: 800;
  color: var(--text-primary, #1e293b);
}
.empty-sub {
  margin: 0 0 1.5rem;
  font-size: 0.9rem;
  color: var(--text-secondary, #475569);
}
.suggest-list {
  display: flex;
  flex-direction: column;
  gap: 0.6rem;
  width: min(480px, 100%);
}
.suggest-chip {
  min-height: 44px;
  padding: 0.6rem 1rem;
  border: 1px solid var(--border-light, #e2e8f0);
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.7);
  color: var(--text-secondary, #475569);
  font-size: 0.85rem;
  text-align: left;
  cursor: pointer;
  transition: border-color 200ms ease, background 200ms ease, transform 200ms ease;
}
.suggest-chip:hover {
  border-color: rgba(16, 185, 129, 0.45);
  background: rgba(16, 185, 129, 0.06);
}

/* 任务卡 */
.task-view {
  max-width: 860px;
  margin: 0 auto;
}
.task-card {
  padding: 1rem 1.1rem;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(15, 23, 42, 0.06));
}
.task-card-head {
  display: flex;
  align-items: center;
  gap: 0.75rem;
  margin-bottom: 0.5rem;
}
.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  padding: 0.2rem 0.7rem;
  border-radius: 999px;
  font-size: 0.75rem;
  font-weight: 700;
}
.status-chip.completed { background: rgba(16, 185, 129, 0.12); color: #059669; }
.status-chip.running { background: rgba(245, 158, 11, 0.14); color: #b45309; }
.status-chip.pending { background: rgba(100, 116, 139, 0.12); color: #475569; }
.status-chip.stopped { background: rgba(245, 158, 11, 0.14); color: #b45309; }
.status-chip.error { background: rgba(239, 68, 68, 0.12); color: #b91c1c; }
.pulse-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #f59e0b;
  animation: pulse 1.2s ease-in-out infinite;
}
@keyframes pulse {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.4; transform: scale(0.8); }
}
.stop-btn {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  min-height: 32px;
  padding: 0 0.8rem;
  border: 1px solid rgba(239, 68, 68, 0.4);
  border-radius: 999px;
  background: rgba(239, 68, 68, 0.06);
  color: #dc2626;
  font-size: 0.78rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 200ms ease;
}
.stop-btn:hover {
  background: rgba(239, 68, 68, 0.14);
}
.stopping-hint {
  font-size: 0.75rem;
  color: var(--text-tertiary, #94a3b8);
}
.task-text {
  margin: 0;
  font-size: 0.95rem;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 分区块通用 */
.task-view section {
  margin-top: 1rem;
}
.section-head {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  margin-bottom: 0.6rem;
  font-size: 0.88rem;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
}
.section-head .icon {
  color: #10b981;
}

/* 计划面板 */
.plan-panel {
  padding: 0.9rem 1rem;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.5);
}
.plan-step {
  display: flex;
  align-items: flex-start;
  gap: 0.55rem;
  padding: 0.4rem 0;
  font-size: 0.88rem;
  line-height: 1.55;
}
.step-icon {
  display: inline-flex;
  align-items: center;
  margin-top: 2px;
  color: var(--text-tertiary, #94a3b8);
  flex-shrink: 0;
}
.plan-step.done .step-icon { color: #10b981; }
.plan-step.in_progress .step-icon { color: #f59e0b; }
.plan-step.skipped .step-icon { color: #94a3b8; }
.plan-step.done .step-content { color: var(--text-secondary, #475569); }
.step-index {
  flex-shrink: 0;
  min-width: 1.2rem;
  font-weight: 700;
  color: var(--text-tertiary, #94a3b8);
  font-size: 0.8rem;
  margin-top: 2px;
}
.step-content {
  word-break: break-word;
}
.step-note {
  display: block;
  font-style: normal;
  font-size: 0.78rem;
  color: var(--text-tertiary, #94a3b8);
  margin-top: 0.15rem;
}
.spin {
  animation: spin 1s linear infinite;
}
@keyframes spin {
  to { transform: rotate(360deg); }
}

/* 时间线 */
.event-card {
  margin-bottom: 0.6rem;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  overflow: hidden;
  transition: border-color 200ms ease;
}
.event-card:hover {
  border-color: rgba(16, 185, 129, 0.25);
}
.event-card.error {
  border-color: rgba(239, 68, 68, 0.35);
  background: rgba(239, 68, 68, 0.04);
}
.event-card.final {
  border-color: rgba(16, 185, 129, 0.35);
}
.event-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.85rem;
}
.event-card.tool_result .event-head {
  cursor: pointer;
}
.event-icon {
  display: inline-flex;
  color: var(--text-tertiary, #94a3b8);
  flex-shrink: 0;
}
.event-card.think .event-icon { color: #10b981; }
.event-card.tool_call .event-icon { color: #8b5cf6; }
.event-card.tool_result .event-icon { color: #8b5cf6; }
.event-card.error .event-icon { color: #ef4444; }
.event-title {
  flex: 1;
  min-width: 0;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
}
.event-time {
  flex-shrink: 0;
  font-size: 0.7rem;
  color: var(--text-tertiary, #94a3b8);
  font-variant-numeric: tabular-nums;
}
.event-body {
  padding: 0 0.85rem 0.7rem;
  font-size: 0.85rem;
  line-height: 1.65;
  color: var(--text-secondary, #475569);
  word-break: break-word;
}
.event-body.compact {
  font-size: 0.78rem;
  color: var(--text-tertiary, #94a3b8);
}
.tool-args {
  display: block;
  padding: 0.5rem 0.7rem;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.05);
  font-size: 0.78rem;
  white-space: pre-wrap;
  word-break: break-all;
}
.tool-result {
  margin: 0;
  max-height: 320px;
  overflow-y: auto;
  padding: 0.5rem 0.7rem;
  border-radius: 10px;
  background: rgba(15, 23, 42, 0.05);
  font-size: 0.76rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-all;
}
.event-icon svg.expanded {
  transition: transform 200ms ease;
  transform: rotate(90deg);
}

/* 报告 */
.report-panel {
  padding: 0.9rem 1rem;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.75);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(16, 185, 129, 0.25);
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(15, 23, 42, 0.06));
}
.report-body {
  font-size: 0.9rem;
  line-height: 1.7;
}

/* 错误横幅（悬浮于主区底部） */
.stream-error-float {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  bottom: 14px;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 0.5rem;
  max-width: min(680px, calc(100% - 2rem));
  padding: 0.65rem 0.9rem;
  border-radius: 12px;
  background: rgba(254, 242, 242, 0.95);
  border: 1px solid rgba(239, 68, 68, 0.35);
  box-shadow: 0 4px 16px rgba(239, 68, 68, 0.15);
  color: #b91c1c;
  font-size: 0.85rem;
}
.stream-error-float span {
  flex: 1;
  min-width: 0;
  word-break: break-word;
}
.stream-error-close {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #b91c1c;
  cursor: pointer;
  transition: background 200ms ease;
}
.stream-error-close:hover {
  background: rgba(239, 68, 68, 0.12);
}
.stream-error-close:focus-visible {
  outline: 2px solid #10b981;
  outline-offset: 2px;
}

/* ===== 输入区 ===== */
.input-area {
  flex-shrink: 0;
  display: flex;
  align-items: flex-end;
  gap: 0.75rem;
  padding: 0.9rem 1.25rem calc(0.9rem + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-top: 1px solid rgba(255, 255, 255, 0.4);
}
.input-area textarea {
  flex: 1;
  min-width: 0;
  max-height: 30vh;
  padding: 0.7rem 1rem;
  border: 1px solid var(--border-light, #e2e8f0);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.85);
  color: var(--text-primary, #1e293b);
  font-size: 16px;
  font-family: inherit;
  line-height: 1.5;
  resize: none;
  outline: none;
  transition: border-color 200ms ease, box-shadow 200ms ease;
}
.input-area textarea:focus {
  border-color: rgba(16, 185, 129, 0.55);
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}
.input-area textarea:disabled {
  opacity: 0.6;
}
.send-btn {
  flex-shrink: 0;
  min-width: 96px;
  min-height: 44px;
  padding: 0 1.1rem;
  border: none;
  border-radius: 14px;
  background: linear-gradient(135deg, #10b981, #34d399);
  color: #fff;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
  transition: opacity 200ms ease, box-shadow 200ms ease;
}
.send-btn:hover:not(:disabled) {
  box-shadow: 0 4px 14px rgba(16, 185, 129, 0.35);
}
.send-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
}
.btn-inner {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
}

/* 图片预览 */
.image-preview-overlay {
  position: fixed;
  inset: 0;
  z-index: 3000;
  display: flex;
  align-items: center;
  justify-content: center;
  background: rgba(15, 23, 42, 0.75);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
}
.image-preview-close {
  position: absolute;
  top: 1rem;
  right: 1rem;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border: none;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.15);
  color: #fff;
  cursor: pointer;
  transition: background 200ms ease;
}
.image-preview-close:hover {
  background: rgba(255, 255, 255, 0.28);
}
.image-preview-img {
  max-width: 92vw;
  max-height: 88vh;
  border-radius: 12px;
  object-fit: contain;
}

/* 焦点可见性 */
.back:focus-visible,
.history-toggle:focus-visible,
.new-task-btn:focus-visible,
.history-item:focus-visible,
.suggest-chip:focus-visible,
.stop-btn:focus-visible,
.send-btn:focus-visible,
.image-preview-close:focus-visible {
  outline: 2px solid #10b981;
  outline-offset: 2px;
}

/* ===== 移动端 ===== */
@media (max-width: 768px) {
  .history-toggle {
    display: flex;
  }
  .task-sidebar {
    position: fixed;
    top: 60px;
    bottom: 0;
    left: 0;
    z-index: 200;
    width: min(300px, 84vw);
    transform: translateX(-100%);
    transition: transform 250ms ease;
    box-shadow: 0 0 30px rgba(15, 23, 42, 0.18);
  }
  .task-sidebar.open {
    transform: translateX(0);
  }
  .sidebar-backdrop {
    position: fixed;
    inset: 60px 0 0;
    z-index: 150;
    background: rgba(15, 23, 42, 0.35);
  }
  .task-main {
    padding: 1rem 0.9rem 1.5rem;
  }
  .send-btn .btn-text {
    display: none;
  }
  .send-btn {
    min-width: 44px;
    padding: 0 0.8rem;
  }
  .empty-title {
    font-size: 1.15rem;
  }
}

/* 减少动效偏好 */
@media (prefers-reduced-motion: reduce) {
  .spin,
  .pulse-dot {
    animation: none;
  }
  .task-sidebar {
    transition: none;
  }
  .task-main {
    scroll-behavior: auto;
  }
}
</style>
