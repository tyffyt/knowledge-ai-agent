<template>
  <div class="app">
    <main class="main-content">
      <router-view />
    </main>

    <!-- 全局 Toast 通知 -->
    <Teleport to="body">
      <div class="toast-container">
        <TransitionGroup name="toast">
          <div
            v-for="t in toasts"
            :key="t.id"
            class="toast"
            :class="'toast-' + t.type"
          >
            <Check v-if="t.type === 'success'" class="toast-icon" size="20" />
	            <AlertCircle v-else-if="t.type === 'error'" class="toast-icon" size="20" />
	            <Info v-else class="toast-icon" size="20" />
            <span class="toast-text">{{ t.message }}</span>
          </div>
        </TransitionGroup>
      </div>
    </Teleport>

    <!-- 右上角品牌 Logo — 线形地球（移动端聊天页隐藏，避免遮挡头部） -->
    <router-link to="/" class="brand-logo" :class="{ 'brand-hidden': hideBrand }" title="AI Agent 智能代理平台">
      <svg viewBox="0 0 40 40" fill="none" class="brand-svg">
        <!-- 地球外轮廓 -->
        <circle cx="20" cy="20" r="14" stroke="#818cf8" stroke-width="1.3" class="globe-outline"/>
        <!-- 经线 -->
        <ellipse cx="20" cy="20" rx="4" ry="14" stroke="#a78bfa" stroke-width="0.9" class="meridian meridian-1"/>
        <ellipse cx="20" cy="20" rx="10" ry="14" stroke="#a78bfa" stroke-width="0.7" class="meridian meridian-2"/>
        <!-- 纬线 -->
        <ellipse cx="20" cy="20" rx="14" ry="4" stroke="#a78bfa" stroke-width="0.9" class="parallel parallel-1"/>
        <ellipse cx="20" cy="20" rx="14" ry="10" stroke="#a78bfa" stroke-width="0.7" class="parallel parallel-2"/>
        <!-- 小圆点装饰（卫星/节点） -->
        <circle cx="34" cy="8" r="1.8" fill="#818cf8" class="node node-1"/>
        <circle cx="6" cy="32" r="1.3" fill="#a78bfa" class="node node-2"/>
        <circle cx="36" cy="28" r="1" fill="#c4b5fd" class="node node-3"/>
        <defs>
          <linearGradient id="brandGrad" x1="0" y1="0" x2="40" y2="40">
            <stop offset="0%" stop-color="#6366f1"/>
            <stop offset="100%" stop-color="#818cf8"/>
          </linearGradient>
        </defs>
      </svg>
    </router-link>

    <!-- 左下角用户模块 -->
    <div class="user-dock" :class="{ 'dock-hidden': !showDock }">
      <!-- 未登录：显示登录入口 + "未登录" 胶囊 -->
      <div v-if="!loggedIn" class="dock-trigger dock-trigger-expanded" @click="goLogin" title="登录">
        <div class="dock-ring-bar-wrapper">
          <div class="dock-avatar-ring">
          <div class="dock-avatar">
	              <User class="dock-guest-icon" size="24" />
	            </div>
          </div>
          <div class="dock-name-bar dock-name-bar-guest">
            <span class="dock-name-text">未登录</span>
          </div>
        </div>
      </div>
      <!-- 已登录：头像 + 胶囊（右侧，被头像覆盖左侧边缘）+ 上拉菜单 -->
      <div v-else ref="avatarWrapRef" class="dock-avatar-wrap">
        <button
          type="button"
          class="dock-trigger dock-trigger-expanded"
          @click.stop="showDropdown = !showDropdown"
          title="账号"
        >
          <div class="dock-ring-bar-wrapper">
            <!-- 头像（顶层） -->
            <div class="dock-avatar-ring">
              <div class="dock-avatar">
                <span class="dock-letter">{{ avatarLetter }}</span>
              </div>
            </div>
            <!-- 胶囊（头像右侧，负边距滑入头像下方） -->
            <div class="dock-name-bar" :style="capsuleStyle">
              <span class="dock-name-text">{{ reactiveUsername }}</span>
            </div>
          </div>
        </button>
        <Transition name="dock-dropdown">
          <div v-show="showDropdown" class="dock-dropdown-panel">
            <div class="dock-dropdown-name">{{ reactiveUsername }}</div>
	            <button v-if="reactiveIsAdmin" type="button" class="dock-dropdown-item" @click="goUserManage">
	              <Users class="dropdown-icon" size="18" />
	              用户管理
	            </button>
	            <button v-if="reactiveIsAdmin" type="button" class="dock-dropdown-item" @click="goKnowledgeDocuments">
	              <Database class="dropdown-icon" size="18" />
	              知识库管理
	            </button>
              <button type="button" class="dock-dropdown-item" @click="goModels">
                <Cpu class="dropdown-icon" size="18" />
                大模型详情
              </button>
              <button type="button" class="dock-dropdown-item" @click="goChangePassword">
                <Lock class="dropdown-icon" size="18" />
                修改密码
              </button>
	            <button type="button" class="dock-dropdown-item" @click="logout">
	              <LogOut class="dropdown-icon" size="18" />
	              退出登录
	            </button>
          </div>
        </Transition>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted, onUnmounted, provide } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { request, fetchCurrentUser } from './api/request'
import {
  isLoggedIn, getUsername, removeToken, token,
  username as reactiveUsername, isAdmin as reactiveIsAdmin,
} from './utils/auth'
import { Check, AlertCircle, Info, User, Lock, LogOut, Database, Users, Cpu } from '@lucide/vue'

const router = useRouter()
const route = useRoute()
const showDropdown = ref(false)
const avatarWrapRef = ref(null)

// Sidebar state: shared across components via provide/inject
const isSidebarOpen = ref(true)
provide('isSidebarOpen', isSidebarOpen)
provide('setSidebarOpen', (val) => { isSidebarOpen.value = val })

// ===== 全局 Toast 通知 =====
const toasts = ref([])
let toastId = 0
function showToast(message, type = 'info', duration = 3000) {
  const id = ++toastId
  toasts.value.push({ id, message, type })
  setTimeout(() => {
    const idx = toasts.value.findIndex(t => t.id === id)
    if (idx !== -1) toasts.value.splice(idx, 1)
  }, duration)
}
provide('showToast', showToast)

// ===== 启动时验证 token 有效性并恢复登录态（含管理员标识） =====
onMounted(async () => {
  if (isLoggedIn()) {
    try {
      await fetchCurrentUser()
    } catch {
      // token 无效或后端重启，清除登录状态
      removeToken()
      loggedIn.value = false
    }
  }
})

// 路由跳转带回的提示（如权限不足被拦回首页）：提示一次后清理 query，避免刷新重复弹出
// 登录页的提示由登录页自身展示，此处跳过避免重复
watch(() => route.query.msg, (msg) => {
  if (typeof msg !== 'string' || !msg) return
  if (route.path === '/login') return
  showToast(msg, 'error')
  const query = { ...route.query }
  delete query.msg
  router.replace({ path: route.path, query })
})

// ===== 移动端检测（<=768px） =====
const isMobile = ref(false)
function updateIsMobile() {
  isMobile.value = window.innerWidth <= 768
}

// 是否显示 dock：移动端聊天页隐藏（避免遮挡底部输入区）；/knowledge 历史对话页、/knowledge-documents 知识库管理页与 /user-manage 用户管理页始终隐藏（避免与历史对话列表、批量操作条重叠）
const showDock = computed(() => {
  if (isMobile.value && (route.path === '/knowledge' || route.path === '/manus')) return false
  // 历史对话页、知识库管理页与用户管理页隐藏个人信息组件，避免与列表/底部批量操作条重叠（入口可经页面返回键回到首页使用）
  if (route.path === '/knowledge' || route.path === '/knowledge-documents' || route.path === '/user-manage') return false
  return true // 其他页面始终显示
})

// 移动端聊天页隐藏品牌 Logo，避免遮挡页面头部
const hideBrand = computed(() => {
  return isMobile.value && (route.path === '/knowledge' || route.path === '/manus')
})

const loggedIn = ref(isLoggedIn())
watch(() => route.path, () => {
  loggedIn.value = isLoggedIn()
}, { immediate: true })

const avatarLetter = computed(() => {
  const name = reactiveUsername.value
  return name ? name.trim().charAt(0).toUpperCase() : ''
})
const avatarBgColor = computed(() => {
  const name = reactiveUsername.value
  if (!name) return '#64748b'
  let n = 0
  for (let i = 0; i < name.length; i++) n += name.charCodeAt(i)
  const hues = ['#6366f1', '#8b5cf6', '#0D9488', '#ef4444', '#f97316', '#22c55e', '#14b8a6', '#3b82f6']
  return hues[n % hues.length]
	})

// 胶囊背景固定使用与头像环一致的靛蓝色系渐变
	const capsuleStyle = computed(() => ({
	  background: `linear-gradient(135deg, rgba(16,185,129,0.06), rgba(52,211,153,0.12))`,
	  borderColor: `rgba(16,185,129,0.18)`
	}))

function goLogin() { router.push('/login') }
function closeDropdown() { showDropdown.value = false }
function handleClickOutside(e) {
  if (avatarWrapRef.value && !avatarWrapRef.value.contains(e.target)) closeDropdown()
}
function goChangePassword() { closeDropdown(); router.push('/change-password') }
function goModels() { closeDropdown(); router.push('/models') }
function goKnowledgeDocuments() { closeDropdown(); router.push('/knowledge-documents') }
function goUserManage() { closeDropdown(); router.push('/user-manage') }
function logout() {
  request.post('/auth/logout').catch(() => {})
  removeToken()
  loggedIn.value = false
  closeDropdown()
  showToast('已退出登录', 'info')
  router.push('/')
}

onMounted(() => {
  updateIsMobile()
  window.addEventListener('resize', updateIsMobile)
  document.addEventListener('click', handleClickOutside)
})
onUnmounted(() => {
  window.removeEventListener('resize', updateIsMobile)
  document.removeEventListener('click', handleClickOutside)
})
</script>

<style>
@import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@300;400;500;600;700&family=Playfair+Display:ital,wght@0,600;0,700;1,600&family=Ma+Shan+Zheng&display=swap');
	:root {
	  --bg-page: #ECFDF5;
	  --bg-card: #ffffff;
	  --text-primary: #064E3B;
	  --text-secondary: #10B981;
	  --text-muted: #94a3b8;
	  --border-light: #A7F3D0;
	  --border-lighter: #D1FAE5;
	  --shadow-sm: 0 1px 3px rgba(16,185,129,0.08);
	  --shadow-md: 0 4px 16px rgba(16,185,129,0.10);
	  --shadow-lg: 0 10px 40px rgba(16,185,129,0.12);
	  --teal: #10B981;
	  --blue: #3b82f6;
	  --primary: #10B981;
	  --primary-light: #34D399;
	  --cta: #10b981;
	  --glass-bg: rgba(255,255,255,0.75);
	  --glass-border: rgba(255,255,255,0.3);
	  --glass-shadow: 0 8px 32px rgba(16,185,129,0.08);
	}
::-webkit-scrollbar { width: 6px; height: 6px; }
::-webkit-scrollbar-track { background: transparent; }
::-webkit-scrollbar-thumb { background: #cbd5e1; border-radius: 3px; }
::-webkit-scrollbar-thumb:hover { background: #94a3b8; }
html { scrollbar-width: thin; scrollbar-color: #cbd5e1 transparent; }
* { margin: 0; padding: 0; box-sizing: border-box; }
html, body, #app, .app {
  font-family: 'Plus Jakarta Sans', 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif;
  background: var(--bg-page);
  color: var(--text-primary);
}
html {
  -webkit-text-size-adjust: 100%;
  text-size-adjust: 100%;
}
html, body, #app {
  height: 100%;
}
html, body {
  overflow-x: hidden;
  -webkit-tap-highlight-color: transparent;
}
.app {
  display: flex;
  flex-direction: column;
  height: 100%;
  /* 移动端浏览器地址栏收起/展开时高度自适应 */
  height: 100dvh;
}
button { touch-action: manipulation; }

/* 全局头部 */
.main-content { flex: 1; min-height: 0; }

/* 邮箱和社交动态发光 */
@keyframes ringSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
@keyframes letterGlow {
  0%, 100% { text-shadow: 0 0 12px rgba(99,102,241,0.3), 0 0 30px rgba(99,102,241,0.1); }
  50% { text-shadow: 0 0 20px rgba(99,102,241,0.5), 0 0 50px rgba(99,102,241,0.2); }
}
@keyframes letterShift {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

/* ===== 右上角品牌 Logo ===== */
.brand-logo {
  position: fixed;
  top: 6px;
  right: 20px;
  z-index: 1000;
  width: 44px;
  height: 44px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12px;
  background: rgba(255,255,255,0.7);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border: 1px solid rgba(255,255,255,0.4);
  box-shadow: 0 4px 16px rgba(99,102,241,0.1);
  cursor: pointer;
  transition: transform 0.25s ease, box-shadow 0.25s ease;
}
.brand-logo:hover {
  transform: scale(1.08) rotate(-3deg);
  box-shadow: 0 8px 28px rgba(99,102,241,0.18);
}
/* 移动端聊天页隐藏品牌 Logo */
.brand-logo.brand-hidden {
  display: none;
}
.brand-svg {
  width: 30px;
  height: 30px;
}
/* 地球线条动画 */
.globe-outline {
  animation: globePulse 4s ease-in-out infinite;
}
.meridian {
  transform-origin: 20px 20px;
}
.meridian-1 {
  animation: meridianSpin 8s linear infinite;
}
.meridian-2 {
  animation: meridianSpin 10s linear infinite reverse;
}
.parallel {
  transform-origin: 20px 20px;
}
.parallel-1 {
  animation: parallelGlow 4s ease-in-out infinite;
}
.parallel-2 {
  animation: parallelGlow 5s ease-in-out infinite reverse;
}
.node {
  animation: nodeFloat 3s ease-in-out infinite;
}
.node-2 { animation-delay: 0.8s; }
.node-3 { animation-delay: 1.6s; }

@keyframes globePulse {
  0%, 100% { opacity: 0.7; stroke-width: 1.3; }
  50% { opacity: 1; stroke-width: 1.6; }
}
@keyframes meridianSpin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
@keyframes parallelGlow {
  0%, 100% { opacity: 0.5; stroke-width: 0.7; }
  50% { opacity: 1; stroke-width: 1.1; }
}
@keyframes nodeFloat {
  0%, 100% { transform: translateY(0); opacity: 0.6; }
  50% { transform: translateY(-3px); opacity: 1; }
}

/* ===== 左下角用户模块 ===== */
.user-dock {
  position: fixed;
  left: 20px;
  bottom: calc(20px + env(safe-area-inset-bottom));
  z-index: 999;
  transition: opacity 0.3s ease, transform 0.3s ease;
}
.user-dock.dock-hidden {
  opacity: 0;
  transform: translateY(20px);
  pointer-events: none;
}

.dock-trigger {
  padding: 0;
  border: none;
  background: none;
  cursor: pointer;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

/* 展开状态：头像在顶层 + 圆柱长条在底层被部分覆盖 */
.dock-trigger-expanded {
	  border-radius: 999px;
	  display: flex;
	  align-items: center;
	  padding: 0;
	  background: rgba(255,255,255,0.5);
	  backdrop-filter: blur(16px);
	  -webkit-backdrop-filter: blur(16px);
	  border: 0px solid rgba(255,255,255,0.4);
	  box-shadow:
	    0 2px 12px rgba(99,102,241,0.08),
	    0 0 0 1px rgba(255,255,255,0.3) inset;
	  transition: box-shadow 0.25s ease, background 0.25s ease;
	}
	.dock-trigger-expanded:hover {
	  box-shadow:
	    0 6px 24px rgba(99,102,241,0.15),
	    0 0 0 1px rgba(255,255,255,0.5) inset;
	  background: rgba(255,255,255,0.65);
	}

/* 圆柱 + 头像组合容器 — 头像左侧 + 胶囊右侧重叠 */
.dock-ring-bar-wrapper {
  display: flex;
  align-items: center;
  height: 46px;
}

/* 胶囊（头像右侧，负边距滑入头像下方） */
	.dock-name-bar {
	  height: 100%;
	  padding: 0 30px 0 40px;
	  display: flex;
	  align-items: center;
	  border-radius: 999px;
	  border: 3px solid;
	  flex: 1;
	  min-width: 50px;
	  margin-left: -27px;
	  backdrop-filter: blur(4px);
	  -webkit-backdrop-filter: blur(4px);
	  background: linear-gradient(135deg, rgba(16,185,129,0.06), rgba(52,211,153,0.10));
	  border-color: rgba(16,185,129,0.25);
	}
	.dock-name-bar-guest {
	  background: linear-gradient(135deg, rgba(16,185,129,0.04), rgba(52,211,153,0.06));
	  border-color: rgba(16,185,129,0.18);
	}
	.dock-name-text {
	  font-size: 0.85rem;
	  font-weight: 400;
	  color: #065F46;
	  white-space: nowrap;
	  max-width: 100px;
	  overflow: hidden;
	  text-overflow: ellipsis;
	  user-select: none;
	}

/* 头像（顶层，覆盖胶囊左侧边缘） */
.dock-avatar-ring {
  position: relative;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  z-index: 1;
  background: var(--bg-page);
}
	.dock-avatar-ring::before {
	  content: '';
	  position: absolute;
	  inset: -2px;
	  border-radius: 50%;
	  background: conic-gradient(#10B981, #34D399, #6EE7B7, #A7F3D0, #10B981);
	  animation: ringSpin 4s linear infinite;
	  mask: radial-gradient(farthest-side, transparent calc(100% - 2.5px), #fff calc(100% - 2.5px));
	  -webkit-mask: radial-gradient(farthest-side, transparent calc(100% - 2.5px), #fff calc(100% - 2.5px));
	}
.dock-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  background: var(--bg-page);
  cursor: pointer;
  transition: transform 0.2s;
}
.dock-avatar:hover {
  transform: scale(1.08);
}
	.dock-letter {
	  font-family: 'Space Grotesk', 'Plus Jakarta Sans', sans-serif;
	  font-size: 1.4rem;
	  font-weight: 700;
	  background: linear-gradient(135deg, #10B981, #34D399, #6EE7B7);
	  background-size: 200% 200%;
	  animation: letterShift 3s ease-in-out infinite, letterGlow 2.5s ease-in-out infinite;
	  -webkit-background-clip: text;
	  -webkit-text-fill-color: transparent;
	  background-clip: text;
	  user-select: none;
	}
	.dock-guest-icon {
	  width: 24px;
	  height: 24px;
	  color: #6EE7B7;
	  opacity: 0.7;
	}
.dock-avatar-wrap {
  position: relative;
}

/* 上拉菜单（从底部弹出） */
	.dock-dropdown-panel {
	  position: absolute;
	  bottom: calc(100% + 12px);
	  left: 0;
	  min-width: 180px;
	  padding: 0.4rem 0;
	  background: var(--glass-bg);
	  backdrop-filter: blur(20px);
	  -webkit-backdrop-filter: blur(20px);
	  border: 1px solid var(--glass-border);
	  border-radius: 14px;
	  box-shadow: 0 -8px 32px rgba(16,185,129,0.1);
	  z-index: 100;
	}
.dock-dropdown-name {
  padding: 0.6rem 1rem;
  color: var(--text-primary);
  font-size: 0.9rem;
  font-weight: 600;
  border-bottom: 1px solid var(--border-lighter);
}
.dock-dropdown-item {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  padding: 0.6rem 1rem;
  border: none;
  background: none;
  color: var(--text-secondary);
  font-size: 0.875rem;
  text-align: left;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}
	.dock-dropdown-item:hover {
	  background: rgba(16,185,129,0.08);
	  color: var(--primary);
	}
.dropdown-icon {
  width: 18px;
  height: 18px;
  flex-shrink: 0;
  opacity: 0.7;
}

/* 下拉动画（向上弹出） */
.dock-dropdown-enter-active,
.dock-dropdown-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.dock-dropdown-enter-from,
.dock-dropdown-leave-to {
  opacity: 0;
  transform: translateY(8px);
}

@media (max-width: 768px) {
  .dock-ring-bar-wrapper {
    height: auto;
  }
  .dock-name-bar {
    display: none;
  }
  .dock-trigger-expanded {
    border-radius: 50%;
    background: none;
    border: none;
    box-shadow: none;
    padding: 0;
  }
  .dock-avatar-ring {
    background: transparent;
  }
  .dock-avatar {
    background: transparent;
  }
  .brand-logo {
    top: calc(12px + env(safe-area-inset-top));
    right: 12px;
    width: 38px;
    height: 38px;
  }
	  .brand-svg {
	    width: 26px;
	    height: 26px;
	  }
	}

/* ===== 全局 Toast 通知 ===== */
.toast-container {
  position: fixed;
  top: 20px;
  left: 50%;
  transform: translateX(-50%);
  z-index: 9999;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8px;
  pointer-events: none;
}
.toast {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 10px;
  font-size: 0.9rem;
  font-weight: 500;
  color: #fff;
  box-shadow: 0 4px 20px rgba(0,0,0,0.12);
  pointer-events: auto;
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
}
	.toast-success {
	  background: rgba(16,185,129,0.92);
	}
	.toast-error {
	  background: rgba(239,68,68,0.92);
	}
	.toast-info {
	  background: rgba(16,185,129,0.92);
	}
.toast-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
}
.toast-text {
  white-space: nowrap;
}
.toast-enter-active,
.toast-leave-active {
  transition: all 0.3s ease;
}
.toast-enter-from {
  opacity: 0;
  transform: translateY(-20px);
}
.toast-leave-to {
  opacity: 0;
  transform: translateY(-10px);
}
</style>
