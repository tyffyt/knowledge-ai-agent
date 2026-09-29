<template>
	<view v-if="visible" class="mask" @tap="close">
		<view class="sheet" @tap.stop>
			<view class="sheet-header">
				<text class="sheet-title">选择大模型</text>
				<view class="sheet-close" @tap="close">
					<image class="close-icon" src="/static/icons/close.svg" mode="aspectFit" />
				</view>
			</view>
			<view v-if="!modelList.length" class="sheet-empty">
				{{ modelsFailed ? '模型列表加载失败，请稍后重试' : '正在加载模型列表...' }}
			</view>
			<view v-else class="model-list">
				<view
					v-for="item in modelList"
					:key="item.key"
					class="model-item"
					:class="{ active: isCurrent(item), unavailable: !item.available }"
					@tap="select(item)"
				>
					<view class="model-main">
						<text class="model-name">{{ item.displayName }}</text>
						<text class="model-meta">{{ item.available ? item.provider : '未配置密钥，暂不可用' }}</text>
					</view>
					<image v-if="isCurrent(item)" class="model-check" src="/static/icons/check.svg" mode="aspectFit" />
				</view>
			</view>
		</view>
	</view>
</template>

<script setup>
/**
 * 大模型选择弹层（底部弹出）：选中即写入全局选择，对所有会话生效
 * props: visible
 * emits: close
 */
import { modelList, modelsFailed, currentModel, setSelectedModel } from '../utils/model'

defineProps({
	visible: { type: Boolean, default: false }
})

const emit = defineEmits(['close'])

function isCurrent(item) {
	return !!currentModel.value && currentModel.value.key === item.key
}

function close() {
	emit('close')
}

/** 选择模型：不可用项不响应 */
function select(item) {
	if (!item.available) {
		uni.showToast({ title: '该模型未配置密钥，暂不可用', icon: 'none' })
		return
	}
	setSelectedModel(item.key)
	emit('close')
}
</script>

<style scoped>
.mask {
	position: fixed;
	left: 0;
	top: 0;
	right: 0;
	bottom: 0;
	background: rgba(6, 78, 59, 0.45);
	z-index: 200;
	display: flex;
	align-items: flex-end;
}

.sheet {
	width: 100%;
	background: #FFFFFF;
	border-radius: 32rpx 32rpx 0 0;
	padding: 24rpx 28rpx calc(24rpx + env(safe-area-inset-bottom));
	box-sizing: border-box;
}

.sheet-header {
	display: flex;
	align-items: center;
	justify-content: space-between;
}

.sheet-title {
	font-size: 32rpx;
	font-weight: 700;
	color: #064E3B;
}

.sheet-close {
	width: 56rpx;
	height: 56rpx;
	display: flex;
	align-items: center;
	justify-content: center;
}

.close-icon {
	width: 32rpx;
	height: 32rpx;
	opacity: 0.6;
}

.sheet-empty {
	padding: 48rpx 0;
	text-align: center;
	font-size: 26rpx;
	color: #94A3B8;
}

.model-list {
	margin-top: 16rpx;
}

.model-item {
	display: flex;
	align-items: center;
	justify-content: space-between;
	min-height: 112rpx;
	padding: 16rpx 20rpx;
	border-radius: 20rpx;
	border-bottom: 1rpx solid rgba(16, 185, 129, 0.12);
}

.model-item:last-child {
	border-bottom: none;
}

.model-item.active {
	background: rgba(16, 185, 129, 0.08);
}

.model-item.unavailable {
	opacity: 0.5;
}

.model-main {
	display: flex;
	flex-direction: column;
	min-width: 0;
}

.model-name {
	font-size: 30rpx;
	color: #334155;
}

.model-item.active .model-name {
	color: #047857;
	font-weight: 600;
}

.model-meta {
	font-size: 24rpx;
	color: #94A3B8;
	margin-top: 6rpx;
}

.model-check {
	width: 36rpx;
	height: 36rpx;
	flex-shrink: 0;
}
</style>
