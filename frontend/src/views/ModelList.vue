<template>
  <div class="model-page">
    <div class="model-shell">
      <!-- 头部：返回 + 标题 -->
      <header class="page-header">
        <button type="button" class="back-link" @click="goBack">
          <ArrowLeft class="icon" size="16" />
          返回
        </button>
        <div class="header-texts">
          <h1 class="page-title">大模型详情</h1>
          <p class="page-subtitle">当前支持切换的全部大模型及其参数</p>
        </div>
      </header>

      <!-- 加载中 -->
      <section v-if="loading" class="state-block">
        <span class="spinner" aria-hidden="true"></span>
        <p>正在加载模型清单...</p>
      </section>

      <!-- 加载失败 -->
      <section v-else-if="loadError" class="state-block">
        <p class="state-error">{{ loadError }}</p>
        <button type="button" class="retry-btn" @click="loadModels">重新加载</button>
      </section>

      <!-- 模型卡片 -->
      <section v-else class="card-list">
        <article v-for="item in modelList" :key="item.key" class="model-card">
          <header class="card-header">
            <div class="card-title-wrap">
              <h2 class="card-title">{{ item.displayName }}</h2>
              <p class="card-key">{{ item.key }}</p>
            </div>
            <div class="card-tags">
              <span class="tag tag-provider">{{ item.provider }}</span>
              <span v-if="item.defaultModel" class="tag tag-default">默认模型</span>
              <span v-if="!item.available" class="tag tag-unavailable">未配置密钥</span>
            </div>
          </header>

          <p class="card-desc">{{ item.description }}</p>

          <div class="card-grid">
            <!-- 技术参数 -->
            <section class="panel">
              <h3 class="panel-title">技术参数</h3>
              <dl class="spec-list">
                <div class="spec-row">
                  <dt>上下文长度</dt>
                  <dd>{{ orDash(item.contextWindow) }}</dd>
                </div>
                <div class="spec-row">
                  <dt>最大输入</dt>
                  <dd>{{ orDash(item.maxInput) }}</dd>
                </div>
                <div class="spec-row">
                  <dt>最大输出</dt>
                  <dd>{{ orDash(item.maxOutput) }}</dd>
                </div>
                <div class="spec-row">
                  <dt>最大思维链</dt>
                  <dd>{{ orDash(item.maxReasoning) }}</dd>
                </div>
                <div class="spec-row">
                  <dt>限流</dt>
                  <dd>{{ orDash(item.rateLimit) }}</dd>
                </div>
              </dl>
            </section>

            <!-- 价格 -->
            <section class="panel">
              <h3 class="panel-title">价格</h3>
              <dl class="spec-list">
                <div v-for="price in item.prices" :key="price.label" class="spec-row">
                  <dt>{{ price.label }}</dt>
                  <dd>
                    <span class="price-value">{{ price.price }}</span>
                    <span class="price-unit">{{ price.unit }}</span>
                  </dd>
                </div>
              </dl>
              <p v-if="item.priceNote" class="panel-note">{{ item.priceNote }}</p>
            </section>
          </div>

          <!-- 能力 -->
          <section class="panel">
            <h3 class="panel-title">能力</h3>
            <ul class="capability-list">
              <li v-for="capability in item.capabilities" :key="capability" class="capability-item">
                <CircleCheck class="capability-icon" size="15" />
                {{ capability }}
              </li>
            </ul>
          </section>

          <footer class="card-footer">
            <span>数据来源：</span>
            <a
              class="source-link"
              :href="item.sourceUrl"
              :title="item.sourceUrl"
              target="_blank"
              rel="noopener noreferrer"
            >{{ sourceLabel(item.sourceUrl) }}</a>
            <span class="footer-divider">·</span>
            <span>核对日期：{{ item.verifiedAt }}</span>
          </footer>
        </article>
      </section>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, CircleCheck } from '@lucide/vue'
import { modelList, fetchModels } from '../utils/model'

const router = useRouter()

const loading = ref(false)
const loadError = ref('')

/** 按需加载模型清单（utils 内已缓存，从聊天页跳转过来不会重复请求） */
function loadModels() {
  loading.value = true
  loadError.value = ''
  fetchModels()
    .catch(() => {
      loadError.value = '模型清单加载失败，请检查网络后重试'
    })
    .finally(() => {
      loading.value = false
    })
}

/** 官网未给出该项时显示占位符，不做推测填充 */
function orDash(value) {
  return value === null || value === undefined || value === '' ? '—' : value
}

/** 数据来源只显示域名，完整地址放在 title 中，避免长链接撑破卡片 */
function sourceLabel(url) {
  if (!url) return '—'
  try {
    return new URL(url).host
  } catch {
    return url
  }
}

function goBack() {
  if (window.history.length > 1) {
    router.back()
  } else {
    router.push('/')
  }
}

onMounted(loadModels)
</script>

<style scoped>
.model-page {
  min-height: 100%;
  padding: 2rem 2rem 6rem;
  background: linear-gradient(160deg, #f0fdf9 0%, #f5f7fb 45%, #ecfdf5 100%);
  color: #1e293b;
}

.model-shell {
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
  min-height: 44px;
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
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.page-title {
  font-family: 'Playfair Display', 'Plus Jakarta Sans', serif;
  font-size: 1.6rem;
  font-weight: 700;
  color: #064e3b;
  letter-spacing: 0.5px;
}

.page-subtitle {
  font-size: 0.85rem;
  color: #64748b;
}

/* ===== 加载 / 失败态 ===== */
.state-block {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0.75rem;
  padding: 3rem 1rem;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 8px 32px rgba(16, 185, 129, 0.09);
  color: #64748b;
  font-size: 0.9rem;
}

.state-error {
  color: #b91c1c;
}

.spinner {
  width: 26px;
  height: 26px;
  border-radius: 50%;
  border: 2.5px solid rgba(16, 185, 129, 0.25);
  border-top-color: #10b981;
  animation: spin 0.8s linear infinite;
}

@keyframes spin {
  to {
    transform: rotate(360deg);
  }
}

.retry-btn {
  min-height: 44px;
  padding: 0 20px;
  border-radius: 12px;
  border: 1px solid rgba(16, 185, 129, 0.3);
  background: rgba(16, 185, 129, 0.08);
  color: #047857;
  font-family: inherit;
  font-size: 0.88rem;
  cursor: pointer;
  transition: background 0.2s, border-color 0.2s;
}
.retry-btn:hover {
  background: rgba(16, 185, 129, 0.16);
  border-color: rgba(16, 185, 129, 0.45);
}

/* ===== 模型卡片 ===== */
.card-list {
  display: flex;
  flex-direction: column;
  gap: 1.25rem;
}

.model-card {
  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 1.5rem;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  backdrop-filter: blur(20px);
  -webkit-backdrop-filter: blur(20px);
  border: 1px solid rgba(255, 255, 255, 0.5);
  box-shadow: 0 8px 32px rgba(16, 185, 129, 0.09);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.model-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 14px 40px rgba(16, 185, 129, 0.14);
}

.card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 1rem;
  flex-wrap: wrap;
}

.card-title-wrap {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.card-title {
  font-size: 1.15rem;
  font-weight: 700;
  color: #064e3b;
}

.card-key {
  font-size: 0.78rem;
  color: #94a3b8;
  letter-spacing: 0.3px;
}

.card-tags {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}

.tag {
  display: inline-flex;
  align-items: center;
  padding: 3px 10px;
  border-radius: 999px;
  font-size: 0.72rem;
  font-weight: 500;
  white-space: nowrap;
}

.tag-provider {
  background: rgba(16, 185, 129, 0.1);
  border: 1px solid rgba(16, 185, 129, 0.2);
  color: #047857;
}

.tag-default {
  background: rgba(245, 158, 11, 0.12);
  border: 1px solid rgba(245, 158, 11, 0.28);
  color: #b45309;
}

.tag-unavailable {
  background: rgba(148, 163, 184, 0.16);
  border: 1px solid rgba(148, 163, 184, 0.3);
  color: #64748b;
}

.card-desc {
  font-size: 0.88rem;
  color: #475569;
  line-height: 1.6;
}

.card-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(320px, 1fr));
  gap: 1rem;
}

.panel {
  padding: 1rem;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.6);
  border: 1px solid rgba(16, 185, 129, 0.1);
}

.panel-title {
  margin-bottom: 0.6rem;
  font-size: 0.85rem;
  font-weight: 600;
  color: #047857;
}

.spec-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.spec-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  gap: 12px;
  font-size: 0.82rem;
}

.spec-row dt {
  color: #64748b;
  flex-shrink: 0;
}

.spec-row dd {
  display: flex;
  align-items: baseline;
  gap: 6px;
  color: #1e293b;
  font-weight: 500;
  text-align: right;
  min-width: 0;
}

.price-value {
  color: #047857;
  font-weight: 600;
}

.price-unit {
  color: #94a3b8;
  font-weight: 400;
  font-size: 0.72rem;
}

.panel-note {
  margin-top: 0.6rem;
  font-size: 0.72rem;
  color: #94a3b8;
  line-height: 1.6;
}

.capability-list {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  list-style: none;
}

.capability-item {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(16, 185, 129, 0.07);
  border: 1px solid rgba(16, 185, 129, 0.16);
  color: #047857;
  font-size: 0.78rem;
}

.capability-icon {
  width: 15px;
  height: 15px;
  flex-shrink: 0;
  color: #10b981;
}

.card-footer {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
  padding-top: 0.85rem;
  border-top: 1px solid rgba(16, 185, 129, 0.12);
  font-size: 0.74rem;
  color: #94a3b8;
}

.source-link {
  color: #10b981;
  text-decoration: none;
  transition: color 0.2s;
}
.source-link:hover {
  color: #047857;
  text-decoration: underline;
}

.footer-divider {
  color: #cbd5e1;
}

@media (max-width: 768px) {
  .model-page {
    padding: 1.25rem 1rem 4rem;
  }
  .page-title {
    font-size: 1.35rem;
  }
  .model-card {
    padding: 1.1rem;
  }
  .card-grid {
    grid-template-columns: 1fr;
  }
  .spec-row {
    flex-direction: column;
    align-items: flex-start;
    gap: 2px;
  }
  .spec-row dd {
    text-align: left;
  }
}

@media (prefers-reduced-motion: reduce) {
  .model-card,
  .back-link,
  .retry-btn,
  .source-link {
    transition: none;
  }
  .spinner {
    animation-duration: 2s;
  }
}
</style>
