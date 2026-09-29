import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn, isAdmin } from '../utils/auth'
import { fetchCurrentUser } from '../api/request'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('../views/Home.vue'),
    meta: { title: '应用中心' },
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('../views/Login.vue'),
    meta: { title: '登录' },
  },
  {
    path: '/knowledge',
    name: 'KnowledgeApp',
    component: () => import('../views/KnowledgeChat.vue'),
    meta: { title: '个人知识助手', requiresAuth: true },
  },
  {
    path: '/manus',
    name: 'ManusApp',
    component: () => import('../views/ManusChat.vue'),
    meta: { title: 'AI 超级智能体', requiresAuth: true },
  },
  {
    path: '/change-password',
    name: 'ChangePassword',
    component: () => import('../views/ChangePassword.vue'),
    meta: { title: '修改密码', requiresAuth: true },
  },
  {
    path: '/models',
    name: 'ModelList',
    component: () => import('../views/ModelList.vue'),
    meta: { title: '大模型', requiresAuth: true },
  },
  {
    path: '/knowledge-documents',
    name: 'KnowledgeDocuments',
    component: () => import('../views/KnowledgeDocuments.vue'),
    meta: { title: '知识库管理', requiresAuth: true, requiresAdmin: true },
  },
  {
    path: '/user-manage',
    name: 'UserManage',
    component: () => import('../views/UserManage.vue'),
    meta: { title: '用户管理', requiresAuth: true, requiresAdmin: true },
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

router.beforeEach(async (to) => {
  if (to.meta?.title) {
    document.title = to.meta.title + ' - AI 应用中心'
  }
  // 需要登录的页面：未登录则跳转登录页，并记录 returnUrl 便于登录后返回
  if (to.meta?.requiresAuth && !isLoggedIn()) {
    return { path: '/login', query: { returnUrl: to.fullPath, msg: '请先登录后再访问该页面' } }
  }
  // 需要管理员权限的页面：以服务端返回的 isAdmin 为准（本地标识缺失时先向服务端确认一次）
  if (to.meta?.requiresAdmin) {
    if (!isAdmin.value) {
      try {
        await fetchCurrentUser()
      } catch {
        return { path: '/login', query: { returnUrl: to.fullPath, msg: '请先登录后再访问该页面' } }
      }
    }
    if (!isAdmin.value) {
      return { path: '/', query: { msg: `仅管理员可访问${to.meta.title || '该页面'}` } }
    }
  }
})

export default router
