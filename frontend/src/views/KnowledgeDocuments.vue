<template>
  <div class="doc-page">
    <div class="doc-shell">
      <!-- 头部：返回 + 标题 + 上传 -->
      <header class="page-header">
        <button type="button" class="back-link" @click="goBack">
          <ArrowLeft class="icon" size="16" />
          返回
        </button>
        <div class="header-texts">
          <h1 class="page-title">知识库管理</h1>
          <p class="page-subtitle">管理知识库文档，查看处理状态与切片结果</p>
        </div>
        <button type="button" class="upload-btn" @click="openUpload">
          <Upload class="icon" size="18" />
          上传知识库
        </button>
      </header>

      <!-- 工具条：搜索 + 排序 + 批量 -->
      <section class="toolbar">
        <div class="search-wrap">
          <Search class="search-icon" size="18" />
          <input
            v-model="keyword"
            class="search-input"
            type="text"
            placeholder="搜索知识库名称..."
            aria-label="搜索知识库名称"
          />
        </div>
        <div ref="sortWrapRef" class="sort-wrap">
          <button
            type="button"
            class="sort-trigger"
            :class="{ open: sortOpen }"
            aria-haspopup="listbox"
            aria-controls="knowledge-sort-menu"
            :aria-expanded="sortOpen ? 'true' : 'false'"
            aria-label="排序方式"
            @click.stop="toggleSortMenu"
            @keydown="handleSortKeydown"
          >
            <span class="sort-value">{{ sortLabel }}</span>
            <ChevronDown class="sort-caret" size="16" />
          </button>
        </div>
        <button type="button" class="ghost-btn" :class="{ active: batchMode }" @click="toggleBatchMode">
          <ListChecks class="icon" size="16" />
          {{ batchMode ? '退出批量' : '批量管理' }}
        </button>
      </section>

      <!-- 排序面板：Teleport 到 body + fixed 定位，避免被滚动容器裁剪 -->
      <Teleport to="body">
        <Transition name="sort-menu">
          <div
            v-if="sortOpen"
            id="knowledge-sort-menu"
            ref="sortMenuRef"
            class="sort-menu-fixed"
            :style="sortMenuStyle"
            role="listbox"
            aria-label="排序方式"
            :aria-activedescendant="`knowledge-sort-option-${activeSortIndex}`"
            @click.stop
          >
            <button
              v-for="(option, index) in SORT_OPTIONS"
              :id="`knowledge-sort-option-${index}`"
              :key="option.value"
              type="button"
              class="sort-option"
              :class="{ active: sort === option.value, highlight: activeSortIndex === index }"
              role="option"
              tabindex="-1"
              :aria-selected="sort === option.value ? 'true' : 'false'"
              @click.stop="selectSort(option.value)"
            >
              <span class="sort-option-label">{{ option.label }}</span>
              <Check v-if="sort === option.value" class="sort-option-check" size="16" />
            </button>
          </div>
        </Transition>
      </Teleport>

      <!-- 文档列表 -->
      <section class="table-card">
        <div class="table-head" :class="{ batch: batchMode }">
          <span v-if="batchMode" class="col-check">
            <button
              type="button"
              class="check-circle"
              :class="{ selected: allSelected }"
              :aria-label="allSelected ? '取消全选' : '全选当前页'"
              @click="toggleSelectAll"
            >
              <Check v-if="allSelected" class="icon" size="13" />
            </button>
          </span>
          <span class="col-name">名称</span>
          <span class="col-size">大小</span>
          <span class="col-status">状态</span>
          <span class="col-time">上传时间</span>
          <span class="col-actions">操作</span>
        </div>

        <div v-if="loading && items.length === 0" class="state-block">
          <span class="spinner" aria-hidden="true"></span>
          <span class="state-text">正在加载知识库文档...</span>
        </div>

        <div v-else-if="items.length === 0" class="state-block">
          <FileText class="state-icon" size="32" />
          <span class="state-text">{{ keyword ? '没有匹配的知识库文档' : '知识库暂无文档，点击右上角上传' }}</span>
        </div>

        <div v-else class="table-body">
          <div
            v-for="doc in items"
            :key="doc.filename"
            class="table-row"
            :class="{ batch: batchMode, selected: selected.includes(doc.filename) }"
          >
            <span v-if="batchMode" class="col-check">
              <button
                type="button"
                class="check-circle"
                :class="{ selected: selected.includes(doc.filename) }"
                :aria-label="selected.includes(doc.filename) ? '取消选择' : '选择该文档'"
                @click="toggleSelect(doc.filename)"
              >
                <Check v-if="selected.includes(doc.filename)" class="icon" size="13" />
              </button>
            </span>

            <div class="col-name" @click="openDetail(doc)">
              <FileText class="doc-icon" size="20" />
              <span class="name-texts">
                <span class="doc-title">{{ doc.name }}</span>
                <span class="doc-filename">{{ doc.filename }}</span>
              </span>
            </div>

            <span class="col-size">{{ formatSize(doc.size) }}</span>

            <span class="col-status">
              <span class="status-badge" :class="'tone-' + statusMeta(doc).tone" :title="doc.errorMessage || ''">
                <span v-if="isRunning(doc)" class="spinner small" aria-hidden="true"></span>
                <Check v-else-if="doc.status === 'COMPLETED'" class="status-icon" size="14" />
                <AlertCircle v-else-if="statusMeta(doc).tone === 'failed'" class="status-icon" size="14" />
                <span v-else class="status-dot" aria-hidden="true"></span>
                {{ statusMeta(doc).text }}
              </span>
            </span>

            <span class="col-time">{{ formatTime(doc.updatedAt) }}</span>

            <div class="col-actions">
              <button type="button" class="row-btn" title="查看内容" aria-label="查看内容" @click="openDetail(doc)">
                <Eye class="icon" size="17" />
              </button>
              <button
                v-if="canReindex(doc)"
                type="button"
                class="row-btn"
                title="重新入库"
                aria-label="重新入库"
                :disabled="reindexing.includes(doc.filename)"
                @click="askReindex(doc)"
              >
                <RefreshCw class="icon" :class="{ spin: reindexing.includes(doc.filename) }" size="17" />
              </button>
              <button type="button" class="row-btn danger" title="删除文档" aria-label="删除文档" @click="askDelete(doc)">
                <Trash2 class="icon" size="17" />
              </button>
            </div>
          </div>
        </div>
      </section>

      <!-- 分页 -->
      <div class="pager">
        <span class="pager-info">共 {{ total }} 条 · 第 {{ page }}/{{ totalPages || 1 }} 页</span>
        <div class="pager-btns">
          <button type="button" class="pager-btn" :disabled="page <= 1" aria-label="上一页" @click="goPage(page - 1)">
            <ChevronLeft class="icon" size="16" />
          </button>
          <span class="pager-current">{{ page }} / {{ totalPages || 1 }}</span>
          <button
            type="button"
            class="pager-btn"
            :disabled="totalPages === 0 || page >= totalPages"
            aria-label="下一页"
            @click="goPage(page + 1)"
          >
            <ChevronRight class="icon" size="16" />
          </button>
        </div>
      </div>
    </div>

    <!-- 批量操作条 -->
    <div v-if="batchMode" class="batch-bar">
      <button type="button" class="batch-bar-btn" @click="toggleBatchMode">取消</button>
      <button
        type="button"
        class="batch-bar-btn delete"
        :disabled="selected.length === 0"
        @click="confirmBatchDelete = true"
      >
        删除({{ selected.length }})
      </button>
    </div>

    <!-- 上传弹窗 -->
    <Teleport to="body">
      <div v-if="uploadVisible" class="modal-overlay" @click="closeUpload">
        <div class="modal-content left" @click.stop>
          <div class="modal-title left">上传知识库文档</div>
          <div class="modal-desc left">
            仅支持 .md 格式的 Markdown 文档，单个文件不超过 5MB。上传后将在后台完成预处理与向量化：
            预处理会去除 HTML 内联标签，并在 ## 标题前插入分割线。
          </div>
          <input ref="fileInputRef" class="file-input" type="file" accept=".md" @change="onFileChange" />
          <div class="file-picker">
            <button type="button" class="modal-btn cancel" @click="pickFile">选择文件</button>
            <span class="file-name" :title="uploadFile ? uploadFile.name : ''">
              {{ uploadFile ? uploadFile.name : '未选择文件' }}
            </span>
          </div>
          <p v-if="uploadError" class="form-error">{{ uploadError }}</p>
          <div class="modal-actions">
            <button type="button" class="modal-btn cancel" @click="closeUpload">取消</button>
            <button
              type="button"
              class="modal-btn confirm green"
              :disabled="uploading || !uploadFile"
              @click="submitUpload"
            >
              {{ uploading ? '上传中...' : '开始上传' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 删除确认弹窗 -->
    <Teleport to="body">
      <div v-if="confirmDeleteDoc" class="modal-overlay" @click="confirmDeleteDoc = null">
        <div class="modal-content" @click.stop>
          <div class="modal-title">删除文档</div>
          <div class="modal-desc">
            确定删除「{{ confirmDeleteDoc.filename }}」吗？文档文件与其向量切片将一并删除，删除后无法恢复。
          </div>
          <div class="modal-actions">
            <button type="button" class="modal-btn cancel" @click="confirmDeleteDoc = null">取消</button>
            <button type="button" class="modal-btn confirm" :disabled="deleting" @click="doDelete">
              {{ deleting ? '删除中...' : '确认删除' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 批量删除确认弹窗 -->
    <Teleport to="body">
      <div v-if="confirmBatchDelete" class="modal-overlay" @click="confirmBatchDelete = false">
        <div class="modal-content" @click.stop>
          <div class="modal-title">批量删除文档</div>
          <div class="modal-desc">
            将删除选中的 {{ selected.length }} 个文档及其向量切片，删除后无法恢复，确认继续？
          </div>
          <div class="modal-actions">
            <button type="button" class="modal-btn cancel" @click="confirmBatchDelete = false">取消</button>
            <button type="button" class="modal-btn confirm" :disabled="deleting" @click="doBatchDelete">
              {{ deleting ? '删除中...' : '删除' }}
            </button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 重新入库确认弹窗（仅已完成文档：重跑会先删旧切片，失败则该文档变为失败） -->
    <Teleport to="body">
      <div v-if="confirmReindexDoc" class="modal-overlay" @click="confirmReindexDoc = null">
        <div class="modal-content" @click.stop>
          <div class="modal-title">重新入库</div>
          <div class="modal-desc">
            确定重新入库「{{ confirmReindexDoc.filename }}」吗？将先删除已有
            {{ confirmReindexDoc.chunkCount || 0 }} 个切片，再重跑 预处理 → 分块 → 向量化；失败时该文档状态会变为「失败」。
          </div>
          <div class="modal-actions">
            <button type="button" class="modal-btn cancel" @click="confirmReindexDoc = null">取消</button>
            <button type="button" class="modal-btn confirm green" @click="confirmReindex">确认重跑</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 内容查看弹窗 -->
    <Teleport to="body">
      <div v-if="detailVisible" class="modal-overlay" @click.self="closeDetail">
        <div class="detail-modal" @click.stop>
          <header class="detail-header">
            <div class="detail-texts">
              <div class="detail-title">{{ detail ? detail.name : '加载中...' }}</div>
              <div class="detail-filename">{{ detail ? detail.filename : '' }}</div>
            </div>
            <button type="button" class="icon-btn" aria-label="关闭" @click="closeDetail">
              <X class="icon" size="20" />
            </button>
          </header>

          <div v-if="detailLoading" class="state-block">
            <span class="spinner" aria-hidden="true"></span>
            <span class="state-text">正在读取文档内容...</span>
          </div>

          <template v-else-if="detail">
            <div v-if="detail.errorMessage" class="detail-alert">
              {{ statusMeta(detail).text }}：{{ detail.errorMessage }}
            </div>
            <div v-else-if="detail.status !== 'COMPLETED'" class="detail-alert info">
              该文档当前状态为「{{ statusMeta(detail).text }}」，展示的是磁盘上当前的内容。
            </div>

            <div class="detail-meta">
              <span>大小 {{ formatSize(detail.size) }}</span>
              <span>切片 {{ detail.chunks ? detail.chunks.length : 0 }} 个</span>
              <span>已入库 {{ detail.chunkCount || 0 }} 个</span>
              <span>{{ formatTime(detail.updatedAt) }}</span>
            </div>

            <div class="detail-columns">
              <section class="detail-pane">
                <div class="pane-title">预处理后正文</div>
                <div class="pane-body markdown-body" v-html="renderedContent"></div>
              </section>
              <section class="detail-pane">
                <div class="pane-title">分块结果</div>
                <div class="pane-body chunk-list">
                  <div v-if="!detail.chunks || detail.chunks.length === 0" class="chunk-empty">
                    未解析出内容切片
                  </div>
                  <div
                    v-for="chunk in detail.chunks"
                    :key="chunk.index"
                    class="chunk-item"
                    :class="{ open: expandedChunk === chunk.index }"
                    :data-chunk-index="chunk.index"
                  >
                    <button
                      type="button"
                      class="chunk-head"
                      :aria-expanded="expandedChunk === chunk.index ? 'true' : 'false'"
                      @click="toggleChunk(chunk.index)"
                    >
                      <span class="chunk-head-row">
                        <span class="chunk-index">第 {{ chunk.index }} 片</span>
                        <span class="chunk-title">{{ chunk.title || firstLine(chunk.text) }}</span>
                        <span class="chunk-length">{{ chunk.length }} 字</span>
                        <TriangleRight class="chunk-arrow" size="14" aria-hidden="true" />
                      </span>
                      <span class="chunk-preview">{{ previewText(chunk.text) }}</span>
                    </button>
                    <pre v-if="expandedChunk === chunk.index" class="chunk-text">{{ chunk.text }}</pre>
                  </div>
                </div>
              </section>
            </div>
          </template>
        </div>
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, watch, inject, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import {
  fetchKnowledgeDocuments,
  fetchKnowledgeDocumentContent,
  uploadKnowledgeDocument,
  deleteKnowledgeDocument,
  batchDeleteKnowledgeDocuments,
  reindexKnowledgeDocument,
} from '../api/request'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import {
  ArrowLeft,
  Upload,
  Search,
  ListChecks,
  FileText,
  Eye,
  Trash2,
  RefreshCw,
  ChevronLeft,
  ChevronRight,
  ChevronDown,
  Check,
  AlertCircle,
  TriangleRight,
  X,
} from '@lucide/vue'

const router = useRouter()
const showToast = inject('showToast', () => {})

const PAGE_SIZE = 10
const POLL_INTERVAL = 1500
const MAX_POLL_FAILURES = 3
const MAX_UPLOAD_BYTES = 5 * 1024 * 1024

/** 排序选项（值与后端接口约定一致） */
const SORT_OPTIONS = [
  { value: 'time_desc', label: '按时间排序（最新优先）' },
  { value: 'time_asc', label: '按时间排序（最早优先）' },
  { value: 'name_asc', label: '按名称排序' },
]

/** 状态码 → 展示文案与配色 */
const STATUS_META = {
  PREPROCESSING: { text: '预处理中', tone: 'running' },
  VECTORIZING: { text: '向量化中', tone: 'running' },
  COMPLETED: { text: '已完成', tone: 'done' },
  NOT_INDEXED: { text: '未入库', tone: 'idle' },
  PREPROCESS_FAILED: { text: '预处理失败', tone: 'failed' },
  VECTORIZE_FAILED: { text: '向量化失败', tone: 'failed' },
}

const items = ref([])
const total = ref(0)
const totalPages = ref(0)
const page = ref(1)
const keyword = ref('')
const sort = ref(SORT_OPTIONS[0].value)
const sortOpen = ref(false)
const sortWrapRef = ref(null)
const sortMenuStyle = ref({ top: '0px', left: '0px' })
const sortMenuRef = ref(null)
const activeSortIndex = ref(0)

const sortLabel = computed(
  () => (SORT_OPTIONS.find((item) => item.value === sort.value) || SORT_OPTIONS[0]).label,
)
const loading = ref(false)

const batchMode = ref(false)
const selected = ref([])
const confirmDeleteDoc = ref(null)
const confirmBatchDelete = ref(false)
const confirmReindexDoc = ref(null)
const deleting = ref(false)
const reindexing = ref([])

const uploadVisible = ref(false)
const uploadFile = ref(null)
const uploadError = ref('')
const uploading = ref(false)
const fileInputRef = ref(null)

const detailVisible = ref(false)
const detailLoading = ref(false)
const detail = ref(null)
const expandedChunk = ref(null)

let pollTimer = null
let pollInFlight = false
let pollFailures = 0
let searchTimer = null
let previousStatus = new Map()
let detailRequestId = 0

const allSelected = computed(() => items.value.length > 0 && selected.value.length === items.value.length)

const renderedContent = computed(() => {
  if (!detail.value || !detail.value.content) return ''
  return DOMPurify.sanitize(marked.parse(detail.value.content, { gfm: true }))
})

/** 返回上一级页面（无历史时回首页） */
function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

function statusMeta(doc) {
  return STATUS_META[doc.status] || { text: doc.status || '未知', tone: 'idle' }
}

function isRunning(doc) {
  return doc.status === 'PREPROCESSING' || doc.status === 'VECTORIZING'
}

/** 非处理中的文档均可重新入库（预处理中/向量化中需等待处理完成） */
function canReindex(doc) {
  return !isRunning(doc)
}

function formatSize(bytes) {
  if (bytes === null || bytes === undefined) return '-'
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(1) + ' MB'
}

function formatTime(value) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '-'
  const pad = (n) => String(n).padStart(2, '0')
  const day = `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
  return `${day} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function firstLine(text) {
  if (!text) return ''
  const line = text.split('\n').find((item) => item.trim()) || ''
  return line.length > 40 ? line.slice(0, 40) + '...' : line
}

/** 折叠态卡片的正文预览（只取前若干字符，超出部分由 CSS 两行截断） */
function previewText(text) {
  if (!text) return ''
  const normalized = text.replace(/\n{3,}/g, '\n\n').trim()
  return normalized.length > 160 ? normalized.slice(0, 160) : normalized
}

/** 统一错误提示：403 单独提示权限问题 */
function errorMessage(error, fallback) {
  if (error?.response?.status === 403) {
    return error.response.data?.message || '无管理权限，仅管理员可操作知识库文档'
  }
  return error?.response?.data?.message || error?.message || fallback
}

async function loadDocuments({ silent = false } = {}) {
  if (!silent) loading.value = true
  try {
    const res = await fetchKnowledgeDocuments({
      keyword: keyword.value,
      page: page.value,
      size: PAGE_SIZE,
      sort: sort.value,
    })
    const data = res.data || {}
    const list = data.items || []
    pollFailures = 0
    notifyTransitions(list)
    items.value = list
    total.value = data.total || 0
    totalPages.value = data.totalPages || 0
    if (hasRunning(list)) {
      startPolling()
    } else {
      stopPolling()
    }
  } catch (error) {
    if (silent) {
      // 轮询刷新失败：记录并等下一轮重试，连续失败超过上限才停止并提示
      pollFailures += 1
      console.warn('[知识库文档] 轮询刷新失败:', error)
      if (pollFailures < MAX_POLL_FAILURES) return
    }
    stopPolling()
    showToast(errorMessage(error, '知识库文档加载失败'), 'error')
  } finally {
    if (!silent) loading.value = false
  }
}

function hasRunning(list) {
  return list.some((doc) => isRunning(doc))
}

/** 处理由进行中变为终态的文档，给出一次结果提示 */
function notifyTransitions(list) {
  const previous = previousStatus
  const next = new Map()
  for (const doc of list) {
    const before = previous.get(doc.filename)
    if (before === 'PREPROCESSING' || before === 'VECTORIZING') {
      if (doc.status === 'COMPLETED') {
        showToast(`文档「${doc.filename}」已完成入库，共 ${doc.chunkCount} 个切片`, 'success', 4000)
      } else if (STATUS_META[doc.status]?.tone === 'failed') {
        showToast(`文档「${doc.filename}」${statusMeta(doc).text}：${doc.errorMessage || '未知原因'}`, 'error', 6000)
      }
    }
    next.set(doc.filename, doc.status)
  }
  previousStatus = next
}

function startPolling() {
  if (pollTimer) return
  pollTimer = setInterval(async () => {
    if (pollInFlight) return
    pollInFlight = true
    try {
      await loadDocuments({ silent: true })
      if (!hasRunning(items.value)) stopPolling()
    } finally {
      pollInFlight = false
    }
  }, POLL_INTERVAL)
}

function stopPolling() {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

function goPage(target) {
  if (target < 1 || (totalPages.value > 0 && target > totalPages.value)) return
  selected.value = []
  page.value = target
}

function toggleBatchMode() {
  batchMode.value = !batchMode.value
  if (!batchMode.value) selected.value = []
}

function toggleSelect(filename) {
  const index = selected.value.indexOf(filename)
  if (index === -1) {
    selected.value.push(filename)
  } else {
    selected.value.splice(index, 1)
  }
}

function toggleSelectAll() {
  selected.value = allSelected.value ? [] : items.value.map((doc) => doc.filename)
}

/** 重新加载列表；非首页时借助页码监听回到首页 */
function reloadFromFirstPage() {
  if (page.value === 1) {
    loadDocuments()
  } else {
    page.value = 1
  }
}

function openUpload() {
  uploadVisible.value = true
  uploadError.value = ''
  uploadFile.value = null
}

function closeUpload() {
  if (uploading.value) return
  uploadVisible.value = false
  uploadError.value = ''
  uploadFile.value = null
}

function pickFile() {
  fileInputRef.value?.click()
}

function onFileChange(event) {
  const file = event.target.files && event.target.files[0]
  uploadError.value = ''
  uploadFile.value = null
  if (file) {
    if (!/\.md$/i.test(file.name)) {
      uploadError.value = '仅支持 .md 格式的 Markdown 文档'
    } else if (file.size > MAX_UPLOAD_BYTES) {
      uploadError.value = '文档大小超过限制，单个文档最大 5MB'
    } else if (file.size === 0) {
      uploadError.value = '文档内容为空'
    } else {
      uploadFile.value = file
    }
  }
  // 清空 input，保证再次选择同一个文件仍能触发 change
  event.target.value = ''
}

async function submitUpload() {
  if (!uploadFile.value) {
    uploadError.value = '请选择要上传的 Markdown 文档'
    return
  }
  uploading.value = true
  try {
    const res = await uploadKnowledgeDocument(uploadFile.value)
    uploadVisible.value = false
    uploadFile.value = null
    showToast(res.data?.message || '已开始处理上传的文档', 'success', 4000)
    reloadFromFirstPage()
  } catch (error) {
    uploadError.value = errorMessage(error, '上传失败，请重试')
  } finally {
    uploading.value = false
  }
}

function askDelete(doc) {
  confirmDeleteDoc.value = doc
}

async function doDelete() {
  const doc = confirmDeleteDoc.value
  if (!doc) return
  deleting.value = true
  try {
    const res = await deleteKnowledgeDocument(doc.filename)
    showToast(res.data?.message || '文档已删除', 'success', 4000)
    confirmDeleteDoc.value = null
    selected.value = selected.value.filter((name) => name !== doc.filename)
    previousStatus.delete(doc.filename)
    if (items.value.length === 1 && page.value > 1) {
      page.value -= 1
    } else {
      await loadDocuments()
    }
  } catch (error) {
    showToast(errorMessage(error, '删除失败，请重试'), 'error')
  } finally {
    deleting.value = false
  }
}

async function doBatchDelete() {
  if (selected.value.length === 0) return
  deleting.value = true
  const filenames = [...selected.value]
  try {
    const res = await batchDeleteKnowledgeDocuments(filenames)
    showToast(res.data?.message || '文档已删除', 'success', 4000)
    confirmBatchDelete.value = false
    selected.value = []
    filenames.forEach((name) => previousStatus.delete(name))
    if (items.value.length === filenames.length && page.value > 1) {
      page.value -= 1
    } else {
      await loadDocuments()
    }
    if (items.value.length === 0) toggleBatchMode()
  } catch (error) {
    showToast(errorMessage(error, '批量删除失败，请重试'), 'error')
    // 后端为逐个删除，失败时可能有部分文档已删除，重新拉取以反映真实状态
    await loadDocuments()
  } finally {
    deleting.value = false
  }
}

/** 点「重新入库」：已完成文档有切片会被替换，先二次确认；未入库与失败状态直接重跑 */
function askReindex(doc) {
  if (doc.status === 'COMPLETED') {
    confirmReindexDoc.value = doc
    return
  }
  doReindex(doc)
}

function confirmReindex() {
  const doc = confirmReindexDoc.value
  confirmReindexDoc.value = null
  if (doc) doReindex(doc)
}

async function doReindex(doc) {
  reindexing.value.push(doc.filename)
  try {
    const res = await reindexKnowledgeDocument(doc.filename)
    showToast(res.data?.message || '已开始重新入库', 'success', 4000)
    await loadDocuments()
  } catch (error) {
    showToast(errorMessage(error, '重新入库失败，请重试'), 'error')
  } finally {
    reindexing.value = reindexing.value.filter((name) => name !== doc.filename)
  }
}

async function openDetail(doc) {
  // 请求序号：只采纳最新一次请求的响应，避免先点的文档后返回时覆盖后点的
  const requestId = ++detailRequestId
  detailVisible.value = true
  detailLoading.value = true
  detail.value = null
  expandedChunk.value = null
  try {
    const res = await fetchKnowledgeDocumentContent(doc.filename)
    if (requestId !== detailRequestId) return
    detail.value = res.data
  } catch (error) {
    if (requestId !== detailRequestId) return
    detailVisible.value = false
    showToast(errorMessage(error, '文档内容加载失败'), 'error')
  } finally {
    if (requestId === detailRequestId) detailLoading.value = false
  }
}

function closeDetail() {
  detailRequestId += 1
  detailVisible.value = false
  detail.value = null
  expandedChunk.value = null
}

function toggleChunk(index) {
  const collapsing = expandedChunk.value === index
  expandedChunk.value = collapsing ? null : index
  if (collapsing) return
  nextTick(() => {
    const item = document.querySelector(`.chunk-item[data-chunk-index="${index}"]`)
    if (item) item.scrollIntoView({ block: 'nearest' })
  })
}

watch(keyword, () => {
  clearTimeout(searchTimer)
  searchTimer = setTimeout(() => {
    // 筛选条件变化后旧的勾选不再可见，避免批量删除误作用到看不到的文档
    selected.value = []
    const alreadyFirstPage = page.value === 1
    page.value = 1
    if (alreadyFirstPage) loadDocuments()
  }, 300)
})

/** 展开排序面板：高亮当前选项并按触发按钮位置定位 */
function openSortMenu() {
  activeSortIndex.value = Math.max(
    0,
    SORT_OPTIONS.findIndex((item) => item.value === sort.value),
  )
  sortOpen.value = true
  nextTick(positionSortMenu)
}

function toggleSortMenu() {
  if (sortOpen.value) {
    closeSortMenu()
    return
  }
  openSortMenu()
}

function closeSortMenu() {
  sortOpen.value = false
}

function selectSort(value) {
  sort.value = value
  sortOpen.value = false
  // 选项随面板一起卸载，把焦点交还触发按钮，避免焦点掉到 body
  nextTick(() => {
    const trigger = sortWrapRef.value ? sortWrapRef.value.querySelector('.sort-trigger') : null
    if (trigger) trigger.focus()
  })
}

/** 面板挂在 body 上（脱离滚动容器），用触发按钮的视口坐标定位；下方空间不足则向上翻转 */
function positionSortMenu() {
  const rect = sortWrapRef.value ? sortWrapRef.value.getBoundingClientRect() : null
  const menu = sortMenuRef.value
  if (!rect || !menu) return
  const gap = 8
  const menuHeight = menu.offsetHeight
  const menuWidth = menu.offsetWidth
  const flipUp = window.innerHeight - rect.bottom - gap < menuHeight && rect.top - gap >= menuHeight
  const top = flipUp ? rect.top - gap - menuHeight : rect.bottom + gap
  const left = Math.min(Math.max(8, rect.left), Math.max(8, window.innerWidth - menuWidth - 8))
  sortMenuStyle.value = { top: `${top}px`, left: `${left}px`, minWidth: `${rect.width}px` }
}

/** 触发按钮上的键盘操作：上下移动高亮、回车选中 */
function handleSortKeydown(event) {
  if (event.key === 'Tab') {
    closeSortMenu()
    return
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!sortOpen.value) {
      openSortMenu()
      return
    }
    const step = event.key === 'ArrowDown' ? 1 : -1
    activeSortIndex.value =
      (activeSortIndex.value + step + SORT_OPTIONS.length) % SORT_OPTIONS.length
    return
  }
  if (event.key === 'Enter' && sortOpen.value) {
    event.preventDefault()
    selectSort(SORT_OPTIONS[activeSortIndex.value].value)
  }
}

function handleGlobalKeydown(event) {
  if (event.key === 'Escape' && sortOpen.value) closeSortMenu()
}

function handleSortOutsideClick() {
  if (sortOpen.value) closeSortMenu()
}

watch(sort, () => {
  // 非首页时只改页码，由 page 监听统一触发加载，避免重复请求
  reloadFromFirstPage()
})

watch(page, () => {
  loadDocuments()
})

onMounted(() => {
  loadDocuments()
  document.addEventListener('click', handleSortOutsideClick)
  document.addEventListener('keydown', handleGlobalKeydown)
  window.addEventListener('scroll', handleSortOutsideClick, true)
})

onUnmounted(() => {
  stopPolling()
  clearTimeout(searchTimer)
  document.removeEventListener('click', handleSortOutsideClick)
  document.removeEventListener('keydown', handleGlobalKeydown)
  window.removeEventListener('scroll', handleSortOutsideClick, true)
})
</script>

<style scoped>
.doc-page {
  min-height: 100%;
  padding: 2rem 2rem 6rem;
  background: linear-gradient(160deg, #f0fdf9 0%, #f5f7fb 45%, #ecfdf5 100%);
  color: #1e293b;
}

.doc-shell {
  max-width: 1180px;
  margin: 0 auto;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

/* ===== 头部 ===== */
.page-header {
  display: flex;
  align-items: center;
  gap: 1rem;
  flex-wrap: wrap;
}

.back-link {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 8px 16px;
  min-height: 40px;
  border-radius: 999px;
  border: 1px solid rgba(255, 255, 255, 0.5);
  background: rgba(255, 255, 255, 0.65);
  backdrop-filter: blur(8px);
  -webkit-backdrop-filter: blur(8px);
  color: #10b981;
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s, color 0.2s, box-shadow 0.2s;
}

.back-link:hover {
  background: rgba(255, 255, 255, 0.95);
  color: #059669;
  box-shadow: 0 2px 8px rgba(16, 185, 129, 0.12);
}

.back-link .icon {
  width: 16px;
  height: 16px;
}

.header-texts {
  flex: 1 1 auto;
  min-width: 0;
}

.page-title {
  font-size: 1.5rem;
  font-weight: 700;
  color: #065f46;
  margin-bottom: 2px;
}

.page-subtitle {
  font-size: 0.85rem;
  color: #64748b;
}

.upload-btn {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 20px;
  border: none;
  border-radius: 12px;
  background: linear-gradient(135deg, #34d399, #059669);
  color: #fff;
  font-size: 0.95rem;
  font-weight: 600;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(5, 150, 105, 0.3);
  transition: transform 0.2s ease, box-shadow 0.2s ease, opacity 0.2s ease;
}

.upload-btn:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 24px rgba(5, 150, 105, 0.38);
}

.upload-btn .icon {
  width: 18px;
  height: 18px;
}

/* ===== 工具条 ===== */
.toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  padding: 12px 16px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.7);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 4px 16px rgba(16, 185, 129, 0.08);
}

.search-wrap {
  position: relative;
  display: flex;
  align-items: center;
  flex: 1 1 260px;
  min-width: 0;
}

.search-icon {
  position: absolute;
  left: 12px;
  width: 18px;
  height: 18px;
  color: #94a3b8;
  pointer-events: none;
}

.search-input {
  width: 100%;
  height: 44px;
  padding: 0 12px 0 38px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #1e293b;
  font-size: 0.95rem;
  font-family: inherit;
  transition: border-color 0.2s, box-shadow 0.2s;
}

.search-input::placeholder {
  color: #94a3b8;
}

.search-input:focus {
  outline: none;
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.sort-wrap {
  position: relative;
  flex-shrink: 0;
}

.sort-trigger {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  height: 44px;
  padding: 0 12px 0 14px;
  border-radius: 12px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  color: #1e293b;
  font-size: 0.9rem;
  font-family: inherit;
  cursor: pointer;
  transition: border-color 0.2s, box-shadow 0.2s, background 0.2s;
}

.sort-trigger:hover {
  background: #fff;
  border-color: rgba(16, 185, 129, 0.35);
}

.sort-trigger.open {
  background: #fff;
  border-color: #10b981;
  box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.12);
}

.sort-value {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sort-caret {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: #64748b;
  transition: transform 0.2s ease;
}

.sort-trigger.open .sort-caret {
  transform: rotate(180deg);
}

/* 排序面板：Teleport 到 body，fixed 定位 + 玻璃拟态 */
.sort-menu-fixed {
  position: fixed;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 12px 32px rgba(16, 185, 129, 0.16);
}

.sort-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  min-height: 44px;
  padding: 0 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #334155;
  font-size: 0.88rem;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.sort-option:hover,
.sort-option.highlight {
  background: rgba(16, 185, 129, 0.08);
  color: #047857;
}

.sort-option.active {
  color: #047857;
  font-weight: 600;
}

.sort-option-label {
  white-space: nowrap;
}

.sort-option-check {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: #10b981;
}

.sort-menu-enter-active,
.sort-menu-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}

.sort-menu-enter-from,
.sort-menu-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}

.ghost-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 44px;
  padding: 0 16px;
  border-radius: 12px;
  border: 1px solid rgba(16, 185, 129, 0.2);
  background: rgba(16, 185, 129, 0.06);
  color: #059669;
  font-size: 0.9rem;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.2s, color 0.2s, border-color 0.2s;
}

.ghost-btn:hover {
  background: rgba(16, 185, 129, 0.12);
}

.ghost-btn.active {
  background: #10b981;
  border-color: #10b981;
  color: #fff;
}

.ghost-btn .icon {
  width: 16px;
  height: 16px;
}

/* ===== 列表 ===== */
.table-card {
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.55);
  box-shadow: 0 8px 32px rgba(16, 185, 129, 0.09);
  overflow: hidden;
}

.table-head {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid rgba(16, 185, 129, 0.1);
  font-size: 0.82rem;
  font-weight: 600;
  color: #64748b;
  letter-spacing: 0.02em;
}

.table-body {
  display: flex;
  flex-direction: column;
}

.table-row {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 14px 20px;
  border-bottom: 1px solid rgba(15, 23, 42, 0.04);
  transition: background 0.2s ease;
}

.table-row:last-child {
  border-bottom: none;
}

.table-row:hover {
  background: #f8fafc;
}

.table-row.selected {
  background: rgba(52, 211, 153, 0.08);
}

/* 列宽容纳 44px 的可点区域，勾选圈本身仍是 22px */
.col-check {
  flex: 0 0 44px;
  width: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.col-name {
  flex: 1 1 auto;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  cursor: pointer;
}

.doc-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  color: #10b981;
}

.name-texts {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.doc-title {
  font-size: 0.95rem;
  font-weight: 600;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.doc-filename {
  font-size: 0.78rem;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.col-size {
  flex: 0 0 90px;
  width: 90px;
  font-size: 0.85rem;
  color: #64748b;
}

.col-status {
  flex: 0 0 136px;
  width: 136px;
}

.col-time {
  flex: 0 0 140px;
  width: 140px;
  font-size: 0.82rem;
  color: #94a3b8;
}

/* 3 个 44px 操作按钮 + 2 个 6px 间距 = 144px */
.col-actions {
  flex: 0 0 144px;
  width: 144px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 6px;
}

/* 状态徽标 */
.status-badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 0.8rem;
  font-weight: 500;
  white-space: nowrap;
}

.status-badge.tone-done {
  background: rgba(16, 185, 129, 0.1);
  color: #047857;
}

.status-badge.tone-running {
  background: rgba(59, 130, 246, 0.1);
  color: #2563eb;
}

.status-badge.tone-idle {
  background: #f1f5f9;
  color: #64748b;
}

.status-badge.tone-failed {
  background: rgba(239, 68, 68, 0.1);
  color: #dc2626;
}

.status-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

.status-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: currentColor;
  flex-shrink: 0;
}

/* 行内操作按钮（触控目标不小于 44×44） */
.row-btn {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 10px;
  background: rgba(16, 185, 129, 0.08);
  color: #10b981;
  cursor: pointer;
  transition: background 0.2s, color 0.2s, opacity 0.2s;
}

.row-btn:hover {
  background: rgba(16, 185, 129, 0.16);
  color: #059669;
}

.row-btn.danger {
  background: rgba(239, 68, 68, 0.08);
  color: #ef4444;
}

.row-btn.danger:hover {
  background: rgba(239, 68, 68, 0.16);
  color: #dc2626;
}

.row-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}

.row-btn .icon {
  width: 17px;
  height: 17px;
}

/* 勾选圈（沿用历史对话批量管理样式）：视觉仍为 22px，靠伪元素把可点区域扩到 44×44 */
.check-circle {
  position: relative;
  width: 22px;
  height: 22px;
  padding: 0;
  border-radius: 50%;
  border: 1.5px solid #cbd5e1;
  background: transparent;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  color: #fff;
  transition: background 0.2s, border-color 0.2s;
}

.check-circle:hover {
  border-color: #10b981;
}

.check-circle.selected {
  background: #10b981;
  border-color: #10b981;
}

.check-circle .icon {
  width: 13px;
  height: 13px;
}

.check-circle::before {
  content: '';
  position: absolute;
  top: 50%;
  left: 50%;
  width: 44px;
  height: 44px;
  margin: -22px 0 0 -22px;
  border-radius: 50%;
}

/* ===== 空态 / 加载 ===== */
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10px;
  padding: 56px 20px;
  color: #94a3b8;
  font-size: 0.9rem;
}

.state-icon {
  width: 32px;
  height: 32px;
  color: #a7f3d0;
}

/* ===== 分页 ===== */
.pager {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;
  padding: 0 4px;
}

.pager-info {
  font-size: 0.85rem;
  color: #64748b;
}

.pager-btns {
  display: flex;
  align-items: center;
  gap: 8px;
}

.pager-btn {
  width: 44px;
  height: 44px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border-radius: 10px;
  border: 1px solid rgba(16, 185, 129, 0.2);
  background: rgba(255, 255, 255, 0.8);
  color: #10b981;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.pager-btn:hover:not(:disabled) {
  background: rgba(16, 185, 129, 0.12);
}

.pager-btn:disabled {
  color: #cbd5e1;
  border-color: #e2e8f0;
  cursor: not-allowed;
  background: rgba(255, 255, 255, 0.5);
}

.pager-current {
  font-size: 0.85rem;
  color: #1e293b;
  min-width: 56px;
  text-align: center;
}

/* ===== 批量操作条 ===== */
.batch-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 100;
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px calc(12px + env(safe-area-inset-bottom));
  background: rgba(255, 255, 255, 0.9);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  border-top: 1px solid rgba(16, 185, 129, 0.12);
  box-shadow: 0 -6px 24px rgba(15, 23, 42, 0.06);
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
  transition: background 0.2s ease, color 0.2s ease;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

.batch-bar-btn:hover:not(:disabled) {
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

/* ===== 弹窗（沿用玻璃拟态） ===== */
.modal-overlay {
  position: fixed;
  inset: 0;
  z-index: 1000;
  background: rgba(16, 185, 129, 0.08);
  backdrop-filter: blur(4px);
  -webkit-backdrop-filter: blur(4px);
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20px;
  animation: fadeIn 0.2s ease;
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

.modal-content {
  width: 340px;
  max-width: 100%;
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
}

.modal-title.left,
.modal-desc.left {
  text-align: left;
}

.modal-actions {
  display: flex;
  gap: 0.75rem;
}

.modal-btn {
  flex: 1;
  min-height: 44px;
  padding: 0.6rem;
  border-radius: 10px;
  border: none;
  font-size: 0.9rem;
  font-weight: 500;
  font-family: inherit;
  cursor: pointer;
  transition: all 0.2s ease;
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

.modal-btn.confirm:hover:not(:disabled) {
  background: #dc2626;
  box-shadow: 0 6px 20px rgba(239, 68, 68, 0.4);
  transform: translateY(-1px);
}

.modal-btn.confirm.green {
  background: linear-gradient(135deg, #34d399, #059669);
  box-shadow: 0 4px 12px rgba(5, 150, 105, 0.3);
}

.modal-btn.confirm.green:hover:not(:disabled) {
  background: linear-gradient(135deg, #10b981, #047857);
  box-shadow: 0 6px 20px rgba(5, 150, 105, 0.4);
  transform: translateY(-1px);
}

.modal-btn:disabled {
  opacity: 0.55;
  cursor: not-allowed;
  box-shadow: none;
  transform: none;
}

/* 上传弹窗文件选择 */
.file-input {
  display: none;
}

.file-picker {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 1rem;
}

.file-picker .modal-btn.cancel {
  flex: 0 0 auto;
  padding: 0.6rem 1rem;
}

.file-name {
  flex: 1 1 auto;
  min-width: 0;
  text-align: left;
  font-size: 0.85rem;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.form-error {
  color: #ef4444;
  font-size: 0.85rem;
  text-align: left;
  margin-bottom: 1rem;
}

/* ===== 详情弹窗 ===== */
.detail-modal {
  width: min(1100px, 100%);
  height: min(760px, 88vh);
  display: flex;
  flex-direction: column;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.4);
  box-shadow: 0 20px 56px rgba(16, 185, 129, 0.14);
  overflow: hidden;
  animation: modalSlideUp 0.25s ease;
}

.detail-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 16px 20px;
  border-bottom: 1px solid rgba(16, 185, 129, 0.1);
  background: rgba(255, 255, 255, 0.7);
}

.detail-texts {
  flex: 1 1 auto;
  min-width: 0;
}

.detail-title {
  font-size: 1.05rem;
  font-weight: 600;
  color: #065f46;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.detail-filename {
  font-size: 0.78rem;
  color: #94a3b8;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.icon-btn {
  width: 44px;
  height: 44px;
  flex-shrink: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  border: none;
  border-radius: 10px;
  background: rgba(16, 185, 129, 0.08);
  color: #10b981;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}

.icon-btn:hover {
  background: rgba(16, 185, 129, 0.16);
  color: #059669;
}

.detail-alert {
  margin: 12px 20px 0;
  padding: 10px 14px;
  border-radius: 10px;
  background: rgba(239, 68, 68, 0.08);
  color: #b91c1c;
  font-size: 0.85rem;
  line-height: 1.5;
}

.detail-alert.info {
  background: rgba(59, 130, 246, 0.08);
  color: #1d4ed8;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px 20px;
  font-size: 0.8rem;
  color: #64748b;
}

.detail-columns {
  flex: 1 1 auto;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  gap: 12px;
  padding: 0 20px 20px;
}

.detail-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  border-radius: 14px;
  border: 1px solid rgba(16, 185, 129, 0.12);
  background: #fbfefd;
  overflow: hidden;
}

.pane-title {
  flex-shrink: 0;
  padding: 10px 14px;
  font-size: 0.82rem;
  font-weight: 600;
  color: #047857;
  background: rgba(16, 185, 129, 0.06);
  border-bottom: 1px solid rgba(16, 185, 129, 0.1);
}

.pane-body {
  flex: 1 1 auto;
  min-height: 0;
  overflow-y: auto;
  padding: 14px;
}

.pane-body::-webkit-scrollbar {
  width: 6px;
}

.pane-body::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

.chunk-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.chunk-empty {
  padding: 20px;
  text-align: center;
  color: #94a3b8;
  font-size: 0.85rem;
}

.chunk-item {
  /* 必须禁止收缩：否则固定高度的 flex 列容器会把分块项压扁并裁掉内容 */
  flex-shrink: 0;
  border-radius: 10px;
  border: 1px solid rgba(16, 185, 129, 0.12);
  background: #fff;
  overflow: hidden;
}

.chunk-item.open {
  border-color: rgba(16, 185, 129, 0.3);
}

.chunk-head {
  width: 100%;
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 10px 12px;
  border: none;
  background: transparent;
  cursor: pointer;
  text-align: left;
  font-family: inherit;
  transition: background 0.2s;
}

/* 折叠态高度固定：头部一行 + 两行预览 */
.chunk-item:not(.open) .chunk-head {
  min-height: 86px;
}

.chunk-head:hover {
  background: rgba(16, 185, 129, 0.05);
}

.chunk-head-row {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  min-height: 22px;
}

.chunk-preview {
  display: -webkit-box;
  flex: 1 1 auto;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  /* 固定预留两行高度：预览内容短时卡片高度不变，字号放大时也不会裁切 */
  min-height: 2.9em;
  font-size: 0.78rem;
  line-height: 1.45;
  color: #64748b;
  white-space: pre-wrap;
  word-break: break-word;
}

.chunk-item.open .chunk-preview {
  display: none;
}

.chunk-arrow {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  color: #94a3b8;
  transition: transform 0.2s ease, color 0.2s ease;
}

.chunk-item.open .chunk-arrow {
  transform: rotate(90deg);
  color: #10b981;
}

.chunk-index {
  flex-shrink: 0;
  font-size: 0.75rem;
  font-weight: 600;
  color: #10b981;
}

.chunk-title {
  flex: 1 1 auto;
  min-width: 0;
  font-size: 0.85rem;
  color: #1e293b;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.chunk-length {
  flex-shrink: 0;
  font-size: 0.75rem;
  color: #94a3b8;
}

.chunk-text {
  margin: 0;
  padding: 12px;
  border-top: 1px dashed rgba(16, 185, 129, 0.2);
  background: #f8fafc;
  font-size: 0.8rem;
  line-height: 1.6;
  color: #334155;
  white-space: pre-wrap;
  word-break: break-word;
  /* 展开态固定高度：超长内容在卡片内部滚动 */
  height: 320px;
  overflow-y: auto;
}

.chunk-text::-webkit-scrollbar {
  width: 6px;
}

.chunk-text::-webkit-scrollbar-thumb {
  background: #cbd5e1;
  border-radius: 3px;
}

/* ===== 加载转圈 ===== */
.spinner {
  width: 20px;
  height: 20px;
  border: 2px solid rgba(16, 185, 129, 0.2);
  border-top-color: #10b981;
  border-radius: 50%;
  animation: spin 0.7s linear infinite;
  flex-shrink: 0;
}

.spinner.small {
  width: 12px;
  height: 12px;
  border-width: 1.5px;
}

.spin {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  to { transform: rotate(360deg); }
}

/* ===== 可访问性 ===== */
.back-link:focus-visible,
.upload-btn:focus-visible,
.ghost-btn:focus-visible,
.sort-trigger:focus-visible,
.sort-option:focus-visible,
.row-btn:focus-visible,
.pager-btn:focus-visible,
.check-circle:focus-visible,
.icon-btn:focus-visible,
.modal-btn:focus-visible,
.batch-bar-btn:focus-visible,
.chunk-head:focus-visible {
  outline: 2px solid #10b981;
  outline-offset: 2px;
}

@media (prefers-reduced-motion: reduce) {
  .spinner,
  .spin {
    animation-duration: 2s;
  }

  .modal-overlay,
  .modal-content,
  .detail-modal {
    animation: none;
  }

  .upload-btn:hover,
  .modal-btn.confirm:hover:not(:disabled) {
    transform: none;
  }

  .sort-menu-enter-active,
  .sort-menu-leave-active,
  .sort-caret,
  .chunk-arrow {
    transition: none;
  }
}

/* ===== 响应式 ===== */
@media (max-width: 1024px) {
  .col-time {
    flex: 0 0 120px;
    width: 120px;
  }
}

@media (max-width: 768px) {
  .doc-page {
    padding: 1rem 1rem 6rem;
  }

  .page-title {
    font-size: 1.25rem;
  }

  .upload-btn {
    width: 100%;
    justify-content: center;
  }

  .toolbar {
    padding: 10px 12px;
  }

  /* 表格转卡片：表头隐藏，元信息换行展示 */
  .table-head {
    display: none;
  }

  .table-row {
    flex-wrap: wrap;
    row-gap: 10px;
    padding: 14px 16px;
    align-items: flex-start;
  }

  .col-check {
    order: 0;
    flex: 0 0 44px;
  }

  .col-name {
    order: 1;
    flex: 1 1 50%;
  }

  .col-actions {
    order: 2;
    flex: 0 0 auto;
    width: auto;
    margin-left: auto;
  }

  .col-size,
  .col-status,
  .col-time {
    order: 3;
    flex: 0 0 auto;
    width: auto;
  }

  .col-time {
    margin-left: auto;
  }

  .detail-columns {
    grid-template-columns: minmax(0, 1fr);
    grid-template-rows: minmax(0, 1fr) minmax(0, 1fr);
  }

  .detail-modal {
    height: 90vh;
  }

  .detail-alert,
  .detail-meta {
    margin-left: 12px;
    margin-right: 12px;
  }

  .detail-meta {
    padding: 10px 12px;
    gap: 10px;
  }

  .detail-columns {
    padding: 0 12px 12px;
  }
}

@media (max-width: 480px) {
  .header-texts {
    flex: 1 1 100%;
    order: 2;
  }

  .back-link {
    order: 1;
  }

  .upload-btn {
    order: 3;
  }
}
</style>
