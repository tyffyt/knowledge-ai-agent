<template>
  <div class="task-page">
    <!-- 任务记录侧栏（全高，与知识问答页布局一致） -->
    <div v-if="sidebarOpen" class="sidebar-backdrop" @click="sidebarOpen = false"></div>
    <aside class="task-sidebar" :class="{ open: sidebarOpen }">
      <div class="sidebar-head">
        <span class="sidebar-title">任务记录</span>
        <button class="new-task-btn" @click="startNewTask" aria-label="新建任务">
          <Plus class="icon" size="16" />
          新任务
        </button>
      </div>
      <div class="task-list" @scroll="menuTask = null">
        <div v-if="!history.length && !historyLoading" class="task-list-empty">暂无任务记录</div>
        <div v-if="historyLoading" class="task-list-empty">加载中…</div>
        <div
          v-for="item in history"
          :key="item.id"
          class="history-item"
          :class="{ current: active && active.id === item.id && !batchMode, 'batch-mode': batchMode }"
        >
          <div class="history-item-main" @click="onItemClick(item)">
            <span class="history-item-title">{{ item.title || '未命名任务' }}</span>
            <span class="history-item-meta">
              <span class="status-dot" :class="statusKey(item.status)"></span>
              <span class="status-text">{{ statusLabel(item.status) }}</span>
              <span v-if="item.totalSteps" class="history-progress">{{ item.doneSteps }}/{{ item.totalSteps }} 步</span>
              <span class="history-time">{{ formatTime(item.updatedAt || item.createdAt) }}</span>
            </span>
          </div>
          <!-- 非批量模式：三点按钮（菜单经 Teleport 渲染到 body，避免被列表滚动容器裁剪） -->
          <div v-if="!batchMode" class="history-more-wrap">
            <button class="history-more-btn" @click.stop="toggleMenu(item, $event)" aria-label="更多操作">
              <MoreVertical class="icon" size="18" />
            </button>
          </div>
          <!-- 批量模式：勾选圆圈（执行中/排队中的任务后端禁删，勾选禁用） -->
          <div
            v-else-if="isFinishedStatus(item.status)"
            class="batch-check-wrap"
            @click.stop="toggleSelectTask(item.id)"
          >
            <span v-if="selectedTaskIds.includes(item.id)" class="batch-circle selected">
              <Check class="icon" size="14" />
            </span>
            <span v-else class="batch-circle"></span>
          </div>
          <div
            v-else
            class="batch-check-wrap disabled"
            :title="item.status === 'RUNNING' ? '执行中的任务须先停止再删除' : '排队中的任务须先停止再删除'"
          >
            <span class="batch-circle disabled"></span>
          </div>
        </div>
      </div>
      <!-- 批量管理模式底部按钮条（取消 / 删除(N)） -->
      <div v-if="batchMode" class="batch-bar">
        <button class="batch-bar-btn" @click="exitBatchMode">取消</button>
        <button class="batch-bar-btn delete" :disabled="selectedTaskIds.length === 0" @click="confirmBatchDelete = true">
          删除 ({{ selectedTaskIds.length }})
        </button>
      </div>
    </aside>

    <!-- 右列：头部 + 消息区 + 输入区（输入区不占侧栏） -->
    <div class="right-col">
      <header class="task-header">
        <router-link to="/" class="back" aria-label="返回首页">
          <ArrowLeft class="icon" size="16" />
          返回
        </router-link>
        <h1>AI 超级智能体</h1>
        <span v-if="active" class="status-chip" :class="statusKey(active.status)">
          <span v-if="active.status === 'RUNNING'" class="pulse-dot"></span>
          {{ statusLabel(active.status) }}
        </span>
        <button
          class="history-toggle"
          :class="{ active: sidebarOpen }"
          @click="sidebarOpen = !sidebarOpen"
          aria-label="任务记录"
        >
          <ListTodo class="icon" size="18" />
        </button>
      </header>

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

        <!-- 任务视图（轮次对话流：用户问题 → 执行过程 → 任务报告，追问追加新一轮） -->
        <div v-else class="task-view">
          <template v-for="(round, ri) in rounds" :key="ri">
            <!-- 用户问题（灰底气泡，与 AI 报告区分） -->
            <div class="user-question">
              <div class="uq-bubble">{{ round.question }}</div>
            </div>

            <!-- 执行过程（透明折叠行；执行中常驻：无事件时显示"正在思考"等待动画） -->
            <div v-if="round.processItems.length || (round.plan && round.plan.length) || isRoundRunning(ri)" class="process-line">
              <button
                class="process-toggle"
                @click="toggleRound(ri)"
                :aria-expanded="!isRoundCollapsed(ri)"
              >
                <Brain class="icon" size="14" :class="{ 'icon-pulse': isRoundRunning(ri) }" />
                <span class="process-title">{{ roundTitle(ri) }}</span>
                <span v-if="roundThinking(ri)" class="thinking-dots" aria-label="思考中"><i></i><i></i><i></i></span>
                <ChevronDown class="chev" :class="{ expanded: !isRoundCollapsed(ri) }" size="14" />
              </button>
              <div v-if="!isRoundCollapsed(ri)" class="process-detail">
                <div v-if="!round.processItems.length && !(round.plan && round.plan.length)" class="process-loading">正在思考中，请稍候…</div>
                <!-- 计划进度面板 -->
                <section v-if="round.plan && round.plan.length" class="plan-panel" aria-label="任务计划">
                  <div class="section-head">
                    <ListChecks class="icon" size="16" />
                    <span>执行计划</span>
                  </div>
                  <div
                    v-for="step in round.plan"
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

                <!-- 该轮事件（工具调用与结果合并为一行，点击展开参数与完整结果） -->
                <div
                  v-for="(item, i) in round.processItems"
                  :key="i"
                  class="event-card"
                  :class="[item.kind === 'tool' ? 'tool' : item.ev.type, { 'sub-agent': eventAgent(item) !== 'main' }]"
                >
                  <!-- 工具调用合并行 -->
                  <template v-if="item.kind === 'tool'">
                    <div class="event-head" @click="item.result ? toggleExpand(ri, i) : null">
                      <span class="event-icon">
                        <Loader v-if="!item.result" size="14" class="spin" />
                        <Wrench v-else size="14" />
                      </span>
                      <span class="event-title">
                        {{ eventTitle(item.call) }}<span v-if="item.result" class="tool-brief">{{ toolBrief(item.result) }}</span>
                      </span>
                      <span class="event-time">{{ formatClock((item.result || item.call).timestamp) }}</span>
                      <ChevronRight v-if="item.result" class="chev" :class="{ expanded: expandedSet.has(ri + '-' + i) }" size="14" />
                    </div>
                    <div v-if="item.result && expandedSet.has(ri + '-' + i)" class="event-body">
                      <code v-if="item.call.toolArgs" class="tool-args">{{ formatArgs(item.call.toolArgs) }}</code>
                      <pre v-if="item.result.toolResult" class="tool-result">{{ item.result.toolResult }}</pre>
                    </div>
                  </template>
                  <!-- 普通事件行（思考/计划/错误/未配对的工具事件） -->
                  <template v-else>
                    <div class="event-head" @click="item.ev.type === 'tool_result' ? toggleExpand(ri, i) : null">
                      <span class="event-icon">
                        <Brain v-if="item.ev.type === 'think'" size="14" />
                        <Wrench v-else-if="item.ev.type === 'tool_call'" size="14" />
                        <ChevronRight v-else-if="item.ev.type === 'tool_result'" size="14" :class="{ expanded: expandedSet.has(ri + '-' + i) }" />
                        <ListChecks v-else-if="item.ev.type === 'plan_updated'" size="14" />
                        <CircleAlert v-else-if="item.ev.type === 'error'" size="14" />
                        <FileText v-else size="14" />
                      </span>
                      <span class="event-title">{{ eventTitle(item.ev) }}</span>
                      <span class="event-time">{{ formatClock(item.ev.timestamp) }}</span>
                    </div>
                    <div
                      v-if="item.ev.type === 'think' && item.ev.content"
                      class="event-body markdown-body"
                      v-html="renderMarkdown(item.ev.content)"
                    ></div>
                    <div v-if="item.ev.type === 'tool_call' && item.ev.toolArgs" class="event-body">
                      <code class="tool-args">{{ formatArgs(item.ev.toolArgs) }}</code>
                    </div>
                    <div v-if="item.ev.type === 'tool_result' && expandedSet.has(ri + '-' + i) && item.ev.toolResult" class="event-body">
                      <pre class="tool-result">{{ item.ev.toolResult }}</pre>
                    </div>
                    <div v-if="item.ev.type === 'plan_updated'" class="event-body compact">
                      {{ planSummary(item.ev.steps) }}
                    </div>
                  </template>
              </div>
            </div>
              </div>

            <!-- 该轮任务报告 -->
            <section v-if="round.report" class="report-panel" aria-label="任务报告">
              <div class="section-head">
                <FileText class="icon" size="16" />
                <span>任务报告</span>
                <button class="copy-report-btn" @click="copyReport(round, ri)" aria-label="复制报告">
                  <CircleCheck v-if="copiedRound === ri" class="icon copied" size="14" />
                  <Copy v-else class="icon" size="14" />
                  <span>{{ copiedRound === ri ? '已复制' : '复制' }}</span>
                </button>
              </div>
              <div class="report-body markdown-body" v-html="renderMarkdown(round.report)"></div>
            </section>

            <!-- 该轮交付物（紧跟报告，预览/下载即点即用；图标按文件类型区分） -->
            <div v-for="d in round.deliverables" :key="d.index" class="deliverable-chip deliverable-inline">
              <component :is="deliverableIcon(d.type)" class="icon" size="14" />
              <span class="deliverable-name">{{ d.name }}</span>
              <span class="deliverable-type">{{ deliverableTypeLabel(d.type) }}</span>
              <button class="deliverable-btn" @click.stop="previewDeliverable(d)" aria-label="预览">预览</button>
              <button class="deliverable-btn" @click.stop="downloadDeliverable(d)" aria-label="下载">下载</button>
            </div>
          </template>
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

      <!-- 输入区（发送按钮与文本域垂直居中；运行/排队期间变红色「停止」） -->
      <div class="input-area">
        <textarea
          v-model="inputText"
          :placeholder="followUpMode ? '继续追问或下达新指令…' : '描述你的任务，例如：搜索最新的大模型资讯，整理成一份摘要…'"
          rows="1"
          :disabled="creating"
          @keydown.enter.exact.prevent="onEnterKey"
        ></textarea>
        <button
          v-if="running"
          class="send-btn stop"
          :disabled="stopping"
          @click="stopTask"
          aria-label="停止任务"
        >
          <span class="btn-inner">
            <Loader v-if="stopping" class="icon spin" size="18" />
            <Square v-else class="icon" size="15" />
            <span class="btn-text">{{ stopping ? '停止中' : '停止' }}</span>
          </span>
        </button>
        <button
          v-else
          class="send-btn"
          :disabled="creating || !inputText.trim()"
          @click="send"
          :aria-label="followUpMode ? '发送追问' : '开始执行任务'"
        >
          <span v-if="!creating" class="btn-inner">
            <ArrowRight class="icon" size="18" />
            <span class="btn-text">{{ followUpMode ? '发送' : '开始任务' }}</span>
          </span>
          <span v-else class="btn-inner">
            <Loader class="icon spin" size="18" />
            <span class="btn-text">创建中</span>
          </span>
        </button>
      </div>
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

    <!-- 历史项三点菜单（Teleport 到 body：fixed 定位，脱离任务列表滚动容器，不被裁剪） -->
    <Teleport to="body">
      <div v-if="menuTask" class="history-menu-fixed" :style="menuStyle">
        <button class="history-menu-item" @click.stop="onBatchManage">
          <ListChecks class="menu-icon" size="18" />
          批量管理
        </button>
        <button class="history-menu-item" @click.stop="onEditTitle(menuTask)">
          <Pencil class="menu-icon" size="18" />
          修改标题
        </button>
        <button
          class="history-menu-item danger"
          :disabled="!isFinishedStatus(menuTask.status)"
          :title="isFinishedStatus(menuTask.status) ? '' : '执行中的任务须先停止再删除'"
          @click.stop="openDeleteConfirm(menuTask)"
        >
          <Trash2 class="menu-icon" size="18" />
          删除任务
        </button>
      </div>
    </Teleport>

    <!-- 删除任务确认弹窗 -->
    <Teleport to="body">
      <div v-if="confirmDeleteTask" class="modal-overlay" @click="confirmDeleteTask = null">
        <div class="modal-content" @click.stop>
          <div class="modal-title">删除任务</div>
          <div class="modal-desc">确定要删除任务「{{ confirmDeleteTask.title || '未命名任务' }}」吗？删除后无法恢复。</div>
          <div class="modal-actions">
            <button class="modal-btn cancel" @click="confirmDeleteTask = null">取消</button>
            <button class="modal-btn confirm" @click="doDeleteTask(confirmDeleteTask.id)">确认删除</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 修改标题弹窗 -->
    <Teleport to="body">
      <div v-if="editTitleVisible" class="modal-overlay" @click="closeEditTitle">
        <div class="modal-content" @click.stop>
          <div class="modal-title left">修改标题</div>
          <input
            v-model="editTitleText"
            class="edit-title-input modal-title-input"
            maxlength="40"
            @keyup.enter="confirmEditTitle"
            @keyup.escape="closeEditTitle"
          />
          <div class="modal-actions">
            <button class="modal-btn cancel" @click="closeEditTitle">取消</button>
            <button class="modal-btn confirm green" @click="confirmEditTitle">确定</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 批量删除确认弹窗 -->
    <Teleport to="body">
      <div v-if="confirmBatchDelete" class="modal-overlay" @click="confirmBatchDelete = false">
        <div class="modal-content" @click.stop>
          <div class="modal-title left">删除任务记录</div>
          <div class="modal-desc left">删除后内容将无法恢复，确认删除选中的 {{ selectedTaskIds.length }} 个任务？</div>
          <div class="modal-actions">
            <button class="modal-btn cancel" @click="confirmBatchDelete = false">取消</button>
            <button class="modal-btn confirm" @click="doBatchDelete">删除</button>
          </div>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, reactive, computed, inject, nextTick, onMounted, onUnmounted } from 'vue'
import {
  createManusTask,
  stopManusTask,
  fetchManusTaskList,
  fetchManusTask,
  streamManusTaskEvents,
  sendManusFollowUp,
  downloadManusDeliverable,
  fetchManusDeliverablePreview,
  renameManusTask,
  deleteManusTask,
  batchDeleteManusTasks,
} from '../api/request'
import { linkifyHtml } from '../utils/linkify'
import { previewImage, openPreview, closePreview } from '../utils/previewImage'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  ArrowLeft, ArrowRight, Square, X,
  Brain, Wrench, ListChecks, ListTodo, Plus,
  CircleCheck, CircleDashed, CircleAlert, ChevronDown, ChevronRight,
  Loader, FileText, Ban, MoreVertical, Check, Pencil, Trash2,
  Copy, Image as ImageIcon, File,
} from '@lucide/vue'

const showToast = inject('showToast', () => {})

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

// 轮次对话流：按 user_message 事件把事件日志切分为多轮
// 每轮 = 用户问题（原始任务或追问）+ 过程条目 + 该轮报告（轮内最后一个 final 事件）+ 该轮交付物（渲染在报告下方）
// 过程条目 processItems：普通事件为 { kind:'single', ev }；工具调用与结果合并为 { kind:'tool', call, result } 一行
const rounds = computed(() => {
  if (!active.value) return []
  const list = []
  let current = { question: active.value.task, processItems: [], plan: null, report: null, deliverables: [] }
  let deliverableSeq = 0
  for (const ev of (active.value.events || [])) {
    if (ev.type === 'user_message') {
      list.push(current)
      // 新一轮计划置空：仅展示该轮内 planCreate/planUpdate 产生的快照，避免误显示上一轮的旧计划
      current = { question: ev.content || '', processItems: [], plan: null, report: null, deliverables: [] }
    } else if (ev.type === 'final') {
      current.report = ev.content || current.report
    } else if (ev.type === 'deliverable' && ev.deliverable) {
      // 交付物独立收集，渲染在该轮报告下方（不进过程明细，避免折叠后看不到）
      deliverableSeq += 1
      current.deliverables.push({ ...ev.deliverable, index: deliverableSeq })
    } else if (ev.type === 'tool_result') {
      // 结果附加到最近的同名未配对工具调用上（降噪：call+result 合并为一行）
      const open = [...current.processItems].reverse().find(
        (item) => item.kind === 'tool' && item.call.toolName === ev.toolName && !item.result)
      if (open) {
        open.result = ev
      } else {
        current.processItems.push({ kind: 'single', ev })
      }
    } else if (ev.type === 'tool_call') {
      current.processItems.push({ kind: 'tool', call: ev, result: null })
    } else {
      if (ev.type === 'plan_updated' && ev.steps) current.plan = ev.steps
      current.processItems.push({ kind: 'single', ev })
    }
  }
  list.push(current)
  return list
})

// 每轮过程折叠状态：默认收起；最后一轮执行中自动展开；结束自动收起；可手动切换
const roundCollapsed = reactive({})
function isRoundRunning(ri) {
  return running.value && ri === rounds.value.length - 1
}
function isRoundCollapsed(ri) {
  if (roundCollapsed[ri] !== undefined) return roundCollapsed[ri]
  return !isRoundRunning(ri)
}
// 轮次标题：执行中且尚无任何事件时为"正在思考"，有事件后为"正在执行"，结束为"执行过程"
function roundThinking(ri) {
  const round = rounds.value[ri]
  return isRoundRunning(ri) && round && !round.processItems.length
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
  copiedRound.value = null
}

// 追问模式：当前任务已到终态，输入框变为追问输入
const followUpMode = computed(() =>
  active.value && ['COMPLETED', 'STOPPED', 'ERROR'].includes(active.value.status)
)

// 预览产生的 blob URL（卸载与超时后统一回收，陷阱 33）
const previewBlobUrls = ref([])
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

const AGENT_LABELS = {
  main: '主智能体',
  researcher: '联网研究员',
  knowledgeResearcher: '知识库研究员',
  writer: '撰写员',
}

function agentLabel(key) {
  return AGENT_LABELS[key] || key || ''
}

function eventTitle(ev) {
  const prefix = ev.agent && ev.agent !== 'main' ? `【${agentLabel(ev.agent)}】` : ''
  const base = (() => {
    switch (ev.type) {
      case 'think': return '思考'
      case 'tool_call': return `调用工具 · ${ev.toolName || '未知'}`
      case 'tool_result': return `工具返回 · ${ev.toolName || '未知'}`
      case 'plan_updated': return '计划已更新'
      case 'user_message': return '用户追问'
      case 'deliverable': return `交付物产出 · ${ev.deliverable?.name || ''}`
      case 'error': return '执行异常'
      case 'final': return '任务结束'
      default: return ev.type
    }
  })()
  return prefix + base
}

/** 合并行的事件来源角色（工具行取调用方，普通行取事件本身） */
function eventAgent(item) {
  const ev = item.kind === 'tool' ? item.call : item.ev
  return ev.agent || 'main'
}

/** 工具结果的一行摘要（合并行展示，详情折叠） */
function toolBrief(resultEv) {
  const text = (resultEv.toolResult || '').replace(/\s+/g, ' ').trim()
  if (!text) return ''
  return text.length > 60 ? text.slice(0, 60) + '…' : text
}

/** 交付物图标按文件类型区分 */
function deliverableIcon(type) {
  if (type === 'image') return ImageIcon
  if (type === 'pdf') return FileText
  return File
}

// 报告复制反馈状态（轮次序号 → 已复制）
const copiedRound = ref(null)

/** 兜底复制：Clipboard API 不可用/被拒时用隐藏文本域 execCommand */
function legacyCopy(text) {
  const ta = document.createElement('textarea')
  ta.value = text
  ta.style.position = 'fixed'
  ta.style.opacity = '0'
  document.body.appendChild(ta)
  ta.select()
  let ok = false
  try {
    ok = document.execCommand('copy')
  } catch (e) {
    ok = false
  }
  document.body.removeChild(ta)
  return ok
}

/** 复制报告 Markdown 原文到剪贴板 */
async function copyReport(round, ri) {
  const succeed = () => {
    copiedRound.value = ri
    showToast('报告已复制', 'success')
    setTimeout(() => {
      if (copiedRound.value === ri) copiedRound.value = null
    }, 2000)
  }
  try {
    if (navigator.clipboard && window.isSecureContext) {
      await navigator.clipboard.writeText(round.report || '')
    } else if (!legacyCopy(round.report || '')) {
      throw new Error('copy failed')
    }
    succeed()
  } catch (e) {
    if (legacyCopy(round.report || '')) {
      succeed()
    } else {
      showToast('复制失败，请手动选择复制', 'error')
    }
  }
}

function deliverableTypeLabel(type) {
  return { pdf: 'PDF', image: '图片', text: '文本', binary: '文件' }[type] || type
}

function formatSize(bytes) {
  if (bytes == null) return ''
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`
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

function toggleExpand(ri, i) {
  const key = ri + '-' + i
  if (expandedSet.has(key)) {
    expandedSet.delete(key)
  } else {
    expandedSet.add(key)
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

// ===== 任务管理：重命名 / 删除 / 批量删除 =====
const batchMode = ref(false)
const selectedTaskIds = ref([])
const menuTask = ref(null)
const menuStyle = ref({ top: '0px', left: '0px' })
const confirmDeleteTask = ref(null)
const confirmBatchDelete = ref(false)
const editTitleVisible = ref(false)
const editTitleText = ref('')
let editTitleTargetId = ''

/** 任务是否处于可删除的终态（执行中/排队中须先停止，后端同样校验） */
function isFinishedStatus(status) {
  return ['COMPLETED', 'STOPPED', 'ERROR'].includes(status)
}

/** 历史项点击：批量模式下仅终态任务可勾选（执行中/排队中的任务后端禁删），普通模式打开详情 */
function onItemClick(item) {
  if (batchMode.value) {
    if (isFinishedStatus(item.status)) {
      toggleSelectTask(item.id)
    }
    return
  }
  openDetail(item.id)
}

/** 三点菜单：打开/关闭（菜单 Teleport 到 body，用视口坐标计算 fixed 位置；下方空间不足自动向上展开） */
function toggleMenu(item, event) {
  const opening = menuTask.value?.id !== item.id
  menuTask.value = opening ? item : null
  if (!opening) return
  // 事件分发结束前先捕获定位锚点，nextTick 回调里 currentTarget 已可能为 null
  const itemEl = event?.currentTarget?.closest?.('.history-item')
  nextTick(() => {
    const menuEl = document.querySelector('.history-menu-fixed')
    const rect = itemEl?.getBoundingClientRect()
    if (!rect || !menuEl) return
    const menuHeight = menuEl.offsetHeight
    const gap = 8
    const spaceBelow = window.innerHeight - rect.bottom
    const spaceAbove = rect.top
    const up = spaceBelow - gap < menuHeight && spaceAbove - gap >= menuHeight
    const top = up ? rect.top - gap - menuHeight : rect.bottom + gap
    const left = Math.max(8, rect.right - gap - 160)
    menuStyle.value = { top: top + 'px', left: left + 'px' }
  })
}

/** 点击菜单外的空白处关闭三点菜单 */
function handleMenuOutsideClick() {
  menuTask.value = null
}

/** 三点菜单：进入批量管理模式 */
function onBatchManage() {
  menuTask.value = null
  batchMode.value = true
  selectedTaskIds.value = []
}

/** 三点菜单：打开修改标题弹窗（预填当前标题） */
function onEditTitle(item) {
  menuTask.value = null
  editTitleTargetId = item.id
  editTitleText.value = item.title || ''
  editTitleVisible.value = true
}

function closeEditTitle() {
  editTitleVisible.value = false
}

/** 确定修改标题：校验非空后调用接口，成功刷新列表 */
async function confirmEditTitle() {
  const title = editTitleText.value.trim()
  if (!title) {
    showToast('标题不能为空', 'error')
    return
  }
  try {
    await renameManusTask(editTitleTargetId, title)
    editTitleVisible.value = false
    loadHistory()
    showToast('标题已更新', 'success')
  } catch (e) {
    showToast(e?.response?.data?.message || '标题更新失败，请重试', 'error')
  }
}

/** 三点菜单：打开单体删除确认弹窗 */
function openDeleteConfirm(item) {
  menuTask.value = null
  confirmDeleteTask.value = item
}

/** 批量模式：选中/取消选中任务 */
function toggleSelectTask(taskId) {
  const idx = selectedTaskIds.value.indexOf(taskId)
  if (idx >= 0) {
    selectedTaskIds.value.splice(idx, 1)
  } else {
    selectedTaskIds.value.push(taskId)
  }
}

/** 批量模式：退出并清空选中 */
function exitBatchMode() {
  batchMode.value = false
  selectedTaskIds.value = []
  confirmBatchDelete.value = false
}

/** 删除任务：当前正在查看的任务被删时清空主区回新建态 */
async function doDeleteTask(taskId) {
  try {
    await deleteManusTask(taskId)
    confirmDeleteTask.value = null
    if (active.value && active.value.id === taskId) {
      startNewTask()
    }
    loadHistory()
    showToast('任务已删除', 'success')
  } catch (e) {
    confirmDeleteTask.value = null
    showToast(e?.response?.data?.message || '删除失败，请重试', 'error')
  }
}

/** 批量删除确认后执行：调用接口，成功后刷新列表（当前任务被删则回新建态） */
async function doBatchDelete() {
  const ids = [...selectedTaskIds.value]
  if (!ids.length) return
  try {
    const res = await batchDeleteManusTasks(ids)
    confirmBatchDelete.value = false
    exitBatchMode()
    if (active.value && ids.includes(active.value.id)) {
      startNewTask()
    }
    loadHistory()
    showToast(res.data?.message || '批量删除成功', 'success')
  } catch (e) {
    // 失败仅关闭确认弹窗、保留勾选，用户可直接重试而不必重选
    confirmBatchDelete.value = false
    showToast(e?.response?.data?.message || '批量删除失败，请重试', 'error')
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
    // 结果就绪：折叠最后一轮的过程，报告呈现
    roundCollapsed[rounds.value.length - 1] = true
    // final 事件不携带终态：拉取详情刷新真实状态、报告与交付物
    fetchManusTask(active.value.id)
      .then((res) => {
        if (active.value && active.value.id === res.data.id) {
          active.value.status = res.data.status
          active.value.finalReport = res.data.finalReport || active.value.finalReport
          active.value.deliverables = withIndex(res.data.deliverables)
        }
      })
      .catch(() => {})
    loadHistory()
  } else if (ev.type === 'deliverable' && ev.deliverable) {
    if (!active.value.deliverables) active.value.deliverables = []
    active.value.deliverables.push({ ...ev.deliverable, index: active.value.deliverables.length + 1 })
    active.value.events.push(ev)
  } else {
    active.value.events.push(ev)
  }
  scrollToBottom()
}

function send() {
  const text = inputText.value.trim()
  if (!text || creating.value) return
  // 追问模式：对已结束的当前任务追加消息，时间线续接
  if (followUpMode.value) {
    submitFollowUp(text)
    return
  }
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
        deliverables: [],
      }
      running.value = true
      stopping.value = false
      sidebarOpen.value = false
      resetRoundState()
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

/** 提交追问：任务回到执行态，输入清空，续接事件流 */
function submitFollowUp(text) {
  creating.value = true
  streamError.value = ''
  expandedSet.clear()
  sendManusFollowUp(active.value.id, text)
    .then((res) => {
      inputText.value = ''
      active.value.status = res.data.status
      running.value = true
      stopping.value = false
      // 展开即将到来的新一轮（下标=当前轮数；此刻追问事件尚未到达，不能按最后一轮取，否则会误展开上一轮）
      roundCollapsed[rounds.value.length] = false
      scrollToBottom()
      // 从当前已展示的事件之后增量订阅，避免整段重复回放
      subscribeEvents(active.value.id, active.value.events.length)
      loadHistory()
    })
    .catch((e) => {
      streamError.value = e?.response?.data?.message || '追问提交失败，请稍后重试'
    })
    .finally(() => {
      creating.value = false
    })
}

function subscribeEvents(taskId, after = 0) {
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
  }, controller.signal, after)
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
    resetRoundState()
    active.value = {
      id: res.data.id,
      task: res.data.task,
      status: res.data.status,
      plan: res.data.plan || [],
      events: res.data.events || [],
      finalReport: res.data.finalReport || '',
      deliverables: withIndex(res.data.deliverables),
    }
    sidebarOpen.value = false
    // 未到终态的任务接续实时事件流（增量：从快照已有事件之后开始，避免重复）
    if (res.data.status === 'PENDING' || res.data.status === 'RUNNING') {
      running.value = true
      resetRoundState()
      subscribeEvents(taskId, active.value.events.length)
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
  resetRoundState()
  sidebarOpen.value = false
}

/** 任务主区点击：图片放大预览（事件委托） */
function handleMainClick(e) {
  const img = e.target?.closest?.('img.chat-image')
  if (img) {
    openPreview(img.getAttribute('src') || '', img.getAttribute('alt') || '')
  }
}

/** 预览交付物：图片走预览浮层，PDF/文本新标签页打开 */
async function previewDeliverable(deliverable) {
  if (!active.value || stopping.value) return
  try {
    const { url } = await fetchManusDeliverablePreview(active.value.id, deliverable.index)
    if (deliverable.type === 'image') {
      openPreview(url, deliverable.name)
    } else {
      window.open(url, '_blank')
    }
    scheduleRevoke(url)
  } catch (e) {
    streamError.value = e?.response?.data?.message || '预览失败，请稍后重试'
  }
}

/** 下载交付物 */
async function downloadDeliverable(deliverable) {
  if (!active.value) return
  try {
    await downloadManusDeliverable(active.value.id, deliverable.index, deliverable.name)
  } catch (e) {
    streamError.value = e?.response?.data?.message || '下载失败，请稍后重试'
  }
}

/** 给交付物标注序号（1 开始，与登记顺序一致，供预览/下载接口使用） */
function withIndex(deliverables) {
  return (deliverables || []).map((d, i) => ({ ...d, index: i + 1 }))
}

/** 延迟回收预览 blob URL（给新标签页留出加载时间） */
function scheduleRevoke(url) {
  previewBlobUrls.value.push(url)
  setTimeout(() => {
    URL.revokeObjectURL(url)
    previewBlobUrls.value = previewBlobUrls.value.filter((u) => u !== url)
  }, 5 * 60 * 1000)
}

function handleKeydown(e) {
  if (e.key !== 'Escape') return
  if (previewImage.value.show) {
    closePreview()
  }
  // 弹窗 Escape 关闭（与修改标题弹窗的 @keyup.escape 一致）
  if (confirmDeleteTask.value) confirmDeleteTask.value = null
  if (confirmBatchDelete.value) confirmBatchDelete.value = false
  if (editTitleVisible.value) editTitleVisible.value = false
}

/** Enter 键处理：忽略中文输入法选词回车（isComposing），防止选词时误创建任务 */
function onEnterKey(e) {
  if (e.isComposing || e.keyCode === 229) return
  send()
}

onMounted(() => {
  loadHistory()
  document.addEventListener('keydown', handleKeydown)
  document.addEventListener('click', handleMenuOutsideClick)
  mainRef.value?.addEventListener('click', handleMainClick)
})

onUnmounted(() => {
  abortController.value?.abort()
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('click', handleMenuOutsideClick)
  mainRef.value?.removeEventListener('click', handleMainClick)
  // 回收预览 blob URL（陷阱 33）
  previewBlobUrls.value.forEach((url) => URL.revokeObjectURL(url))
  previewBlobUrls.value = []
})
</script>

<style scoped>
.task-page {
  height: 100%;
  display: flex;
  flex-direction: row;
  background: var(--bg-primary, #f5f7fb);
  color: var(--text-primary, #1e293b);
  overflow: hidden;
}

/* ===== 右列（头部 + 消息区 + 输入区） ===== */
.right-col {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  position: relative;
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
  margin: 0;
  font-size: 1.05rem;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
}
.back {
  /* 与普通智能体页返回按钮同款（浅绿底圆角矩形） */
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
  background: rgba(16, 185, 129, 0.08);
  border: 1px solid rgba(255, 255, 255, 0.3);
  color: #10b981;
  height: 40px;
  padding: 0 16px;
  box-sizing: border-box;
  border-radius: 12px;
  cursor: pointer;
  font-size: 0.85rem;
  font-weight: 500;
  text-decoration: none;
  transition: background 0.2s, color 0.2s, box-shadow 0.2s;
}
.back:hover {
  background: rgba(16, 185, 129, 0.15);
  color: #059669;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.1);
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
.task-main {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 1.25rem 1.5rem 2rem;
  scroll-behavior: smooth;
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
  /* 与普通智能体页新对话按钮同款（绿色渐变实心） */
  display: flex;
  align-items: center;
  gap: 6px;
  background: linear-gradient(135deg, #34d399, #059669);
  color: #fff;
  border: none;
  padding: 7px 14px;
  border-radius: 10px;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
  box-shadow: 0 4px 12px rgba(5, 150, 105, 0.3);
}
.new-task-btn:hover {
  opacity: 0.95;
  box-shadow: 0 6px 20px rgba(5, 150, 105, 0.4);
  transform: translateY(-1px);
}
.new-task-btn:active {
  transform: translateY(0);
  box-shadow: 0 2px 8px rgba(5, 150, 105, 0.3);
}
.task-list {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 2px 1rem 0.6rem;
}
.task-list-empty {
  padding: 1.5rem 0.5rem;
  text-align: center;
  font-size: 0.82rem;
  color: var(--text-tertiary, #94a3b8);
}
.history-item {
  position: relative;
  margin-bottom: 0.4rem;
  border: 1px solid transparent;
  border-radius: 12px;
  background: transparent;
  transition: background 200ms ease, border-color 200ms ease;
}
.history-item:hover {
  background: rgba(16, 185, 129, 0.07);
}
.history-item.current {
  background: rgba(16, 185, 129, 0.12);
  border-color: rgba(16, 185, 129, 0.35);
}
.history-item-main {
  cursor: pointer;
  padding: 0.6rem 44px 0.6rem 0.75rem;
  min-width: 0;
}
.history-item-title {
  display: block;
  font-size: 0.85rem;
  font-weight: 600;
  color: var(--text-primary, #1e293b);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
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

/* ===== 历史项三点按钮与悬浮菜单（对齐知识问答页交互） ===== */
.history-more-wrap {
  position: absolute;
  right: 2px;
  top: 50%;
  transform: translateY(-50%);
}
.history-more-btn {
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #94a3b8;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 200ms ease, color 200ms ease;
}
.history-more-btn:hover {
  background: rgba(16, 185, 129, 0.1);
  color: #10b981;
}
/* 三点菜单（Teleport 到 body，fixed 定位——脱离任务列表滚动容器，不被任何元素裁剪/遮挡） */
.history-menu-fixed {
  position: fixed;
  width: 160px;
  background: rgba(255, 255, 255, 0.97);
  border-radius: 12px;
  border: 1px solid rgba(16, 185, 129, 0.1);
  box-shadow: 0 12px 32px rgba(15, 23, 42, 0.18);
  z-index: 3000;
  overflow: hidden;
  animation: menuFadeIn 0.15s ease;
}
@keyframes menuFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.history-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  padding: 12px 14px;
  border: none;
  background: none;
  font-size: 0.9rem;
  color: #1e293b;
  cursor: pointer;
  transition: background 150ms ease;
  text-align: left;
}
.history-menu-item:hover {
  background: #f8fafc;
}
.history-menu-item.danger {
  color: #ef4444;
}
.history-menu-item.danger:hover {
  background: rgba(239, 68, 68, 0.06);
}
.history-menu-item.danger:disabled {
  color: #fca5a5;
  cursor: not-allowed;
}
.history-menu-item.danger:disabled:hover {
  background: none;
}
.menu-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

/* ===== 批量管理模式：勾选圆圈与底部按钮条 ===== */
.history-item.batch-mode {
  cursor: pointer;
}
.batch-check-wrap {
  position: absolute;
  right: 6px;
  top: 0;
  bottom: 0;
  width: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
}
.batch-check-wrap.disabled {
  cursor: not-allowed;
}
.batch-circle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid #cbd5e1;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 150ms ease;
  box-sizing: border-box;
}
.batch-circle.selected {
  background: #10b981;
  border-color: #10b981;
}
.batch-circle.disabled {
  border-color: #e2e8f0;
  cursor: not-allowed;
}
.batch-circle .icon {
  width: 14px;
  height: 14px;
  color: #ffffff;
}
.batch-bar {
  flex-shrink: 0;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px;
  background: rgba(245, 247, 250, 0.75);
  border-top: 1px solid rgba(16, 185, 129, 0.08);
}
.batch-bar-btn {
  flex: 1;
  height: 44px;
  border-radius: 12px;
  border: none;
  background: #f8fafc;
  color: #1e293b;
  font-size: 1rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 200ms ease;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}
.batch-bar-btn:hover {
  background: #f1f5f9;
}
.batch-bar-btn.delete {
  color: #ef4444;
}
.batch-bar-btn.delete:hover:not(:disabled) {
  background: rgba(239, 68, 68, 0.08);
}
.batch-bar-btn.delete:disabled {
  color: #d1d5db;
  cursor: not-allowed;
  background: #f8fafc;
}

/* ===== 弹窗（删除确认 / 修改标题 / 批量删除确认，玻璃拟态） ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  background: rgba(16, 185, 129, 0.08);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  animation: menuFadeIn 0.2s ease;
}
.modal-content {
  width: 340px;
  max-width: calc(100vw - 2rem);
  padding: 2rem;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.3);
  box-shadow: 0 16px 48px rgba(16, 185, 129, 0.12);
  text-align: center;
  animation: modalSlideUp 0.25s ease;
}
@keyframes modalSlideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
.modal-title {
  font-size: 1.1rem;
  font-weight: 600;
  color: #1e1b4b;
  margin-bottom: 0.5rem;
}
.modal-desc {
  font-size: 0.9rem;
  color: #64748b;
  margin-bottom: 1.5rem;
  line-height: 1.5;
  word-break: break-word;
}
.modal-actions {
  display: flex;
  gap: 0.75rem;
}
.modal-btn {
  flex: 1;
  padding: 0.6rem;
  border-radius: 10px;
  border: none;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 200ms ease;
}
.modal-btn.cancel {
  background: #f1f5f9;
  color: #64748b;
}
.modal-btn.cancel:hover {
  background: #e2e8f0;
  color: #1e293b;
}
.modal-btn.confirm {
  background: #ef4444;
  color: #fff;
  box-shadow: 0 4px 12px rgba(239, 68, 68, 0.3);
}
.modal-btn.confirm:hover {
  background: #dc2626;
  box-shadow: 0 6px 20px rgba(239, 68, 68, 0.4);
  transform: translateY(-1px);
}
/* 标题/正文左对齐（修改标题、批量删除确认弹窗使用） */
.modal-title.left,
.modal-desc.left {
  text-align: left;
}
/* 修改标题弹窗的确定按钮：绿色主色 */
.modal-btn.confirm.green {
  background: linear-gradient(135deg, #34d399, #059669);
  box-shadow: 0 4px 12px rgba(5, 150, 105, 0.3);
}
.modal-btn.confirm.green:hover {
  background: linear-gradient(135deg, #10b981, #047857);
  box-shadow: 0 6px 20px rgba(5, 150, 105, 0.4);
  transform: translateY(-1px);
}
/* 修改标题弹窗输入框 */
.edit-title-input {
  width: 100%;
  box-sizing: border-box;
  padding: 10px 12px;
  border: 1px solid #e2e8f0;
  border-radius: 10px;
  font-size: 1rem;
  background: #f8fafc;
  color: #1e1b4b;
  outline: none;
  margin-bottom: 1.25rem;
  transition: border-color 200ms ease, box-shadow 200ms ease;
}
.edit-title-input:focus {
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

/* ===== 空状态 ===== */
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

/* 任务视图（轮次对话流） */
.task-view {
  max-width: 860px;
  margin: 0 auto;
}

/* 用户问题（灰底气泡，与 AI 回复区分） */
.user-question {
  margin: 1.6rem 0 0.6rem;
  display: flex;
  justify-content: flex-end;
}
.user-question:first-child {
  margin-top: 0;
}
.uq-bubble {
  max-width: 85%;
  padding: 0.65rem 1rem;
  border-radius: 14px 14px 4px 14px;
  background: #eceff4;
  color: var(--text-primary, #1e293b);
  font-size: 0.92rem;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}

/* 执行过程折叠行（透明、无轮廓、不占整行） */
.process-line {
  margin: 0.4rem 0;
}
.process-toggle {
  display: inline-flex;
  align-items: center;
  gap: 0.4rem;
  min-height: 32px;
  padding: 0.15rem 0.5rem;
  margin-left: -0.5rem;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  border-radius: 10px;
  transition: background 200ms ease;
}
.process-toggle:hover {
  background: rgba(16, 185, 129, 0.07);
}
.process-toggle:focus-visible {
  outline: 2px solid #10b981;
  outline-offset: 2px;
}
.process-toggle .icon {
  color: #10b981;
}
.process-toggle .icon-pulse {
  animation: pulse 1.2s ease-in-out infinite;
}
.process-title {
  font-size: 0.82rem;
  font-weight: 600;
  color: var(--text-secondary, #475569);
}
.process-toggle .chev {
  color: var(--text-tertiary, #94a3b8);
  transition: transform 200ms ease;
}
.process-toggle .chev.expanded {
  transform: rotate(180deg);
}
.process-toggle .chev.expanded {
  transform: rotate(180deg);
}
.process-detail {
  padding: 0.3rem 0 0.4rem;
}
.process-loading {
  display: flex;
  align-items: center;
  gap: 0.4rem;
  padding: 0.4rem 0.6rem;
  font-size: 0.8rem;
  color: var(--text-tertiary, #94a3b8);
}
.thinking-dots {
  display: inline-flex;
  gap: 3px;
  align-items: center;
  margin-left: 0.1rem;
}
.thinking-dots i {
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background: #10b981;
  animation: dotBounce 1.2s ease-in-out infinite;
}
.thinking-dots i:nth-child(2) { animation-delay: 0.2s; }
.thinking-dots i:nth-child(3) { animation-delay: 0.4s; }
@keyframes dotBounce {
  0%, 60%, 100% { opacity: 0.3; transform: translateY(0); }
  30% { opacity: 1; transform: translateY(-3px); }
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

/* 时间线（扁平轻量：减淡底色与边框，弱化"彩色卡片"感） */
.event-card {
  margin-bottom: 0.5rem;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.45);
  border: 1px solid rgba(226, 232, 240, 0.7);
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
/* 子智能体事件：中性左边框 + 角色名文字标签区分来源（不再用彩色） */
.event-card.sub-agent {
  border-left: 3px solid #94a3b8;
}
.event-card.final {
  border-color: rgba(16, 185, 129, 0.3);
}
.event-head {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  padding: 0.55rem 0.85rem;
}
.event-card.tool .event-head,
.event-card.tool_result .event-head {
  cursor: pointer;
}
.event-icon {
  display: inline-flex;
  color: var(--text-tertiary, #94a3b8);
  flex-shrink: 0;
}
.event-card.think .event-icon { color: #64748b; }
.event-card.tool .event-icon,
.event-card.tool_call .event-icon,
.event-card.tool_result .event-icon { color: #64748b; }
.event-card.error .event-icon { color: #ef4444; }
.event-title {
  flex: 1;
  min-width: 0;
  font-size: 0.82rem;
  font-weight: 700;
  color: var(--text-primary, #1e293b);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
/* 工具合并行内的一行结果摘要 */
.tool-brief {
  margin-left: 0.5rem;
  font-weight: 400;
  font-size: 0.78rem;
  color: var(--text-tertiary, #94a3b8);
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

/* 交付物 */
.deliverable-chip {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  padding: 0.5rem 0.7rem;
  border-radius: 10px;
  background: rgba(16, 185, 129, 0.07);
  border: 1px solid rgba(16, 185, 129, 0.25);
}
.deliverable-inline {
  margin: 0.6rem 0;
}
.deliverable-name {
  font-weight: 700;
  color: var(--text-primary, #1e293b);
  word-break: break-all;
}
.deliverable-type {
  font-size: 0.7rem;
  padding: 0.1rem 0.5rem;
  border-radius: 999px;
  background: rgba(100, 116, 139, 0.1);
  color: #475569;
  flex-shrink: 0;
}
.deliverable-size {
  font-size: 0.72rem;
  color: var(--text-tertiary, #94a3b8);
  flex-shrink: 0;
}
.deliverable-btn {
  min-height: 28px;
  padding: 0 0.65rem;
  border: 1px solid rgba(16, 185, 129, 0.4);
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.8);
  color: #059669;
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 200ms ease;
}
.deliverable-btn:hover {
  background: rgba(16, 185, 129, 0.15);
}
.deliverable-btn:focus-visible {
  outline: 2px solid #10b981;
  outline-offset: 2px;
}
.deliverable-note {
  margin: 0.4rem 0 0;
  font-size: 0.78rem;
  color: var(--text-tertiary, #94a3b8);
  word-break: break-word;
}
.deliverables-panel .deliverable-row {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  flex-wrap: wrap;
  padding: 0.5rem 0;
  border-bottom: 1px solid rgba(226, 232, 240, 0.6);
  font-size: 0.85rem;
}
.deliverables-panel .deliverable-row:last-child {
  border-bottom: none;
}
.deliverables-panel .icon {
  color: #10b981;
  flex-shrink: 0;
}

/* 报告（玻璃拟态减淡：更透的底、更轻的描边） */
.report-panel {
  padding: 0.9rem 1rem;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(16, 185, 129, 0.16);
  box-shadow: var(--shadow-sm, 0 1px 3px rgba(15, 23, 42, 0.06));
}
.report-body {
  font-size: 0.9rem;
  line-height: 1.7;
}
/* 报告头部复制按钮（标题右侧轻量文字钮） */
.copy-report-btn {
  margin-left: auto;
  display: inline-flex;
  align-items: center;
  gap: 0.25rem;
  min-width: 44px;
  min-height: 44px;
  justify-content: center;
  padding: 0 0.6rem;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--text-tertiary, #94a3b8);
  font-size: 0.75rem;
  font-weight: 600;
  cursor: pointer;
  transition: background 200ms ease, color 200ms ease;
}
.copy-report-btn:hover {
  background: rgba(16, 185, 129, 0.08);
  color: #059669;
}
.copy-report-btn .icon.copied {
  color: #10b981;
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

/* ===== 输入区（按钮与文本域垂直居中） ===== */
.input-area {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 0.75rem;
  /* 容器留白与普通智能体一致（0.8rem 1rem） */
  padding: 0.8rem 1rem calc(0.8rem + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-top: 1px solid rgba(255, 255, 255, 0.4);
}
.input-area textarea {
  flex: 1;
  min-width: 0;
  min-height: 56px;
  max-height: 56px;
  padding: 0 1rem;
  border: 1px solid var(--border-light, #e2e8f0);
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.85);
  color: var(--text-primary, #1e293b);
  font-size: 0.9rem;
  font-family: inherit;
  /* 56px 高含 2px 边框，行高 54px 让文字垂直居中且不溢出出现滚动条 */
  line-height: 54px;
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
/* 运行/排队期间：发送按钮变红色「停止」（对齐知识问答页"发送变终止"惯例） */
.send-btn.stop {
  background: linear-gradient(135deg, #ef4444, #f87171);
}
.send-btn.stop:hover:not(:disabled) {
  background: linear-gradient(135deg, #dc2626, #ef4444);
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
    top: 0;
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
    inset: 0;
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
