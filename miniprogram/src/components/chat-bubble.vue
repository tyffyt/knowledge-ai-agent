<template>
	<view class="bubble-row" :class="isUser ? 'row-user' : 'row-ai'">
		<!-- AI 头像 -->
		<view v-if="!isUser" class="avatar avatar-ai">知</view>

		<view class="bubble-wrap">
			<!-- 用户消息：绿底白字 -->
			<view v-if="isUser" class="bubble bubble-user">
				<text class="user-text" user-select>{{ msg.content }}</text>
			</view>

			<!-- AI 消息：白底 + markdown -->
			<view v-else class="bubble bubble-ai">
				<!-- 流式生成中：三点动画 -->
				<view v-if="msg.loading && !msg.content" class="loading-dots">
					<view class="dot"></view>
					<view class="dot"></view>
					<view class="dot"></view>
				</view>
				<!-- 流式生成中：纯文本增量（mp-html 内容频繁变化会触发其 watch 空填充 bug，流结束后再渲染） -->
				<view v-else-if="msg.loading && msg.content" class="streaming-text">{{ msg.content }}</view>
				<mp-html
					v-else-if="rendered"
					:content="rendered"
					:tag-style="tagStyle"
					:lazy-load="false"
					:preview-img="false"
					@imgtap="onImgTap"
					@linktap="onLinkTap"
				/>
				<!-- TTS 播报按钮：仅图标，无边框无底色；播报中变为停止图标 -->
				<view v-if="msg.content && !msg.loading" class="ai-actions">
					<view class="action-btn" @tap="onToggleSpeech">
						<image class="action-icon" :src="speaking ? '/static/icons/volume-off.svg' : '/static/icons/volume.svg'" mode="aspectFit" />
					</view>
				</view>
				<!-- RAG 引用折叠展示 -->
				<view v-if="msg.references && msg.references.length" class="refs">
					<view class="refs-toggle" @tap="refsCollapsed = !refsCollapsed">
						<text>引用 {{ msg.references.length }} 篇 {{ refsCollapsed ? '展开' : '收起' }}</text>
					</view>
					<view v-if="!refsCollapsed" class="refs-list">
						<view v-for="ref in msg.references" :key="ref.index" class="ref-item">
							<text class="ref-index">[{{ ref.index }}]</text>
							<text class="ref-filename">{{ ref.metadata && ref.metadata.filename ? ref.metadata.filename : '知识库' }}</text>
							<view class="ref-content">{{ ref.content }}</view>
						</view>
					</view>
				</view>
			</view>
		</view>

		<!-- 用户头像 -->
		<view v-if="isUser" class="avatar avatar-user">我</view>
	</view>
</template>

<script setup>
/**
 * 消息气泡：用户右对齐绿底，AI 左对齐白底
 * - Markdown 用 marked 渲染为 HTML（gfm 自动链接裸 URL，图片走代理）再交给 mp-html 展示与清洗
 * - 图片点击放大预览（uni.previewImage）
 * - 链接点击：/api/files 下载打开，外链复制到剪贴板
 * - TTS 播报 / 停止
 * - RAG 引用折叠展示
 */
import { computed, ref, watch } from 'vue'
import { Marked } from 'marked'
import { preprocessMarkdown, proxyImageUrl, classifyLink } from '../utils/markdown'
import { playTTS, stopSpeech } from '../utils/tts'
import { getLocalImagePath, getImagePlaceholder, preloadMessageImages } from '../utils/image-loader'

const props = defineProps({
	msg: { type: Object, required: true }
})

const isUser = computed(() => props.msg.role === 'user')
const speaking = ref(false)
const refsCollapsed = ref(true)
// 图片下载完成/失败后自增，触发 rendered 重算（mp-html 重新设置 content）
const renderVersion = ref(0)

function escapeAttr(s) {
	return String(s || '')
		.replace(/&/g, '&amp;')
		.replace(/"/g, '&quot;')
		.replace(/</g, '&lt;')
		.replace(/>/g, '&gt;')
}

// marked 渲染器：图片地址代理化并优先用本地已下载路径（真机 image 组件加载 http+IP 受限），
// 未下载完成时显示占位图，下载完成后 renderVersion 触发重渲染换成本地路径；链接保留原 href
const marked = new Marked({
	gfm: true,
	renderer: {
		image({ href, text }) {
			const proxyUrl = proxyImageUrl(href)
			const local = getLocalImagePath(proxyUrl)
			return '<img src="' + escapeAttr(local || getImagePlaceholder()) + '" alt="' + escapeAttr(text) + '" />'
		},
		link({ href, text }) {
			return '<a href="' + escapeAttr(href) + '">' + text + '</a>'
		}
	}
})

const rendered = computed(() => {
	if (isUser.value || props.msg.loading) return ''
	renderVersion.value // 依赖标记：图片下载完成后自增触发重渲染
	// 流式生成中走 streaming-text 纯文本分支，这里不做解析，流结束只解析一次
	const html = marked.parse(preprocessMarkdown(props.msg.content || ''))
	return html || ''
})

// 流结束后预加载消息内全部图片：下载完成再触发重新渲染（本地路径加载无真机限制）
// 用户消息纯文本渲染不走图片链路，跳过
watch(
	() => props.msg.loading,
	(loading) => {
		if (!loading && props.msg.content && !isUser.value) {
			preloadMessageImages(props.msg.content, proxyImageUrl).then((count) => {
				if (count > 0) renderVersion.value++
			})
		}
	},
	{ immediate: true }
)

const tagStyle = {
	code: 'background:#F0FDF4;color:#065F46;padding:2rpx 8rpx;border-radius:6rpx;font-size:24rpx;',
	pre: 'background:#F8FAFC;padding:20rpx;border-radius:12rpx;overflow-x:auto;font-size:24rpx;',
	table: 'display:block;width:100%;overflow-x:auto;border-collapse:collapse;font-size:24rpx;',
	td: 'border:1rpx solid #D1D5DB;padding:8rpx 12rpx;',
	th: 'border:1rpx solid #D1D5DB;padding:8rpx 12rpx;background:#F0FDF4;',
	blockquote: 'border-left:6rpx solid #10B981;padding-left:16rpx;color:#64748B;margin:16rpx 0;',
	a: 'color:#059669;text-decoration:underline;'
}

/**
 * mp-html 的 imgtap/linktap emit 参数是 attrs 对象本身（Vue 语义），
 * 部分环境可能包装为事件对象（e.detail）——统一兼容两种形态
 */
function resolveAttrs(e) {
	return (e && e.detail) || e || {}
}

/** 图片点击放大预览；占位图（未加载完成/失败）点击时重试加载 */
function onImgTap(e) {
	const attrs = resolveAttrs(e)
	const src = attrs.src || ''
	if (!src) return
	if (src.startsWith('data:') || src.includes('img-placeholder')) {
		uni.showToast({ title: '正在加载图片...', icon: 'none' })
		preloadMessageImages(props.msg.content, proxyImageUrl).then((count) => {
			if (count > 0) renderVersion.value++
			else uni.showToast({ title: '图片加载失败，请稍后重试', icon: 'none' })
		})
		return
	}
	uni.previewImage({ urls: [src], current: src })
}

/** 链接点击：文件下载 / 外链复制 */
function onLinkTap(e) {
	const attrs = resolveAttrs(e)
	const href = attrs.href || ''
	if (!href) return
	const link = classifyLink(href)
	if (link.type === 'file') {
		downloadFile(link.url)
	} else if (link.type === 'web') {
		uni.setClipboardData({
			data: link.url,
			success: () => {
				uni.showToast({ title: '链接已复制', icon: 'none' })
			}
		})
	} else {
		uni.setClipboardData({
			data: link.url,
			success: () => {
				uni.showToast({ title: '已复制', icon: 'none' })
			}
		})
	}
}

/** 下载 AI 生成的文件（PDF 等）并用系统文档查看器打开 */
function downloadFile(url) {
	uni.showLoading({ title: '下载中...' })
	const task = uni.downloadFile({
		url,
		success: (res) => {
			uni.hideLoading()
			if (res.statusCode >= 200 && res.statusCode < 300) {
				uni.openDocument({
					filePath: res.tempFilePath,
					showMenu: true,
					fail: () => {
						uni.showToast({ title: '文件已下载，但无法预览', icon: 'none' })
					}
				})
			} else {
				uni.showToast({ title: '下载失败(' + res.statusCode + ')', icon: 'none' })
			}
		},
		fail: () => {
			uni.hideLoading()
			uni.showToast({ title: '下载失败，请检查网络', icon: 'none' })
		}
	})
	// 文件较大时显示进度
	task.onProgressUpdate && task.onProgressUpdate((p) => {
		if (p.progress === 100) {
			setTimeout(() => uni.hideLoading(), 300)
		}
	})
}

/** TTS 播报 / 停止切换 */
function onToggleSpeech() {
	if (speaking.value) {
		stopSpeech()
		speaking.value = false
		return
	}
	speaking.value = true
	playTTS(props.msg.content)
		.then(() => {
			speaking.value = false
		})
		.catch((err) => {
			speaking.value = false
			uni.showToast({ title: (err && err.message) || '播报失败', icon: 'none' })
		})
}
</script>

<style scoped>
.bubble-row {
	display: flex;
	padding: 16rpx 24rpx;
	align-items: flex-start;
}

.row-user {
	justify-content: flex-end;
}

.row-ai {
	justify-content: flex-start;
}

.avatar {
	width: 80rpx;
	height: 80rpx;
	border-radius: 50%;
	display: flex;
	align-items: center;
	justify-content: center;
	font-size: 28rpx;
	flex-shrink: 0;
	margin-top: 8rpx;
}

.avatar-ai {
	background: #ECFDF5;
	color: #10B981;
	font-weight: 700;
	margin-right: 16rpx;
}

.avatar-user {
	background: #10B981;
	color: #FFFFFF;
	font-weight: 600;
	margin-left: 16rpx;
}

.bubble-wrap {
	max-width: 78%;
	min-width: 0;
}

.bubble {
	border-radius: 24rpx;
	padding: 20rpx 24rpx;
	overflow-wrap: anywhere;
}

.bubble-user {
	background: linear-gradient(135deg, #10B981, #34D399);
	color: #FFFFFF;
	border-top-right-radius: 8rpx;
}

.user-text {
	font-size: 28rpx;
	line-height: 1.6;
}

.bubble-ai {
	background: rgba(255, 255, 255, 0.9);
	border: 1rpx solid rgba(16, 185, 129, 0.15);
	border-top-left-radius: 8rpx;
	box-shadow: 0 4rpx 16rpx rgba(16, 185, 129, 0.08);
}

/* 流式生成中动画 */
.loading-dots {
	display: flex;
	align-items: center;
	height: 48rpx;
}

/* 流式生成中的纯文本增量 */
.streaming-text {
	font-size: 28rpx;
	line-height: 1.6;
	white-space: pre-wrap;
	word-break: break-all;
	color: #064E3B;
}

.dot {
	width: 12rpx;
	height: 12rpx;
	border-radius: 50%;
	background: #10B981;
	margin-right: 10rpx;
	animation: blink 1.2s infinite ease-in-out;
}

.dot:nth-child(2) {
	animation-delay: 0.2s;
}

.dot:nth-child(3) {
	animation-delay: 0.4s;
}

@keyframes blink {
	0%, 80%, 100% {
		opacity: 0.2;
		transform: scale(0.8);
	}
	40% {
		opacity: 1;
		transform: scale(1);
	}
}

/* AI 消息操作条 */
.ai-actions {
	display: flex;
	margin-top: 16rpx;
}

/* 仅图标、无边框无底色 */
.action-btn {
	display: flex;
	align-items: center;
	justify-content: center;
	background: transparent;
	border: none;
	padding: 12rpx;
	border-radius: 12rpx;
}

.action-btn:active {
	opacity: 0.6;
}

.action-icon {
	width: 32rpx;
	height: 32rpx;
}

/* RAG 引用 */
.refs {
	margin-top: 20rpx;
	border-top: 1rpx dashed rgba(16, 185, 129, 0.3);
	padding-top: 16rpx;
}

.refs-toggle {
	background: rgba(245, 158, 11, 0.1);
	color: #B45309;
	border-radius: 999rpx;
	padding: 8rpx 24rpx;
	font-size: 24rpx;
	display: inline-block;
}

.refs-list {
	margin-top: 12rpx;
}

.ref-item {
	background: #FFFBEB;
	border: 1rpx solid rgba(245, 158, 11, 0.25);
	border-radius: 12rpx;
	padding: 16rpx;
	margin-top: 12rpx;
}

.ref-index {
	color: #B45309;
	font-weight: 700;
	font-size: 24rpx;
}

.ref-filename {
	color: #92400E;
	font-size: 24rpx;
	margin-left: 8rpx;
}

.ref-content {
	color: #78716C;
	font-size: 24rpx;
	margin-top: 8rpx;
	display: -webkit-box;
	-webkit-box-orient: vertical;
	-webkit-line-clamp: 3;
	overflow: hidden;
}
</style>
