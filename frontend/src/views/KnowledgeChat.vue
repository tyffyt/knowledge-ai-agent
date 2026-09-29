<template>
  <div class="chat-layout">
    <!-- Sidebar -->
    <div class="sidebar" :class="{ 'sidebar-open': isSidebarOpen }">
      <div class="sidebar-header">
        <h3>历史对话</h3>
        <button v-if="!batchMode" class="new-chat-btn" @click="createNewChat">
	          <Plus class="icon" size="16" />
	          新对话
	        </button>
      </div>
      <div class="history-list" v-if="historyList.length > 0" @scroll="menuChatId = ''">
        <div
          v-for="chat in historyList"
          :key="chat.chatId"
          class="history-item"
          :class="{ active: chat.chatId === chatId && !batchMode, 'batch-mode': batchMode }"
        >
          <div class="history-item-main" :class="{ 'batch-select': batchMode }" @click="batchMode ? toggleSelectChat(chat.chatId) : loadHistoryChat(chat.chatId)">
            <div class="history-title">{{ chat.title }}</div>
            <div class="history-time">{{ new Date(chat.updatedAt || chat.createdAt).toLocaleString() }}</div>
          </div>
          <!-- 非批量模式：三点按钮（菜单经 Teleport 渲染到 body，避免被列表滚动容器裁剪） -->
          <div v-if="!batchMode" class="history-more-wrap">
            <button class="history-more-btn" @click.stop="toggleMenu(chat.chatId, $event)" title="更多操作" aria-label="更多操作">
              <MoreVertical class="icon" size="18" />
            </button>
          </div>
          <!-- 批量模式：勾选圆圈 -->
          <div v-else class="batch-check-wrap" @click.stop="toggleSelectChat(chat.chatId)">
            <span v-if="selectedChatIds.includes(chat.chatId)" class="batch-circle selected">
              <Check class="icon" size="14" />
            </span>
            <span v-else class="batch-circle"></span>
          </div>
        </div>
      </div>
      <div class="history-empty" v-else>
        暂无历史记录
      </div>
      <!-- 移动端底部个人信息卡片（批量管理模式隐藏；桌面端已由 user-dock 承担，不显示） -->
      <div v-if="isMobile && !batchMode" class="sidebar-footer">
        <div class="profile-card">
          <div class="profile-info">
            <div class="profile-avatar"><span class="profile-avatar-letter">{{ userAvatarLetter }}</span></div>
            <span class="profile-name">{{ reactiveUsername }}</span>
          </div>
          <div class="profile-actions">
            <button class="profile-action-btn" @click="goChangePassword" title="修改密码">
              <Lock class="profile-action-icon" size="16" />
              修改密码
            </button>
            <button class="profile-action-btn" @click="logout" title="退出登录">
              <LogOut class="profile-action-icon" size="16" />
              退出登录
            </button>
          </div>
        </div>
      </div>
      <!-- 批量管理模式底部按钮条（取消 / 删除(N)） -->
      <div v-if="batchMode" class="batch-bar">
        <button class="batch-bar-btn" @click="exitBatchMode">取消</button>
        <button class="batch-bar-btn delete" :disabled="selectedChatIds.length === 0" @click="confirmBatchDelete = true">
          删除 ({{ selectedChatIds.length }})
        </button>
      </div>
    </div>

    <!-- 三点菜单（Teleport 到 body：fixed 定位，脱离历史列表滚动容器，任何情况下不被裁剪/遮挡） -->
    <Teleport to="body">
      <div v-if="menuChatId" class="history-menu-fixed" :style="menuStyle">
        <button class="history-menu-item" @click.stop="onBatchManage">
          <ListChecks class="menu-icon" size="18" />
          批量管理
        </button>
        <button class="history-menu-item" @click.stop="onEditTitle(menuChatChat)">
          <Pencil class="menu-icon" size="18" />
          修改标题
        </button>
        <button class="history-menu-item danger" @click.stop="openDeleteConfirm(menuChatId)">
          <Trash2 class="menu-icon" size="18" />
          删除对话
        </button>
      </div>
    </Teleport>

    <!-- 侧边栏右缘外侧切换按钮（不遮挡列表滚动条） -->
    <button class="toggle-sidebar-btn" :class="{ collapsed: !isSidebarOpen }" @click="toggleSidebar" :title="isSidebarOpen ? '收起历史对话' : '展开历史对话'">
	      <ChevronLeft v-if="isSidebarOpen" class="icon" size="18" />
	      <ChevronRight v-else class="icon" size="18" />
	    </button>

    <!-- 移动端抽屉遮罩 -->
    <div v-if="isMobile && isSidebarOpen" class="sidebar-backdrop" @click="closeSidebar"></div>

    <!-- Main Chat Container -->
    <div class="chat-container">
        <div class="header">
          <div class="header-left">
            <button class="sidebar-menu-btn" @click="toggleSidebar" title="历史对话" aria-label="历史对话">
	              <Menu class="icon" size="18" />
	            </button>
            <button class="back-btn" @click="$router.push('/')">
	              <ArrowLeft class="icon" size="16" />
	              返回
	            </button>
            <h2 class="header-title">{{ currentChatTitle }}</h2>
          </div>
        <div class="header-right">
          <span class="chat-id-display">{{ chatId }}</span>
        </div>
      </div>
    <div class="messages" ref="messagesRef" @click="handleMessagesClick">
      <!-- 空状态欢迎语 -->
	      <div v-if="messages.length === 0" class="welcome">
	        <div class="welcome-icon-wrap">
	          <svg viewBox="0 0 48 48" fill="none" class="welcome-icon">
	            <path d="M8 8h20v24H8z" stroke="#10B981" stroke-width="1.8" fill="rgba(16,185,129,0.06)"/>
	            <path d="M12 14h12M12 18h8M12 22h4" stroke="#10B981" stroke-width="1.8" stroke-linecap="round"/>
	            <path d="M28 12l6 2v24l-6-2V12z" stroke="#10B981" stroke-width="1.8" fill="rgba(16,185,129,0.06)"/>
	            <circle cx="28" cy="30" r="2" fill="#F59E0B" opacity="0.7"/>
	          </svg>
	        </div>
	        <h3>你的知识库已就绪</h3>
	        <p class="welcome-desc">基于你的笔记文档与网页收藏，AI 帮你回忆、理解和思考</p>
	        <div class="welcome-tips">
	          <span class="tip-item">
	            <svg viewBox="0 0 24 24" fill="none" class="tip-icon"><path d="M12 4v16M4 12h16" stroke="#F59E0B" stroke-width="2" stroke-linecap="round"/></svg>
	            新建会话
	          </span>
	          <span class="tip-item">
	            <svg viewBox="0 0 24 24" fill="none" class="tip-icon"><circle cx="11" cy="11" r="6" stroke="#10B981" stroke-width="2"/><path d="M20 20l-4.3-4.3" stroke="#10B981" stroke-width="2" stroke-linecap="round"/></svg>
	            检索知识库
	          </span>
	          <span class="tip-item">
	            <svg viewBox="0 0 24 24" fill="none" class="tip-icon"><path d="M21 12a9 9 0 11-9-9" stroke="#10B981" stroke-width="2" stroke-linecap="round"/><path d="M12 6v6l3 3" stroke="#10B981" stroke-width="2" stroke-linecap="round"/></svg>
	            快速问答
	          </span>
	        </div>
	      </div>
      <div
        v-for="(msg, i) in messages"
        :key="i"
        :class="['message-row', msg.role]"
      >
        <div
          class="chat-avatar"
          :class="msg.role"
        >
          <template v-if="msg.role === 'assistant'">
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
          </template>
            <template v-else><span class="chat-user-letter">{{ userAvatarLetter }}</span></template>
        </div>
        <div class="bubble-content" :class="{ 'markdown-body': msg.role === 'assistant' }">
          <template v-if="msg.role === 'user'">{{ msg.content.trim() }}</template>
          <div v-else>
            <div v-if="loading && i === messages.length - 1" class="streaming-text">{{ msg.content }}</div>
            <div v-else v-html="renderMarkdown(msg.content)"></div>
            <!-- 播报按钮 + 知识库引用（分割线下方区域） -->
            <div class="speech-refs-area">
              <!-- 语音播报按钮：仅图标，无边框无底色；播报中变为停止图标 -->
              <button
                class="speech-btn"
                :class="{ speaking: msg._speaking }"
                @click="toggleSpeech(msg)"
                :title="msg._speaking ? '停止播报' : '语音播报'"
                :aria-label="msg._speaking ? '停止播报' : '语音播报'"
              >
                <Volume2 v-if="!msg._speaking" size="16" />
                <VolumeX v-else size="16" />
              </button>
              <!-- RAG 引用切片展示 -->
              <div v-if="msg.references && msg.references.length > 0" class="rag-references">
		              <div class="rag-refs-header">
			                <FileText class="refs-icon" size="16" />
			                <span>知识库引用 <span class="refs-count">{{ msg.references.length }}</span></span>
			                <button class="refs-toggle" @click="msg._refsCollapsed = !msg._refsCollapsed">
			                  <ChevronDown class="toggle-icon" :class="{ rotated: !msg._refsCollapsed }" size="18" />
			                </button>
			              </div>
			              <div v-if="!msg._refsCollapsed" class="rag-refs-list">
			                <div v-for="(ref, ri) in msg.references" :key="ri" class="rag-ref-item">
			                  <div class="rag-ref-index">{{ ref.index }}</div>
			                  <div class="rag-ref-body">
			                    <div class="rag-ref-content">{{ ref.content }}</div>
			                    <div v-if="ref.metadata && ref.metadata.filename" class="rag-ref-source">
			                      <svg viewBox="0 0 24 24" fill="none" class="source-icon"><path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8l-6-6z" stroke="currentColor" stroke-width="1.5"/><path d="M14 2v6h6" stroke="currentColor" stroke-width="1.5"/></svg>
			                      {{ ref.metadata.filename }}
			                    </div>
			                  </div>
			                </div>
			              </div>
		            </div>
            </div>
	          </div>
	        </div>
	      </div>
      <!-- 打字指示器 -->
      <div v-if="loading" class="typing-indicator">
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
        <div class="typing-dot"></div>
      </div>
    </div>
    <div class="input-area">
      <!-- 输入框容器：输入框在上，底部工具条在下（左下 RAG 开关、右下大模型切换） -->
      <div class="input-composer">
        <textarea
          ref="inputRef"
          v-model="inputText"
          placeholder="输入你想了解的内容..."
          rows="1"
          :disabled="loading"
          @keydown.enter.prevent="onInputEnter"
        />
        <div class="input-toolbar">
          <label class="rag-toggle" title="开启后 AI 会从知识库检索相关内容回答问题" @click="ragEnabled = !ragEnabled">
            <Search class="rag-toggle-icon" size="16" />
            <!-- 移动端用短标签 RAG，桌面端用完整标签 -->
            <span class="rag-label-full">RAG</span>
            <span class="rag-label-short">RAG</span>
            <div class="toggle-switch" :class="{ active: ragEnabled }">
              <div class="toggle-knob"></div>
            </div>
          </label>
          <!-- 大模型切换：面板 Teleport 到 body，避免被 sticky 输入区裁剪 -->
          <div ref="modelWrapRef" class="model-select">
            <button
              type="button"
              class="model-trigger"
              :class="{ open: modelMenuOpen }"
              role="combobox"
              aria-haspopup="listbox"
              :aria-expanded="modelMenuOpen ? 'true' : 'false'"
              aria-controls="chat-model-menu"
              :aria-activedescendant="modelMenuOpen ? `chat-model-option-${activeModelIndex}` : undefined"
              :aria-label="'当前大模型：' + currentModelLabel"
              title="切换当前对话使用的大模型"
              @click.stop="toggleModelMenu"
              @keydown="handleModelKeydown"
            >
              <Cpu class="model-trigger-icon" size="16" />
              <span class="model-trigger-text">{{ currentModelLabel }}</span>
              <ChevronDown class="model-trigger-caret" size="14" />
            </button>
          </div>
        </div>
      </div>
      <!-- 语音输入（麦克风录音转文字） -->
      <button
        class="mic-btn"
        :class="{ recording }"
        @click="toggleRecording"
        :disabled="transcribing || loading"
        :title="recording ? '停止录音并转写 (' + recordSeconds + 's)' : (transcribing ? '正在转写...' : '语音输入')"
      >
        <span v-if="transcribing" class="mic-spinner"></span>
        <MicOff v-else-if="recording" size="18" />
        <Mic v-else size="18" />
      </button>
      <button class="send-btn" :class="{ 'stop-btn': loading }" :disabled="loading ? false : !inputText.trim()" @click="loading ? stopStream() : send()">
	        <template v-if="loading">
	          <Square class="btn-icon" size="18" />
	          <span class="btn-text">终止</span>
	        </template>
	        <template v-else>
	          <ArrowRight class="btn-icon" size="18" />
	          <span class="btn-text">发送</span>
	        </template>
	      </button>
    </div>
    </div>

    <!-- 大模型选择面板：Teleport 到 body + fixed 定位，避免被 sticky 输入区裁剪 -->
    <Teleport to="body">
      <Transition name="model-menu">
        <div
          v-if="modelMenuOpen"
          id="chat-model-menu"
          ref="modelMenuRef"
          class="model-menu-fixed"
          :style="modelMenuStyle"
          role="listbox"
          aria-label="选择大模型"
          @click.stop
        >
          <div v-if="!modelList.length" class="model-menu-empty">
            {{ modelsFailed ? '模型列表加载失败，请稍后重试' : '正在加载模型列表...' }}
          </div>
          <button
            v-for="(item, index) in modelList"
            :id="`chat-model-option-${index}`"
            :key="item.key"
            type="button"
            class="model-option"
            :class="{ active: currentModelKey === item.key, highlight: activeModelIndex === index, unavailable: !item.available }"
            role="option"
            tabindex="-1"
            :aria-selected="currentModelKey === item.key ? 'true' : 'false'"
            :aria-disabled="item.available ? 'false' : 'true'"
            @click.stop="selectModel(item)"
          >
            <span class="model-option-main">
              <span class="model-option-name">{{ item.displayName }}</span>
              <span class="model-option-meta">
                {{ item.available ? item.provider : '未配置密钥，暂不可用' }}
              </span>
            </span>
            <Check v-if="currentModelKey === item.key" class="model-option-check" size="16" />
          </button>
        </div>
      </Transition>
    </Teleport>

    <!-- 删除确认弹窗 -->
    <Teleport to="body">
      <div v-if="confirmDeleteChatId" class="modal-overlay" @click="confirmDeleteChatId = ''">
        <div class="modal-content" @click.stop>
          <div class="modal-title">删除对话</div>
          <div class="modal-desc">确定要删除这个对话吗？删除后无法恢复。</div>
          <div class="modal-actions">
            <button class="modal-btn cancel" @click="confirmDeleteChatId = ''">取消</button>
            <button class="modal-btn confirm" @click="doDeleteChat(confirmDeleteChatId)">确认删除</button>
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
            class="edit-title-input modal-title-input"
            v-model="editTitleText"
            @keyup.enter="confirmEditTitle"
            @keyup.escape="closeEditTitle"
            autofocus
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
          <div class="modal-title left">删除对话记录</div>
          <div class="modal-desc left">删除后内容将无法恢复，确认删除选中记录？</div>
          <div class="modal-actions">
            <button class="modal-btn cancel" @click="confirmBatchDelete = false">取消</button>
            <button class="modal-btn confirm" @click="doBatchDelete">删除</button>
          </div>
        </div>
      </div>
    </Teleport>

    <!-- 图片预览弹窗 -->
    <Teleport to="body">
      <div v-if="previewImage.show" class="image-preview-overlay" @click.self="closePreview">
        <button class="image-preview-close" @click="closePreview" title="关闭">
	          <X size="22" />
	        </button>
        <img :src="previewImage.src" :alt="previewImage.alt" class="image-preview-img" @click.self="closePreview" />
      </div>
    </Teleport>
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted, onUnmounted, watch, inject } from 'vue'
import { useRouter } from 'vue-router'
import { streamKnowledgeChat, streamKnowledgeChatRag, request, updateChatTitle, deleteChat, batchDeleteChats } from '../api/request'
import { username as reactiveUsername, removeToken } from '../utils/auth'
import { linkifyHtml } from '../utils/linkify'
import { previewImage, openPreview, closePreview } from '../utils/previewImage'
import { modelList, currentModel, modelsFailed, fetchModels, setSelectedModel } from '../utils/model'
import { marked } from 'marked'
import DOMPurify from 'dompurify'
import { Plus, Pencil, Trash2, ChevronLeft, ChevronRight, ArrowLeft, Search, ChevronDown, ArrowRight, Square, X, FileText, Volume2, VolumeX, Mic, MicOff, Menu, Lock, LogOut, MoreVertical, ListChecks, Check, Cpu } from '@lucide/vue'

const router = useRouter()

const chatId = ref('')
const messages = ref([])
const inputText = ref('')
const loading = ref(false)
const messagesRef = ref(null)
const inputRef = ref(null)
const abortController = ref(null)

/**
 * 输入框高度自适应：初始一行，内容变多时增高（上限与 CSS max-height 一致），清空后回到一行
 */
watch(inputText, () => {
  const el = inputRef.value
  if (!el) return
  nextTick(() => {
    el.style.height = 'auto'
    // 上限取 CSS 的 max-height（30vh），超出后由 textarea 内部滚动
    const limit = parseFloat(getComputedStyle(el).maxHeight)
    el.style.height = `${Number.isFinite(limit) ? Math.min(el.scrollHeight, limit) : el.scrollHeight}px`
  })
})

// RAG 知识库搜索开关
const ragEnabled = ref(true)

// ===== 大模型切换 =====
const modelWrapRef = ref(null)
const modelMenuRef = ref(null)
const modelMenuOpen = ref(false)
const modelMenuStyle = ref({ top: '0px', left: '0px' })
const activeModelIndex = ref(0)

/** 当前生效的模型标识，清单未加载完成时为空（后端按默认模型处理） */
const currentModelKey = computed(() => (currentModel.value ? currentModel.value.key : ''))

/** 触发按钮上显示的模型名 */
const currentModelLabel = computed(() => (currentModel.value ? currentModel.value.displayName : '大模型'))

/** 从指定位置沿指定方向找下一个可用模型的下标，找不到时返回原下标 */
function findAvailableModelIndex(from, step) {
  const list = modelList.value
  const total = list.length
  if (!total) return from
  let index = from
  for (let i = 0; i < total; i++) {
    index = (index + step + total) % total
    if (list[index].available) return index
  }
  return from
}

/** 展开模型面板：高亮当前模型并定位，清单为空时重试拉取 */
function openModelMenu() {
  const list = modelList.value
  const currentIndex = list.findIndex((m) => m.key === currentModelKey.value && m.available)
  // 清单为空（尚未加载或加载失败）时保持 -1，等清单到达后由 watch 修正为当前模型
  activeModelIndex.value = currentIndex >= 0 ? currentIndex : (list.length ? findAvailableModelIndex(-1, 1) : -1)
  modelMenuOpen.value = true
  nextTick(positionModelMenu)
  if (!list.length) {
    fetchModels().catch(() => {})
  }
}

// 清单异步到达后：面板高度会变化，重新定位并补上高亮，避免选项落到视口外或回车无响应
watch(modelList, () => {
  if (!modelMenuOpen.value) return
  const currentIndex = modelList.value.findIndex((m) => m.key === currentModelKey.value && m.available)
  activeModelIndex.value = currentIndex >= 0 ? currentIndex : findAvailableModelIndex(-1, 1)
  nextTick(positionModelMenu)
}, { flush: 'post' })

function toggleModelMenu() {
  if (modelMenuOpen.value) {
    closeModelMenu()
    return
  }
  openModelMenu()
}

function closeModelMenu() {
  modelMenuOpen.value = false
}

/** 选择模型：不可用项不响应，选择结果持久化到本地并对后续对话生效 */
function selectModel(item) {
  if (!item.available) return
  setSelectedModel(item.key)
  modelMenuOpen.value = false
  // 选项随面板一起卸载，把焦点交还触发按钮，避免焦点掉到 body
  nextTick(() => {
    const trigger = modelWrapRef.value ? modelWrapRef.value.querySelector('.model-trigger') : null
    if (trigger) trigger.focus()
  })
}

/** 面板挂在 body 上（脱离滚动容器），用触发按钮的视口坐标定位；空间不足则向上翻转并夹在视口内 */
function positionModelMenu() {
  const rect = modelWrapRef.value ? modelWrapRef.value.getBoundingClientRect() : null
  const menu = modelMenuRef.value
  if (!rect || !menu) return
  const gap = 8
  const menuHeight = menu.offsetHeight
  const menuWidth = menu.offsetWidth
  const flipUp = window.innerHeight - rect.bottom - gap < menuHeight && rect.top - gap >= menuHeight
  const top = flipUp ? Math.max(8, rect.top - gap - menuHeight) : rect.bottom + gap
  // 触发按钮位于输入框右下角：面板优先与按钮右缘对齐，再夹在视口内
  const left = Math.min(Math.max(8, rect.right - menuWidth), Math.max(8, window.innerWidth - menuWidth - 8))
  modelMenuStyle.value = { top: `${top}px`, left: `${left}px`, minWidth: `${rect.width}px` }
}

/** 触发按钮上的键盘操作：上下移动高亮（跳过不可用项）、回车选中、Esc/Tab 关闭 */
function handleModelKeydown(event) {
  if (event.key === 'Tab' || event.key === 'Escape') {
    closeModelMenu()
    return
  }
  if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
    event.preventDefault()
    if (!modelMenuOpen.value) {
      openModelMenu()
      return
    }
    activeModelIndex.value = findAvailableModelIndex(activeModelIndex.value, event.key === 'ArrowDown' ? 1 : -1)
    nextTick(positionModelMenu)
    return
  }
  if (event.key === 'Enter' && modelMenuOpen.value) {
    event.preventDefault()
    const item = modelList.value[activeModelIndex.value]
    if (item) selectModel(item)
  }
}

/** 点击模型面板外部关闭面板 */
function handleModelOutsideClick() {
  if (modelMenuOpen.value) closeModelMenu()
}

/** 页面滚动时关闭模型面板（面板用视口坐标固定定位，滚动后会与触发按钮错位） */
function handleModelScroll() {
  if (modelMenuOpen.value) closeModelMenu()
}

// Sidebar state from App.vue
const isSidebarOpen = inject('isSidebarOpen', ref(true))
const setSidebarOpen = inject('setSidebarOpen', (v) => {})
const showToast = inject('showToast', () => {})

// 移动端检测（<=768px）
const isMobile = ref(false)
function updateIsMobile() {
  isMobile.value = window.innerWidth <= 768
}

// History state
const historyList = ref([])

// 编辑标题弹窗状态
const editTitleVisible = ref(false)
const editTitleText = ref('')
let editTitleTargetId = ''
// 删除确认状态
const confirmDeleteChatId = ref('')
// 三点菜单状态（Teleport 到 body，fixed 定位，位置由菜单项视口坐标计算）
const menuChatId = ref('')
const menuStyle = ref({ top: '0px', left: '0px' })
const menuChatChat = computed(() => historyList.value.find(c => c.chatId === menuChatId.value))
// 批量管理模式状态
const batchMode = ref(false)
const selectedChatIds = ref([])
const confirmBatchDelete = ref(false)

// 图片预览状态（共享单例，见 utils/previewImage.js）

// 当前会话标题
const currentChatTitle = computed(() => {
  if (!chatId.value) return '个人知识助手'
  const found = historyList.value.find(c => c.chatId === chatId.value)
  return found ? found.title : '个人知识助手'
})

const userAvatarLetter = computed(() => {
  const name = reactiveUsername.value
  return name ? name.trim().charAt(0).toUpperCase() : '?'
})

function generateChatId() {
  const name = reactiveUsername.value || 'anonymous'
  return `know_${name}_${Date.now()}_${Math.random().toString(36).slice(2, 10)}`
}

async function fetchHistoryList() {
  try {
    const res = await request.get('/ai/knowledge/chat/history')
    historyList.value = res.data
  } catch (error) {
    console.error('Failed to load history list:', error)
  }
}

async function loadHistoryChat(loadChatId) {
  if (chatId.value === loadChatId) return
  if (isMobile.value) {
    isSidebarOpen.value = false
  }
  // 切换会话时清空输入框，避免残留内容误发到其他会话
  inputText.value = ''
  try {
    const res = await request.get(`/ai/knowledge/chat/history/${loadChatId}`)
    chatId.value = loadChatId
    messages.value = res.data.map(m => ({
      role: m.role.toLowerCase(),
      content: m.content,
      references: m.references || [],
      _refsCollapsed: true
    }))
    scrollToBottom()
  } catch (error) {
    console.error('Failed to load history chat:', error)
  }
}

function createNewChat() {
  chatId.value = generateChatId()
  messages.value = []
  if (isMobile.value) {
    isSidebarOpen.value = false
  }
}

// 侧边栏切换（同步到 App.vue）
function toggleSidebar() {
  isSidebarOpen.value = !isSidebarOpen.value
}

// 关闭侧边栏（移动端抽屉遮罩点击）
function closeSidebar() {
  isSidebarOpen.value = false
}

// 三点菜单：打开/关闭（菜单 Teleport 到 body，用视口坐标计算 fixed 位置；下方空间不足自动向上展开）
function toggleMenu(chatId, event) {
  const opening = menuChatId.value !== chatId
  menuChatId.value = opening ? chatId : ''
  if (!opening) return
  const item = event?.currentTarget?.closest?.('.history-item')
  nextTick(() => {
    const menuEl = document.querySelector('.history-menu-fixed')
    const rect = item?.getBoundingClientRect()
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

// 三点菜单：进入批量管理模式
function onBatchManage() {
  menuChatId.value = ''
  batchMode.value = true
  selectedChatIds.value = []
}

// 三点菜单：打开修改标题弹窗（预填当前标题）
function onEditTitle(chat) {
  menuChatId.value = ''
  editTitleTargetId = chat.chatId
  editTitleText.value = chat.title
  editTitleVisible.value = true
}

// 关闭修改标题弹窗
function closeEditTitle() {
  editTitleVisible.value = false
}

// 确定修改标题：校验非空后调用接口，成功刷新列表
async function confirmEditTitle() {
  const title = editTitleText.value.trim()
  if (!title) {
    showToast('标题不能为空', 'error')
    return
  }
  try {
    await updateChatTitle(editTitleTargetId, title)
    editTitleVisible.value = false
    fetchHistoryList()
    showToast('标题更新成功', 'success')
  } catch (error) {
    console.error('Failed to update title:', error)
    showToast('标题更新失败，请重试', 'error')
  }
}

// 三点菜单：打开单体删除确认弹窗（复用现有弹窗）
function openDeleteConfirm(chatId) {
  menuChatId.value = ''
  confirmDeleteChatId.value = chatId
}

// 批量模式：选中/取消选中会话
function toggleSelectChat(chatId) {
  const idx = selectedChatIds.value.indexOf(chatId)
  if (idx >= 0) {
    selectedChatIds.value.splice(idx, 1)
  } else {
    selectedChatIds.value.push(chatId)
  }
}

// 批量模式：退出并清空选中
function exitBatchMode() {
  batchMode.value = false
  selectedChatIds.value = []
  confirmBatchDelete.value = false
}

// 批量删除确认后执行：调用接口，成功后刷新列表（当前会话被删则新建）
async function doBatchDelete() {
  const ids = selectedChatIds.value
  if (ids.length === 0) return
  try {
    const res = await batchDeleteChats(ids)
    const deleted = res.data?.deleted ?? ids.length
    confirmBatchDelete.value = false
    exitBatchMode()
    if (ids.includes(chatId.value)) {
      createNewChat()
    }
    fetchHistoryList()
    showToast(`已删除 ${deleted} 个会话`, 'success')
  } catch (error) {
    console.error('Failed to batch delete chats:', error)
    confirmBatchDelete.value = false
    exitBatchMode()
    showToast('批量删除失败，请重试', 'error')
  }
}

// 删除会话
async function doDeleteChat(deleteId) {
  try {
    await deleteChat(deleteId)
    confirmDeleteChatId.value = ''
    if (deleteId === chatId.value) {
      createNewChat()
    }
    fetchHistoryList()
    showToast('会话已删除', 'success')
  } catch (error) {
    console.error('Failed to delete chat:', error)
    confirmDeleteChatId.value = ''
    showToast('删除失败，请重试', 'error')
  }
}

onMounted(() => {
  chatId.value = generateChatId()
  fetchHistoryList()

  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)

  // 从修改密码页返回时保持侧边栏打开（进入时打的标记，读取后立即清除防止残留误开）
  if (isMobile.value) {
    isSidebarOpen.value = sessionStorage.getItem('return_to_knowledge') === '1'
    sessionStorage.removeItem('return_to_knowledge')
  }

  // 图片加载失败自动隐藏（DOMPurify 会剥离 img 的 onerror 属性，改用事件捕获监听）
  document.addEventListener('error', handleImageError, true)
  // ESC 键关闭预览（具名 handler，卸载时移除）
  document.addEventListener('keydown', handleKeydown)
  // 点击外部关闭三点菜单
  document.addEventListener('click', handleMenuOutsideClick)
  // 点击外部关闭模型面板
  document.addEventListener('click', handleModelOutsideClick)
  // 滚动时关闭模型面板
  window.addEventListener('scroll', handleModelScroll, true)

  // 拉取大模型清单（带缓存；失败不阻塞页面，展开面板时会重试）
  fetchModels().catch(() => {})
})

/** 点击历史对话外的空白处关闭三点菜单 */
function handleMenuOutsideClick() {
  menuChatId.value = ''
}

/** 全局 ESC 键：关闭图片预览与模型面板 */
function handleKeydown(e) {
  if (e.key !== 'Escape') return
  if (previewImage.value.show) {
    closePreview()
  }
  if (modelMenuOpen.value) {
    closeModelMenu()
  }
}

/** 聊天图片加载失败时隐藏（避免显示裂图） */
function handleImageError(event) {
  const target = event.target
  if (target && target.tagName === 'IMG' && target.classList.contains('chat-image')) {
    target.style.display = 'none'
  }
}

// 长按图片保存逻辑已移除（点击图片放大预览由 handleMessagesClick 事件委托处理）

/** 消息区点击：图片放大预览（事件委托，避免依赖 DOMPurify 保留内联事件属性） */
function handleMessagesClick(e) {
  const img = e.target?.closest?.('img.chat-image')
  if (img) {
    openPreview(img.getAttribute('src') || '', img.getAttribute('alt') || '')
  }
}

/** 打开修改密码页（移动端个人信息卡片入口），打标记供返回时恢复侧边栏 */
function goChangePassword() {
  closeSidebar()
  sessionStorage.setItem('return_to_knowledge', '1')
  router.push('/change-password')
}

/** 退出登录：清除本地登录态并返回首页 */
function logout() {
  request.post('/auth/logout').catch(() => {})
  removeToken()
  closeSidebar()
  showToast('已退出登录', 'info')
  router.push('/')
}

onUnmounted(() => {
  window.removeEventListener('resize', updateIsMobile)
  document.removeEventListener('error', handleImageError, true)
  document.removeEventListener('keydown', handleKeydown)
  document.removeEventListener('click', handleMenuOutsideClick)
  document.removeEventListener('click', handleModelOutsideClick)
  window.removeEventListener('scroll', handleModelScroll, true)
  stopSpeech()
  if (recording.value) {
    recording.value = false
    clearInterval(recordTimer)
    if (scriptProcessor) {
      scriptProcessor.disconnect()
      scriptProcessor = null
    }
    if (audioContext) {
      audioContext.close().catch(() => {})
      audioContext = null
    }
    if (mediaStream) {
      mediaStream.getTracks().forEach((track) => track.stop())
      mediaStream = null
    }
  }
})

	/** AI 回复：渲染为安全的 Markdown HTML */
	function renderMarkdown(content) {
	  if (!content) return ''
			  const renderer = new marked.Renderer()
			  renderer.link = ({ href, title, text }) => `<a target="_blank" href="${href}" title="${title || ''}">${text}</a>`
			  renderer.heading = ({ depth, text }) => `<h${depth}>${text}</h${depth}>`
			  renderer.del = ({ text }) => text // 去掉删除线，只保留文字
			  renderer.image = ({ href, title, text }) => {
		    const escapedSrc = encodeURIComponent(href)
		    const escapedAlt = text ? text.replace(/"/g, '&quot;') : ''
		    // 通过后端代理加载图片，绕过防盗链；预览由消息区 click 事件委托处理（DOMPurify 会剥离内联 onclick）
		    return `<img src="/api/image-proxy?url=${escapedSrc}" alt="${escapedAlt}" class="chat-image" loading="lazy"`
		      + ` style="width:100%;height:auto;display:block;border-radius:8px;margin:4px 0;" />`
		  }
	  const rawHtml = marked.parse(content, { renderer, gfm: true })
	  // 清洗后把裸地址（含 /api/... 根相对路径）转为可点击链接，点击自动拼接当前站点
	  return linkifyHtml(DOMPurify.sanitize(rawHtml))
	}

function scrollToBottom() {
  nextTick(() => {
    if (messagesRef.value) messagesRef.value.scrollTop = messagesRef.value.scrollHeight
  })
}

/**
 * 输入框回车处理
 * 纯 Enter 发送消息；Ctrl/Cmd + Enter 在光标处插入换行
 */
function onInputEnter(event) {
  if (event.ctrlKey || event.metaKey) {
    insertNewline(event)
  } else {
    send()
  }
}

/** 在光标位置插入换行 */
function insertNewline(event) {
  const el = event.target
  const start = el.selectionStart
  const end = el.selectionEnd
  inputText.value = inputText.value.slice(0, start) + '\n' + inputText.value.slice(end)
  nextTick(() => {
    el.selectionStart = el.selectionEnd = start + 1
  })
}

function send() {
  const text = inputText.value.trim()
  if (!text || loading.value) return
  inputText.value = ''
  messages.value.push({ role: 'user', content: text })
  scrollToBottom()
  const aiIndex = messages.value.length
  messages.value.push({ role: 'assistant', content: '', references: [], _refsCollapsed: true })
  loading.value = true

  // 创建 AbortController 用于终止请求
  const controller = new AbortController()
  abortController.value = controller

  // RAG 模式：使用带引用标注的流式接口
  if (ragEnabled.value) {
    streamKnowledgeChatRag(text, chatId.value, {
      onChunk(chunk) {
        if (chunk) {
          messages.value[aiIndex].content += chunk
        }
        scrollToBottom()
      },
      onDone(refs) {
        if (refs) {
          // 提取纯文本内容（去掉 <!--RAG_REFS--> 标记后的部分）
          messages.value[aiIndex].content = refs.displayContent
          messages.value[aiIndex].references = refs.references
        }
        loading.value = false
        abortController.value = null
        scrollToBottom()
        fetchHistoryList()
      },
      onError(err) {
        loading.value = false
        abortController.value = null
        if (err?.name === 'AbortError') return
        messages.value[aiIndex].content = '回复失败：' + (err?.message || '网络错误')
        scrollToBottom()
        fetchHistoryList()
      },
    }, controller.signal, currentModelKey.value)
  } else {
    // 普通模式：不使用 RAG
    streamKnowledgeChat(text, chatId.value, {
      onChunk(chunk) {
        if (chunk) {
          messages.value[aiIndex].content += chunk
        }
        scrollToBottom()
      },
      onDone() {
        loading.value = false
        abortController.value = null
        scrollToBottom()
        fetchHistoryList()
      },
      onError(err) {
        loading.value = false
        abortController.value = null
        if (err?.name === 'AbortError') return
        messages.value[aiIndex].content = '回复失败：' + (err?.message || '网络错误')
        scrollToBottom()
        fetchHistoryList()
      },
    }, controller.signal, currentModelKey.value)
  }
}

/** 终止 AI 回复 */
function stopStream() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
    loading.value = false
  }
}

// 语音播报
let speechAudio = null // 当前播放的 Audio 对象（非响应式）
let speechUrl = null   // 当前播放音频的 Blob URL（非响应式）

/** 停止当前语音播报并复位所有播报状态 */
function stopSpeech() {
  if (speechAudio) {
    speechAudio.pause()
    speechAudio.onended = null
    speechAudio.onerror = null
    speechAudio = null
  }
  if (speechUrl) {
    URL.revokeObjectURL(speechUrl)
    speechUrl = null
  }
  for (const m of messages.value) {
    if (m._speaking) m._speaking = false
  }
}

/** 播报/停止 AI 消息语音 */
async function toggleSpeech(msg) {
  // 点击正在播报的消息 → 停止
  if (msg._speaking) {
    stopSpeech()
    return
  }
  if (!msg.content || loading.value) return
  stopSpeech()
  msg._speaking = true
  try {
    const res = await request.post('/speech/tts', { text: msg.content }, {
      responseType: 'blob',
      timeout: 120000,
    })
    if (!msg._speaking) return // 等待期间已被停止
    const url = URL.createObjectURL(res.data)
    speechUrl = url
    const audio = new Audio(url)
    speechAudio = audio
    audio.onended = () => {
      URL.revokeObjectURL(url)
      if (speechUrl === url) speechUrl = null
      msg._speaking = false
      speechAudio = null
    }
    audio.onerror = () => {
      URL.revokeObjectURL(url)
      if (speechUrl === url) speechUrl = null
      msg._speaking = false
      speechAudio = null
      showToast('语音播报失败', 'error')
    }
    await audio.play()
  } catch (err) {
    if (speechUrl) {
      URL.revokeObjectURL(speechUrl)
      speechUrl = null
    }
    msg._speaking = false
    showToast('语音播报失败：' + (err?.message || '网络错误'), 'error')
  }
}

// 语音输入（STT：麦克风录音转文字）
const recording = ref(false)      // 是否正在录音
const transcribing = ref(false)   // 是否正在转写
const recordSeconds = ref(0)      // 录音秒数
const RECORD_MAX_SECONDS = 60     // 录音时长上限（秒）
let mediaStream = null            // 麦克风音频流
let audioContext = null           // 录音音频上下文
let scriptProcessor = null        // 录音处理器
let pcmChunks = []                // 收集的 PCM 分片
let recordTimer = null            // 录音计时器

/** 开始/停止录音，停止后转写并填入输入框 */
async function toggleRecording() {
  if (recording.value) {
    stopRecording()
    return
  }
  // 分诊提示：先判断安全上下文，再判断 API 支持度，给用户可执行的修复指引
  if (!window.isSecureContext) {
    showToast('当前是 HTTP 环境，无法使用录音，请改用 https:// 地址访问', 'error')
    return
  }
  if (!navigator.mediaDevices?.getUserMedia) {
    showToast('当前浏览器/系统内核不支持录音，请升级浏览器或系统 WebView 后重试', 'error')
    return
  }
  try {
    mediaStream = await navigator.mediaDevices.getUserMedia({ audio: true })
    audioContext = new AudioContext()
    // 确保音频上下文运行（合成点击可能不被识别为用户手势导致 suspended）
    await audioContext.resume()
    const source = audioContext.createMediaStreamSource(mediaStream)
    scriptProcessor = audioContext.createScriptProcessor(4096, 1, 1)
    pcmChunks = []
    const sourceRate = audioContext.sampleRate
    // 采集音频并降采样为 16kHz 单声道 16bit PCM
    scriptProcessor.onaudioprocess = (event) => {
      const input = event.inputBuffer.getChannelData(0)
      const targetRate = 16000
      if (sourceRate !== targetRate) {
        const ratio = sourceRate / targetRate
        const outLen = Math.floor(input.length / ratio)
        const out = new Int16Array(outLen)
        for (let i = 0; i < outLen; i++) {
          out[i] = Math.max(-1, Math.min(1, input[Math.floor(i * ratio)])) * 0x7FFF
        }
        pcmChunks.push(out)
      } else {
        const out = new Int16Array(input.length)
        for (let i = 0; i < input.length; i++) {
          out[i] = Math.max(-1, Math.min(1, input[i])) * 0x7FFF
        }
        pcmChunks.push(out)
      }
    }
    source.connect(scriptProcessor)
    // 静音节点连接扬声器保持音频图活跃（Web Audio 无输出节点的图不执行处理，采集不触发）
    // 增益为 0 不产生可听输出，因此无回声
    const silentGain = audioContext.createGain()
    silentGain.gain.value = 0
    scriptProcessor.connect(silentGain)
    silentGain.connect(audioContext.destination)
    recording.value = true
    recordSeconds.value = 0
    recordTimer = setInterval(() => {
      recordSeconds.value++
      if (recordSeconds.value >= RECORD_MAX_SECONDS) {
        stopRecording() // 超时自动停止
      }
    }, 1000)
  } catch (err) {
    const detail = err?.name === 'NotAllowedError'
      ? '请在系统设置中允许麦克风权限后重试'
      : (err?.message || '权限被拒绝')
    showToast('无法访问麦克风：' + detail, 'error')
  }
}

/** 停止录音，编码 WAV 并上传转写 */
function stopRecording() {
  if (!recording.value) return
  recording.value = false
  clearInterval(recordTimer)
  if (scriptProcessor) {
    scriptProcessor.disconnect()
    scriptProcessor.onaudioprocess = null
    scriptProcessor = null
  }
  if (audioContext) {
    audioContext.close().catch(() => {})
    audioContext = null
  }
  if (mediaStream) {
    mediaStream.getTracks().forEach((track) => track.stop())
    mediaStream = null
  }
  const totalLen = pcmChunks.reduce((sum, chunk) => sum + chunk.length, 0)
  if (totalLen === 0) {
    showToast('未录到有效音频，请重新录音', 'error')
    return
  }
  // 录音时长过短（<0.6s）无法识别，直接前端拦截，避免上传无效音频后收到 400
  if (totalLen / 16000 < 0.6) {
    showToast('录音时间过短，请按住麦克风说话至少 1 秒后再松开', 'error')
    return
  }
  const pcm = new Int16Array(totalLen)
  let offset = 0
  for (const chunk of pcmChunks) {
    pcm.set(chunk, offset)
    offset += chunk.length
  }
  uploadForRecognition(encodeWav(pcm, 16000))
}

/** PCM 数据封装为标准 WAV Blob（44 字节头 + 16bit 单声道） */
function encodeWav(pcm, sampleRate) {
  const buffer = new ArrayBuffer(44 + pcm.length * 2)
  const view = new DataView(buffer)
  const writeStr = (off, str) => {
    for (let i = 0; i < str.length; i++) view.setUint8(off + i, str.charCodeAt(i))
  }
  writeStr(0, 'RIFF')
  view.setUint32(4, 36 + pcm.length * 2, true)
  writeStr(8, 'WAVE')
  writeStr(12, 'fmt ')
  view.setUint32(16, 16, true)
  view.setUint16(20, 1, true)             // PCM 编码
  view.setUint16(22, 1, true)             // 单声道
  view.setUint32(24, sampleRate, true)
  view.setUint32(28, sampleRate * 2, true) // 字节率
  view.setUint16(32, 2, true)             // 块对齐
  view.setUint16(34, 16, true)            // 位深
  writeStr(36, 'data')
  view.setUint32(40, pcm.length * 2, true)
  for (let i = 0; i < pcm.length; i++) {
    view.setInt16(44 + i * 2, pcm[i], true)
  }
  return new Blob([buffer], { type: 'audio/wav' })
}

/** 上传 WAV 转写，结果填入输入框 */
async function uploadForRecognition(wavBlob) {
  transcribing.value = true
  try {
    const formData = new FormData()
    formData.append('file', wavBlob, 'speech.wav')
    // 覆盖实例默认的 application/json（request.js 全局设置），
    // 置空后浏览器自动生成 multipart/form-data 及 boundary
    const res = await request.post('/speech/stt', formData, {
      timeout: 90000,
      headers: { 'Content-Type': undefined },
    })
    const text = res.data?.text
    if (text) {
      inputText.value = text
      showToast('语音已转文字', 'success')
    } else {
      showToast('未检测到语音内容，请重新录制', 'error')
    }
  } catch (err) {
    showToast('语音识别失败：' + sttErrorMessage(err), 'error')
  } finally {
    transcribing.value = false
  }
}

/** 将语音识别错误转换为用户可读提示 */
function sttErrorMessage(err) {
  // 后端返回的业务提示（如"未识别到语音内容"、格式错误等）优先展示
  if (err?.response?.data?.message) return err.response.data.message
  const status = err?.response?.status
  if (status === 400) return '音频无效或未检测到语音，请重新录制'
  if (status === 413) return '录音文件过大，请缩短录音时长'
  if (status && status >= 500) return '语音识别服务异常，请稍后重试'
  if (err?.code === 'ECONNABORTED') return '语音识别超时，请重试'
  if (err?.request) return '网络异常，请检查网络后重试'
  return '语音识别失败，请重试'
}
</script>

<style scoped>
.chat-layout {
  display: flex;
  height: 100%;
  width: 100%;
  background: #f5f7fb;
  overflow: hidden;
  position: relative;
}

/* Sidebar Styles */
.sidebar {
	  width: 260px;
	  min-width: 260px;
	  background: rgba(255,255,255,0.55);
	  backdrop-filter: blur(20px);
	  -webkit-backdrop-filter: blur(20px);
	  border-right: 1px solid rgba(255,255,255,0.4);
	  border-bottom: 1px solid rgba(16,185,129,0.06);
	  box-shadow:
	    2px 0 20px rgba(16,185,129,0.06),
	    0 0 0 1px rgba(255,255,255,0.5) inset;
	  display: flex;
	  flex-direction: column;
	  transition: margin-left 0.3s ease;
	  z-index: 10;
	  overflow: hidden;
	  position: relative;
	  flex-shrink: 0;
	}
.sidebar-open {
  margin-left: 0;
}
.sidebar:not(.sidebar-open) {
  margin-left: -260px;
  border-right: none;
}

/* 移动端抽屉遮罩（点击关闭侧边栏） */
.sidebar-backdrop {
  position: absolute;
  inset: 0;
  background: rgba(15, 23, 42, 0.3);
  backdrop-filter: blur(2px);
  -webkit-backdrop-filter: blur(2px);
  z-index: 15;
  animation: fadeIn 0.2s ease;
}

.sidebar-header {
  height: 75px;
  box-sizing: border-box;
  padding: 0 20px 0 24px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  border-bottom: 1px solid #f1f5f9;
  gap: 8px;
  flex-shrink: 0;
}
.sidebar-header h3 {
  margin: 0;
  color: #1e293b;
  font-size: 1.1rem;
  white-space: nowrap;
}
.new-chat-btn {
	  display: flex;
	  align-items: center;
	  gap: 6px;
	  background: linear-gradient(135deg, #34D399, #059669);
	  color: #fff;
	  border: none;
	  padding: 7px 14px;
	  border-radius: 10px;
	  font-size: 0.9rem;
	  font-weight: 500;
	  cursor: pointer;
	  transition: all 0.2s ease;
	  box-shadow: 0 4px 12px rgba(5,150,105,0.3);
	}
	.new-chat-btn:hover {
	  opacity: 0.95;
	  box-shadow: 0 6px 20px rgba(5,150,105,0.4);
	  transform: translateY(-1px);
	}
	.new-chat-btn:active {
	  transform: translateY(0);
	  box-shadow: 0 2px 8px rgba(5,150,105,0.3);
	}
.new-chat-btn .icon {
  width: 16px;
  height: 16px;
}

/* 侧边栏边缘切换按钮 */
.toggle-sidebar-btn {
	  position: absolute;
	  top: 50%;
	  left: 260px;
	  /* 按钮整体置于侧边栏右缘外侧（不居中），避免遮挡列表滚动条 */
	  transform: translateY(-50%);
	  z-index: 50;
	  display: flex;
	  align-items: center;
	  justify-content: center;
	  width: 28px;
	  height: 52px;
	  background: rgba(255,255,255,0.85);
	  backdrop-filter: blur(8px);
	  -webkit-backdrop-filter: blur(8px);
	  border: 1px solid rgba(255,255,255,0.3);
	  border-radius: 0 10px 10px 0;
	  color: #10B981;
	  cursor: pointer;
	  box-shadow: 2px 0 8px rgba(16,185,129,0.08);
	  transition: color 0.2s, box-shadow 0.2s, left 0.3s ease;
	}
	.toggle-sidebar-btn:hover {
	  color: #059669;
	  box-shadow: 2px 0 16px rgba(16,185,129,0.15);
	}
.toggle-sidebar-btn .icon {
  width: 18px;
  height: 18px;
}
.toggle-sidebar-btn.collapsed {
  left: 0;
  transform: translateY(-50%);
  border-radius: 0 8px 8px 0;
  border-left: none;
}

.history-list {
  flex-grow: 1;
  overflow-y: scroll;
  overflow-x: hidden;
  /* 滚动条吸附在历史对话最右侧边框；收起按钮在右缘外侧，互不重叠 */
  padding: 10px 10px 10px 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.history-list::-webkit-scrollbar {
  width: 5px;
}
.history-list::-webkit-scrollbar-track {
  background: transparent;
}
.history-list::-webkit-scrollbar-thumb {
  background: #e2e8f0;
  border-radius: 3px;
}
.history-item {
  padding: 0;
  border-radius: 10px;
  background: transparent;
  transition: background 0.2s;
  box-sizing: border-box;
  position: relative;
}
.history-item:hover {
  background: #f8fafc;
}
.history-item.active {
	  background: rgba(52, 211, 153, 0.06);
	}
.history-item-main {
  cursor: pointer;
  padding: 12px 72px 12px 16px;
}
.history-title {
  color: #1e293b;
  font-size: 0.95rem;
  margin-bottom: 4px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.history-time {
  color: #94a3b8;
  font-size: 0.8rem;
}
.history-empty {
  padding: 30px;
  text-align: center;
  color: #94a3b8;
  font-size: 0.9rem;
}

/* 历史项三点按钮与悬浮菜单 */
.history-more-wrap {
  position: absolute;
  right: 8px;
  top: 6px;
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
  transition: background 0.2s, color 0.2s;
}
.history-more-btn:hover {
  background: rgba(16,185,129,0.1);
  color: #10B981;
}
.history-more-btn .icon {
  width: 18px;
  height: 18px;
}
/* 三点菜单（Teleport 到 body，fixed 定位——脱离历史列表滚动容器，不被任何元素裁剪/遮挡） */
.history-menu-fixed {
  position: fixed;
  width: 160px;
  background: rgba(255,255,255,0.97);
  border-radius: 12px;
  border: 1px solid rgba(16,185,129,0.1);
  box-shadow: 0 12px 32px rgba(15,23,42,0.18);
  z-index: 3000;
  overflow: hidden;
  animation: fadeIn 0.15s ease;
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
  transition: background 0.15s;
  text-align: left;
}
.history-menu-item:hover {
  background: #f8fafc;
}
.history-menu-item.danger {
  color: #ef4444;
}
.history-menu-item.danger:hover {
  background: rgba(239,68,68,0.06);
}
.menu-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

/* 批量管理模式：勾选圆圈与底部按钮条 */
.history-item.batch-mode {
  cursor: pointer;
}
.history-item-main.batch-select {
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
.batch-circle {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  border: 2px solid #cbd5e1;
  background: transparent;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: all 0.15s ease;
  box-sizing: border-box;
}
.batch-circle.selected {
  background: #111111;
  border-color: #111111;
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
  background: rgba(245,247,250,0.75);
  border-top: 1px solid rgba(16,185,129,0.08);
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
  transition: all 0.2s ease;
  box-shadow: 0 1px 4px rgba(0,0,0,0.06);
}
.batch-bar-btn:hover {
  background: #f1f5f9;
}
.batch-bar-btn.delete {
  color: #ef4444;
}
.batch-bar-btn.delete:hover:not(:disabled) {
  background: rgba(239,68,68,0.08);
}
.batch-bar-btn.delete:disabled {
  color: #d1d5db;
  cursor: not-allowed;
  background: #f8fafc;
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
  transition: border-color 0.2s, box-shadow 0.2s;
}
.edit-title-input:focus {
  border-color: #10B981;
  box-shadow: 0 0 0 3px rgba(16,185,129,0.12);
}

/* 头像多彩动效 */
@keyframes hueCycle {
  from { filter: hue-rotate(0deg); }
  to { filter: hue-rotate(360deg); }
}
.chat-avatar.user {
  background: transparent;
}
.chat-user-letter {
	  font-family: 'Space Grotesk', 'Plus Jakarta Sans', sans-serif;
	  font-size: 1rem;
	  font-weight: 700;
	  color: #10B981;
	  text-shadow: 0 0 8px rgba(16,185,129,0.2);
	}

/* AI 机器人头像 */
.robot-avatar {
  width: 40px;
  height: 40px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.robot-svg {
  width: 40px;
  height: 40px;
  overflow: visible;
}
.antenna-dot {
  animation: dotPulse 2s ease-in-out infinite;
}
@keyframes dotPulse {
  0%, 100% { opacity: 1; r: 2.5; }
  50% { opacity: 0.4; r: 2; }
}
.eye {
  animation: eyeBlink 4s ease-in-out infinite;
}
.eye-left { animation-delay: 0s; }
.eye-right { animation-delay: 0.08s; }
@keyframes eyeBlink {
  0%, 96%, 100% { opacity: 1; ry: 3.5; }
  98% { opacity: 0.3; ry: 0.5; }
}
.mouth {
  animation: mouthMove 3s ease-in-out infinite;
}
@keyframes mouthMove {
  0%, 100% { x1: 18; x2: 30; }
  50% { x1: 20; x2: 28; }
}
.body-dot {
  animation: bodyBreathe 3s ease-in-out infinite;
}
@keyframes bodyBreathe {
  0%, 100% { opacity: 0.6; r: 1.5; }
  50% { opacity: 1; r: 2; }
}
.chat-avatar.assistant {
  animation: float 4s ease-in-out infinite;
}
@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-3px); }
}

/* 删除确认弹窗 - 玻璃模态 */
.modal-overlay {
	  position: fixed;
	  inset: 0;
	  background: rgba(16,185,129,0.08);
	  backdrop-filter: blur(4px);
	  -webkit-backdrop-filter: blur(4px);
	  display: flex;
	  align-items: center;
	  justify-content: center;
	  z-index: 1000;
	  animation: fadeIn 0.2s ease;
	}
@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}
.modal-content {
	  width: 340px;
	  padding: 2rem;
	  border-radius: 20px;
	  background: rgba(255,255,255,0.9);
	  backdrop-filter: blur(20px);
	  -webkit-backdrop-filter: blur(20px);
	  border: 1px solid rgba(255,255,255,0.3);
	  box-shadow: 0 16px 48px rgba(16,185,129,0.12);
	  text-align: center;
	  animation: modalSlideUp 0.25s ease;
	}
@keyframes modalSlideUp {
  from { transform: translateY(20px); opacity: 0; }
  to { transform: translateY(0); opacity: 1; }
}
.modal-icon {
  width: 48px;
  height: 48px;
  margin: 0 auto 1rem;
  background: rgba(239,68,68,0.1);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #ef4444;
}
.modal-icon .icon {
  width: 24px;
  height: 24px;
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
  box-shadow: 0 4px 12px rgba(239,68,68,0.3);
}
.modal-btn.confirm:hover {
  background: #dc2626;
  box-shadow: 0 6px 20px rgba(239,68,68,0.4);
  transform: translateY(-1px);
}
/* 标题/正文左对齐（修改标题、批量删除确认弹窗使用） */
.modal-title.left,
.modal-desc.left {
  text-align: left;
}
/* 修改标题弹窗的确定按钮：绿色主色 */
.modal-btn.confirm.green {
  background: linear-gradient(135deg, #34D399, #059669);
  box-shadow: 0 4px 12px rgba(5,150,105,0.3);
}
.modal-btn.confirm.green:hover {
  background: linear-gradient(135deg, #10B981, #047857);
  box-shadow: 0 6px 20px rgba(5,150,105,0.4);
  transform: translateY(-1px);
}

/* Main Chat Container Styles */
.chat-container {
  flex-grow: 1;
  display: flex;
  flex-direction: column;
  height: 100%;
  position: relative;
  background: #f5f7fb;
  min-height: 0;
  /* 允许收缩到容器宽度以内（防止输入区/内容把容器撑宽导致右侧被裁切） */
  min-width: 0;
}

.header {
	  flex-shrink: 0;
	  height: 60px;
	  padding: 0 20px;
	  display: flex;
	  align-items: center;
	  justify-content: space-between;
	  background: rgba(255,255,255,0.6);
	  backdrop-filter: blur(16px);
	  -webkit-backdrop-filter: blur(16px);
	  border-bottom: 1px solid rgba(255,255,255,0.3);
	  box-shadow:
	    0 1px 4px rgba(16,185,129,0.06),
	    0 0 0 1px rgba(255,255,255,0.4) inset;
	}
.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
  flex-shrink: 1;
}

/* 移动端侧边栏开关按钮（桌面隐藏，移动端显示） */
.sidebar-menu-btn {
  display: none;
  align-items: center;
  justify-content: center;
  width: 40px;
  height: 40px;
  flex-shrink: 0;
  border: none;
  border-radius: 12px;
  background: rgba(16,185,129,0.08);
  color: #10B981;
  cursor: pointer;
  transition: background 0.2s, color 0.2s;
}
.sidebar-menu-btn:hover {
  background: rgba(16,185,129,0.15);
  color: #059669;
}
.sidebar-menu-btn .icon {
  width: 20px;
  height: 20px;
}
.header-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.back-btn {
	  display: flex;
	  align-items: center;
	  justify-content: center;
	  gap: 4px;
	  background: rgba(16,185,129,0.08);
	  border: 1px solid rgba(255,255,255,0.3);
	  color: #10B981;
	  height: 40px;
	  padding: 0 16px;
	  box-sizing: border-box;
	  border-radius: 12px;
	  cursor: pointer;
	  font-size: 0.85rem;
	  font-weight: 500;
	  white-space: nowrap;
	  flex-shrink: 0;
	  transition: background 0.2s, color 0.2s, box-shadow 0.2s;
	}
	.back-btn .icon {
	  width: 16px;
	  height: 16px;
	}
	.back-btn:hover {
	  background: rgba(16,185,129,0.15);
	  color: #059669;
	  box-shadow: 0 2px 8px rgba(16,185,129,0.1);
	}
.header-title {
  font-size: 1.1rem;
  margin: 0;
  color: #1e293b;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  min-width: 0;
}
.chat-id-display {
  font-size: 0.75rem;
  color: #94a3b8;
  display: none;
}
.messages {
  flex: 1;
  overflow-y: scroll;
  overflow-x: hidden;
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}
.messages::-webkit-scrollbar {
  width: 5px;
}
.messages::-webkit-scrollbar-track {
  background: transparent;
}
.messages::-webkit-scrollbar-thumb {
  background: #e2e8f0;
  border-radius: 3px;
}

/* 消息入场动效 */
@keyframes messageIn {
  from { opacity: 0; transform: translateY(12px) scale(0.98); }
  to   { opacity: 1; transform: translateY(0) scale(1); }
}
.message-row {
  animation: messageIn 0.3s ease both;
}

/* 空状态欢迎语 */
.welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
  padding: 2rem;
  color: #64748b;
  animation: welcomeFadeIn 0.6s ease both;
}
@keyframes welcomeFadeIn {
  from { opacity: 0; transform: translateY(16px); }
  to { opacity: 1; transform: translateY(0); }
}
.welcome-icon-wrap {
  width: 72px;
  height: 72px;
  margin-bottom: 1.25rem;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 20px;
  background: rgba(16,185,129,0.06);
  border: 1px solid rgba(16,185,129,0.1);
}
.welcome-icon {
  width: 48px;
  height: 48px;
}
.welcome h3 {
  font-size: 1.25rem;
  color: #065F46;
  margin-bottom: 0.5rem;
  font-weight: 600;
}
.welcome-desc {
  font-size: 0.9rem;
  line-height: 1.6;
  max-width: 300px;
  color: #94a3b8;
  margin-bottom: 1.5rem;
}
.welcome-tips {
  display: flex;
  gap: 0.75rem;
  flex-wrap: wrap;
  justify-content: center;
}
.tip-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  padding: 5px 12px;
  border-radius: 999px;
  font-size: 0.8rem;
  color: #64748b;
  background: rgba(255,255,255,0.5);
  border: 1px solid rgba(255,255,255,0.3);
  backdrop-filter: blur(8px);
}
.tip-icon {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
}

/* 打字指示器 */
.typing-indicator {
  display: flex;
  align-items: center;
  gap: 5px;
  padding: 0.5rem 0;
  margin-left: 3rem;
}
.typing-dot {
	  width: 8px;
	  height: 8px;
	  border-radius: 50%;
	  background: #6EE7B7;
	  animation: typingBounce 1.4s ease-in-out infinite both;
	}
	.typing-dot:nth-child(1) { animation-delay: 0s; }
	.typing-dot:nth-child(2) { animation-delay: 0.2s; }
	.typing-dot:nth-child(3) { animation-delay: 0.4s; }
	@keyframes typingBounce {
	  0%, 80%, 100% { transform: translateY(0); background: #6EE7B7; }
	  40% { transform: translateY(-8px); background: #10B981; }
	}

.message-row {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  max-width: 92%;
}
.message-row.assistant {
  align-self: flex-start;
}
.message-row.user {
  align-self: flex-end;
  flex-direction: row-reverse;
}
.chat-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 0.9rem;
  font-weight: 600;
  color: #fff;
  background: transparent;
  margin-top: 10px;
}
.bubble-content {
  padding: 1rem 1.5rem;
  border-radius: 14px;
  white-space: normal;
  word-break: break-word;
  overflow-wrap: anywhere;
  text-align: left;
  line-height: 1.8;
  min-width: 0;
}
.streaming-text {
  white-space: pre-wrap;
  line-height: 1.8;
}
.message-row.user .bubble-content {
	  width: fit-content;
	  max-width: min(85%, 65ch);
	  background: linear-gradient(135deg, #34D399, #10B981);
	  color: #fff;
	}
.message-row.assistant .bubble-content {
  min-width: 12em;
  max-width: min(85%, 65ch);
  background: #ffffff;
  border: 1px solid #e2e8f0;
  color: #1e293b;
  box-shadow: 0 1px 4px rgba(0,0,0,0.04);
}
/* AI 回复语音播报按钮 + 知识库引用区域（顶部分割线） */
.speech-refs-area {
  margin-top: 16px;
  border-top: 1px solid rgba(245, 158, 11, 0.12);
  padding-top: 12px;
}
/* 语音播报按钮：仅图标，无边框无底色；hover 变深、播报中转红（表示可停止） */
.speech-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 6px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: #10B981;
  cursor: pointer;
  transition: color 0.2s ease;
}
.speech-btn:hover {
  color: #047857;
}
.speech-btn.speaking {
  color: #EF4444;
}
/* 麦克风录音按钮 */
.mic-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 44px;
  height: 44px;
  border-radius: 50%;
  border: 1px solid rgba(16, 185, 129, 0.3);
  background: rgba(16, 185, 129, 0.08);
  color: #10B981;
  cursor: pointer;
  transition: all 0.2s ease;
  flex-shrink: 0;
}
.mic-btn:hover {
  background: rgba(16, 185, 129, 0.15);
  border-color: rgba(16, 185, 129, 0.5);
}
.mic-btn.recording {
  background: #EF4444;
  border-color: #EF4444;
  color: #fff;
  animation: mic-pulse 1.2s ease-in-out infinite;
}
.mic-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.mic-spinner {
  width: 16px;
  height: 16px;
  border: 2px solid rgba(16, 185, 129, 0.3);
  border-top-color: #10B981;
  border-radius: 50%;
  animation: mic-rotate 0.8s linear infinite;
}
@keyframes mic-pulse {
  0%, 100% { box-shadow: 0 0 0 0 rgba(239, 68, 68, 0.5); }
  50% { box-shadow: 0 0 0 8px rgba(239, 68, 68, 0); }
}
@keyframes mic-rotate {
  to { transform: rotate(360deg); }
}
/* Enhanced Markdown styles for light theme */
.markdown-body :deep(p) {
  margin-bottom: 0.9em;
  line-height: 1.8;
}
.markdown-body :deep(p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(ol) {
  margin-bottom: 0.8em;
  padding-left: 1.8em;
  list-style: none;
  counter-reset: li-counter;
}
.markdown-body :deep(ol > li) {
  counter-increment: li-counter;
  position: relative;
  padding-left: 0.5em;
  margin-bottom: 0.6em;
  line-height: 1.7;
}
.markdown-body :deep(ol > li::before) {
	  content: counter(li-counter) ".";
	  position: absolute;
	  left: -1.8em;
	  width: 1.5em;
	  text-align: right;
	  color: #10B981;
	  font-weight: 600;
	}
.markdown-body :deep(ul) {
  margin-bottom: 0.8em;
  padding-left: 1.5em;
}
.markdown-body :deep(li) {
  margin-bottom: 0.5em;
  line-height: 1.7;
}
.markdown-body :deep(ul > li) {
  list-style: none;
  position: relative;
  padding-left: 0.5em;
}
.markdown-body :deep(ul > li::before) {
	  content: '';
	  position: absolute;
	  left: -1.2em;
	  top: 0.65em;
	  width: 6px;
	  height: 6px;
	  border-radius: 50%;
	  background: #6EE7B7;
	}
.markdown-body :deep(ul ul), .markdown-body :deep(ol ul),
.markdown-body :deep(ul ol), .markdown-body :deep(ol ol) {
  margin-top: 0.4em;
  margin-bottom: 0.4em;
}
.markdown-body :deep(h1), .markdown-body :deep(h2), .markdown-body :deep(h3),
.markdown-body :deep(h4), .markdown-body :deep(h5), .markdown-body :deep(h6) {
  margin-top: 1.4em;
  margin-bottom: 0.6em;
  font-weight: 600;
  line-height: 1.4;
  color: #0f172a;
}
.markdown-body :deep(h3) {
	  padding-left: 12px;
	  border-left: 3px solid #10B981;
	}
	.markdown-body :deep(h4) {
	  color: #059669;
	  font-size: 1em;
	}
.markdown-body :deep(h1:first-child), .markdown-body :deep(h2:first-child),
.markdown-body :deep(h3:first-child) {
  margin-top: 0;
}
.markdown-body :deep(strong) {
  color: #0f172a;
  font-weight: 600;
}
.markdown-body :deep(blockquote) {
	  margin: 1em 0;
	  padding: 0.8em 1.2em;
	  border-left: 3px solid #6EE7B7;
	  background: rgba(16,185,129,0.04);
	  border-radius: 0 8px 8px 0;
	  color: #475569;
	  line-height: 1.7;
	}
.markdown-body :deep(blockquote p) {
  margin-bottom: 0.4em;
}
.markdown-body :deep(blockquote p:last-child) {
  margin-bottom: 0;
}
.markdown-body :deep(hr) {
	  margin: 1.2em 0;
	  border: none;
	  height: 1px;
	  background: linear-gradient(90deg, transparent, #A7F3D0, transparent);
	}
.markdown-body :deep(code) {
	  background: #f1f5f9;
	  padding: 0.2em 0.4em;
	  border-radius: 4px;
	  font-family: 'Cascadia Code', 'Fira Code', monospace;
	  font-size: 0.9em;
	  color: #10B981;
	}
.markdown-body :deep(pre) {
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 1em;
  border-radius: 10px;
  overflow-x: auto;
  margin-bottom: 0.8em;
}
.markdown-body :deep(pre code) {
  background: none;
  padding: 0;
  color: #1e293b;
}
.markdown-body :deep(a) {
	  color: #10B981;
	  text-decoration: underline;
	}
/* Markdown 表格：超出宽度时气泡内横向滚动，不撑破布局 */
.markdown-body :deep(table) {
  display: block;
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  border-collapse: collapse;
  margin-bottom: 0.8em;
  font-size: 0.9em;
  line-height: 1.5;
}
.markdown-body :deep(th),
.markdown-body :deep(td) {
  border: 1px solid #e2e8f0;
  padding: 0.4em 0.6em;
  text-align: left;
  white-space: normal;
  word-break: break-word;
}
.markdown-body :deep(th) {
  background: rgba(16, 185, 129, 0.06);
  font-weight: 600;
  color: #065F46;
}
.input-area {
  flex-shrink: 0;
  padding: 0.8rem 1rem;
  border-top: 1px solid rgba(255,255,255,0.3);
  display: flex;
  gap: 0.75rem;
  align-items: center;
  background: rgba(255,255,255,0.6);
  backdrop-filter: blur(16px);
  -webkit-backdrop-filter: blur(16px);
  box-shadow:
	    0 -4px 16px rgba(16,185,129,0.04),
	    0 0 0 1px rgba(255,255,255,0.4) inset;
	  position: sticky;
	  bottom: 0;
	  z-index: 10;
	}
/* 输入框容器：输入框在上、底部工具条在下，两者共用一个边框，聚焦时整框高亮 */
.input-composer {
  height: 100%;
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  /* 上内边距放在容器上（不在 textarea 的滚动区内）：文字超出上限内部滚动时顶部留白不会被顶掉 */
  padding-top: 0.5rem;
  border-radius: 14px;
  border: 1px solid #e2e8f0;
  background: #f8fafc;
  transition: border-color 0.2s, box-shadow 0.2s;
}
.input-composer:focus-within {
  border-color: #34D399;
  box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.1);
}
.input-composer textarea {
  width: 100%;
  min-height: 32px;
  max-height: 32px;
  padding: 0 1.2rem 0.05rem;
  border: none;
  border-radius: 14px 14px 0 0;
  background: transparent;
  color: #1e293b;
  resize: none;
  font-size: 0.9rem;
}
.input-composer textarea:focus {
  outline: none;
}
.send-btn {
	  display: flex;
	  align-items: center;
	  gap: 6px;
	  background: linear-gradient(135deg, #34D399, #10B981);
	  border: none;
	  color: #fff;
	  padding: 0 28px;
	  border-radius: 12px;
	  cursor: pointer;
	  font-weight: 600;
	  height: 44px;
	  letter-spacing: 0.02em;
	  transition: all 0.2s ease;
	  box-shadow: 0 4px 14px rgba(52,211,153,0.3);
	}
.send-btn:hover:not(:disabled) {
	  opacity: 0.95;
	  box-shadow: 0 6px 24px rgba(52,211,153,0.4);
	  transform: translateY(-1px);
	}
	.send-btn:active:not(:disabled) {
	  transform: translateY(0);
	  box-shadow: 0 2px 8px rgba(52,211,153,0.3);
	}
.send-btn:disabled {
  background: #cbd5e1;
  cursor: not-allowed;
  opacity: 0.7;
  box-shadow: none;
}
.send-btn.stop-btn {
  background: #ef4444;
  box-shadow: 0 4px 14px rgba(239,68,68,0.3);
  display: flex;
  align-items: center;
  gap: 6px;
}
.send-btn.stop-btn:hover {
  background: #dc2626;
  box-shadow: 0 6px 24px rgba(239,68,68,0.4);
  transform: translateY(-1px);
}
.btn-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
}

@media (max-width: 768px) {
  .sidebar {
    position: absolute;
    left: 0;
    top: 0;
    bottom: 0;
    width: 260px;
    margin-left: 0;
    transform: translateX(-100%);
    transition: transform 0.3s ease;
    border-right: 1px solid #e2e8f0;
    /* 移动端统一为灰色半透明底，保证与床头卡片底色一致 */
    background: rgba(245, 247, 250, 0.75);
    box-shadow: none;
    z-index: 20;
  }
  /* 批量条与个人信息模块同高，批量模式切换时历史列表不跳动 */
  .batch-bar {
    min-height: 168px;
  }
  .sidebar.sidebar-open {
    transform: translateX(0);
    width: 260px;
    box-shadow: 5px 0 30px rgba(0,0,0,0.1);
  }
  .sidebar:not(.sidebar-open) {
    margin-left: 0;
    border-right: none;
  }
  .toggle-sidebar-btn {
    display: none;
  }
  .chat-id-display {
    display: none;
  }
  /* 移动端头部：显示侧边栏开关，返回按钮紧凑 */
  .sidebar-menu-btn {
    display: flex;
  }
  .back-btn {
    padding: 0 12px;
    height: 40px;
  }
  /* 移动端发送按钮：仅图标，节省横向空间 */
  .send-btn {
    padding: 0;
    width: 44px;
    justify-content: center;
  }
  /* 移动端播报按钮：图标不变，点击区补到 44px（16px 图标 + 14px×2） */
  .speech-btn {
    padding: 14px;
  }
  .btn-text {
    display: none;
  }
  /* 移动端消息区与输入区适配 */
  .messages {
    padding: 1rem 0.75rem;
    gap: 1rem;
  }
  .message-row {
    max-width: 96%;
  }
  /* 移动端输入区：整个输入区就是一个框，四个控件全在框内——左下角 RAG + 模型并列，右下角语音/发送 */
  .input-area {
    flex-wrap: wrap;
    align-items: center;
    gap: 6px;
    margin: 0.6rem;
    padding: 0.25rem 0.45rem calc(0.25rem + env(safe-area-inset-bottom));
    border: 1px solid #e2e8f0;
    border-radius: 16px;
    background: #f8fafc;
    backdrop-filter: none;
    -webkit-backdrop-filter: none;
    box-shadow: none;
    transition: border-color 0.2s, box-shadow 0.2s;
  }
  .input-area:focus-within {
    border-color: #34D399;
    box-shadow: 0 0 0 3px rgba(52, 211, 153, 0.1);
  }
  /* 解开输入框容器：输入框与工具条直接参与输入区的排布（框由输入区来画） */
  .input-composer {
    display: contents;
  }
  .input-composer textarea {
    flex: 1 1 100%;
    border: none;
    background: transparent;
    /* 顶部留白改用外边距：不属于 textarea 的滚动区，文字超长内部滚动时不会被顶掉 */
    margin-top: 0.5rem;
    padding: 0 0.45rem 0;
  }
  .input-composer textarea:focus {
    box-shadow: none;
  }
  /* 左下角：RAG 与模型切换并列（basis 0 让它在换行计算时不吃掉整行，内部放不下则省略模型名） */
  .input-area .input-toolbar {
    flex: 1 1 0;
    min-width: 0;
    flex-direction: row;
    align-items: center;
    justify-content: flex-start;
    gap: 6px;
    padding: 0 0 0.1rem 0.35rem;
  }
  .input-area .rag-label-full {
    display: none;
  }
  .input-area .rag-label-short {
    display: inline;
  }
  /* 模型按钮撑满收缩后的容器，模型名超长时省略（否则按钮保持自然宽度会溢出、压到语音按钮） */
  .input-area .model-trigger {
    width: 100%;
  }
  /* 右下角：语音/发送去边框、与输入框同底色 */
  .input-area .mic-btn,
  .input-area .send-btn {
    width: 34px;
    height: 34px;
    min-width: 34px;
    padding: 0;
    border: none;
    border-radius: 10px;
    background: transparent;
    box-shadow: none;
  }
  .input-area .mic-btn {
    color: #10B981;
  }
  .input-area .send-btn {
    color: #10B981;
  }
  .input-area .mic-btn:hover,
  .input-area .send-btn:hover:not(:disabled),
  .input-area .send-btn:active:not(:disabled) {
    background: rgba(16, 185, 129, 0.1);
    transform: none;
    box-shadow: none;
  }
  .input-area .send-btn:disabled {
    background: transparent;
    color: #cbd5e1;
    opacity: 1;
  }
  /* 录音与终止是瞬时状态，保留实色底 + 白图标以便一眼分辨 */
  .input-area .mic-btn.recording {
    background: #EF4444;
    color: #FFFFFF;
  }
  .input-area .send-btn.stop-btn {
    background: #EF4444;
    color: #FFFFFF;
  }
  /* iOS 聚焦输入框不自动放大（<16px 会触发） */
  .input-area textarea {
    font-size: 1rem;
  }
}


/* 图片预览弹窗（背景半透明黑 + 虚化） */
.image-preview-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.5);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2000;
  animation: fadeIn 0.2s ease;
  cursor: zoom-out;
}
.image-preview-img {
  max-width: 90vw;
  max-height: 90vh;
  object-fit: contain;
  border-radius: 8px;
  box-shadow: 0 8px 40px rgba(0,0,0,0.4);
  animation: modalSlideUp 0.25s ease;
  cursor: default;
}
.image-preview-close {
  position: fixed;
  top: 16px;
  right: 16px;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: rgba(255,255,255,0.15);
  backdrop-filter: blur(8px);
  border: 1px solid rgba(255,255,255,0.2);
  color: #fff;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.2s;
  z-index: 2001;
}
.image-preview-close:hover {
  background: rgba(255,255,255,0.25);
}
.image-preview-close svg {
  width: 22px;
  height: 22px;
}

/* ==================== 移动端侧边栏底部个人信息卡片 ==================== */
.sidebar-footer {
  flex-shrink: 0;
  padding: 12px 12px 16px;
  border-top: 1px solid rgba(16,185,129,0.08);
}
.profile-card {
  /* 完全透明：底色与侧边栏100%一致，避免半透明叠加偏白；靠边框+阴影+圆角区分区域 */
  background: transparent;
  border: 1px solid rgba(16,185,129,0.3);
  border-radius: 20px;
  box-shadow: 0 4px 16px rgba(16,185,129,0.1);
  padding: 16px;
  display: flex;
  flex-direction: column;
  gap: 14px;
}
.profile-info {
  display: flex;
  align-items: center;
  gap: 12px;
}
.profile-avatar {
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  /* 与主页头像一致：淡绿底 + 绿色渐变字母（无动效版） */
  background: #ECFDF5;
  box-shadow: 0 2px 8px rgba(16,185,129,0.2);
}
.profile-avatar-letter {
  font-family: 'Space Grotesk', 'Plus Jakarta Sans', sans-serif;
  font-size: 1.4rem;
  font-weight: 700;
  background: linear-gradient(135deg, #10B981, #34D399, #6EE7B7);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
  user-select: none;
}
.profile-name {
  font-size: 1rem;
  font-weight: 600;
  color: #1e293b;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.profile-actions {
  display: flex;
  gap: 10px;
}
.profile-action-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  min-height: 44px;
  border-radius: 12px;
  border: 1px solid rgba(16,185,129,0.18);
  background: rgba(16,185,129,0.05);
  color: #065F46;
  font-size: 0.85rem;
  font-weight: 500;
  cursor: pointer;
  transition: all 0.2s ease;
}
.profile-action-btn:hover {
  background: rgba(16,185,129,0.12);
  border-color: rgba(16,185,129,0.3);
}
.profile-action-btn:active {
  transform: scale(0.98);
}
.profile-action-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: #10B981;
}

/* ==================== 大模型切换 ==================== */
.model-select {
  /* 与 RAG 开关同处一行：空间不足时收缩并省略模型名，不换行 */
  flex: 0 1 auto;
  min-width: 0;
  margin-left: auto;
}
.model-trigger {
  display: flex;
  align-items: center;
  gap: 6px;
  min-height: 28px;
  padding: 4px 10px 4px 8px;
  border-radius: 8px;
  border: 1px;
  background: #f8fafc;
  color: #065F46;
  font-family: inherit;
  font-size: 0.82rem;
  cursor: pointer;
  transition: background 0.2s ease;
}
.model-trigger:hover {
  background: rgba(16, 185, 129, 0.1);
}
.model-trigger:focus-visible {
  outline: 2px solid #10B981;
  outline-offset: 2px;
}
.model-trigger.open {
  background: rgba(16, 185, 129, 0.12);
}
.model-trigger-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: #10B981;
}
.model-trigger-text {
  min-width: 0;
  max-width: 150px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.model-trigger-caret {
  width: 14px;
  height: 14px;
  flex-shrink: 0;
  opacity: 0.7;
  transition: transform 0.2s ease;
}
.model-trigger.open .model-trigger-caret {
  transform: rotate(180deg);
}

/* 模型面板：Teleport 到 body，fixed 定位 + 玻璃拟态 */
.model-menu-fixed {
  position: fixed;
  z-index: 3000;
  display: flex;
  flex-direction: column;
  gap: 2px;
  padding: 6px;
  border-radius: 14px;
  /* 面板过高时内部滚动，避免选项落到视口外 */
  max-height: calc(100vh - 24px);
  overflow-y: auto;
  background: rgba(255, 255, 255, 0.94);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(200, 255, 255, 0.5);
  box-shadow: 0 12px 32px rgba(16, 185, 129, 0.16);
}
.model-menu-empty {
  padding: 12px;
  color: #64748b;
  font-size: 0.85rem;
  white-space: nowrap;
}
.model-option {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  width: 100%;
  min-height: 44px;
  padding: 6px 12px;
  border: none;
  border-radius: 10px;
  background: transparent;
  color: #334155;
  font-family: inherit;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
.model-option:hover:not(.unavailable),
.model-option.highlight:not(.unavailable) {
  background: rgba(16, 185, 129, 0.08);
  color: #047857;
}
.model-option.active {
  color: #047857;
  font-weight: 600;
}
.model-option.unavailable {
  color: #94a3b8;
  cursor: not-allowed;
}
.model-option-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.model-option-name {
  font-size: 0.88rem;
  white-space: nowrap;
}
.model-option-meta {
  font-size: 0.75rem;
  color: #94a3b8;
  white-space: nowrap;
}
.model-option-check {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: #10b981;
}

.model-menu-enter-active,
.model-menu-leave-active {
  transition: opacity 0.15s ease, transform 0.15s ease;
}
.model-menu-enter-from,
.model-menu-leave-to {
  opacity: 0;
  transform: translateY(4px);
}
@media (prefers-reduced-motion: reduce) {
  .model-menu-enter-active,
  .model-menu-leave-active,
  .model-trigger,
  .model-trigger-caret {
    transition: none;
  }
}

/* ==================== 输入框底部工具条（左：RAG 开关，右：大模型切换） ==================== */
.input-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  /* 输入框比内容高时（见 .input-composer 的高度），工具条贴住框底 */
  margin-top: auto;
  padding: 0 0.5rem 0.35rem;
}
.rag-toggle {
	  min-height: 28px;
	  flex-shrink: 0;
	  display: flex;
	  align-items: center;
	  gap: 8px;
	  cursor: pointer;
	  user-select: none;
	  font-size: 0.82rem;
	  color: #065F46;
	  padding: 4px 10px 4px 8px;
	  border-radius: 8px;
	  background: #f8fafc;
	  transition: all 0.2s ease;
	}
	.rag-toggle:hover {
	  background: rgba(16,185,129,0.1);
	}
	.rag-toggle-icon {
	  width: 20px;
	  height: 16px;
	  flex-shrink: 0;
	  color: #10B981;
	}
	/* 移动端短标签 RAG：桌面端隐藏 */
	.rag-label-short {
	  display: none;
	}
	.toggle-switch {
	  width: 32px;
	  height: 18px;
	  border-radius: 999px;
	  background: #cbd5e1;
	  position: relative;
	  transition: background 0.25s ease;
	  flex-shrink: 0;
	}
	.toggle-switch.active {
	  background: #10B981;
	}
.toggle-knob {
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  position: absolute;
  top: 2px;
  left: 2px;
  transition: transform 0.25s ease;
  box-shadow: 0 1px 3px rgba(0,0,0,0.15);
}
.toggle-switch.active .toggle-knob {
  transform: translateX(14px);
}

/* ==================== RAG 引用切片展示 ==================== */
.rag-references {
  margin-top: 12px;
}
.rag-refs-header {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 0.82rem;
  color: #10B981;
  font-weight: 500;
  margin-bottom: 8px;
}
.refs-icon {
  width: 16px;
  height: 16px;
  flex-shrink: 0;
  color: #F59E0B;
}
.refs-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: rgba(245,158,11,0.12);
  color: #D97706;
  font-size: 0.7rem;
  font-weight: 700;
  margin-left: 2px;
}
.refs-toggle {
  margin-left: auto;
  background: none;
  border: none;
  cursor: pointer;
  color: #94a3b8;
  padding: 2px;
  display: flex;
  align-items: center;
  transition: color 0.2s;
}
.refs-toggle:hover {
  color: #10B981;
}
.toggle-icon {
  width: 18px;
  height: 18px;
  transition: transform 0.25s ease;
}
.toggle-icon.rotated {
  transform: rotate(180deg);
}
.rag-refs-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.rag-ref-item {
  display: flex;
  gap: 12px;
  padding: 12px 14px;
  background: rgba(255,255,255,0.5);
  border: 1px solid rgba(16,185,129,0.08);
  border-left: 3px solid #34D399;
  border-radius: 12px;
  transition: background 0.2s, box-shadow 0.2s;
  box-shadow: 0 1px 4px rgba(0,0,0,0.02);
}
.rag-ref-item:hover {
  background: rgba(255,255,255,0.75);
  box-shadow: 0 4px 12px rgba(16,185,129,0.06);
}
.rag-ref-index {
  font-size: 0.75rem;
  font-weight: 700;
  color: #D97706;
  background: rgba(245,158,11,0.1);
  border-radius: 8px;
  padding: 3px 8px;
  height: fit-content;
  white-space: nowrap;
  flex-shrink: 0;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  letter-spacing: 0.02em;
}
.rag-ref-body {
  flex: 1;
  min-width: 0;
}
.rag-ref-content {
  font-size: 0.82rem;
  color: #475569;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 3;
  -webkit-box-orient: vertical;
  overflow: hidden;
  word-break: break-word;
}
.rag-ref-source {
  font-size: 0.75rem;
  color: #94a3b8;
  margin-top: 6px;
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 3px 8px;
  background: rgba(16,185,129,0.04);
  border-radius: 6px;
  width: fit-content;
}
.source-icon {
  width: 12px;
  height: 12px;
  flex-shrink: 0;
  color: #10B981;
}
.rag-ref-source::before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 3px;
  border-radius: 50%;
  background: #cbd5e1;
}

/* 流式文本中引用标记样式 */
.streaming-text :deep([rag-ref]) {
	  color: #10B981;
	  font-weight: 600;
	  cursor: pointer;
	}
</style>
