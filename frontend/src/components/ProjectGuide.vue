<template>
  <div class="guide-overlay" @click.self="handleOverlayClick">
    <div class="guide-modal">
      <!-- 头部 -->
      <div class="guide-header">
        <h3>航空维修智能助手 · 项目介绍</h3>
        <button class="guide-close" @click="emitClose" aria-label="关闭">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <line x1="18" y1="6" x2="6" y2="18"></line>
            <line x1="6" y1="6" x2="18" y2="18"></line>
          </svg>
        </button>
      </div>

      <!-- 主体：左侧导航 + 右侧内容 -->
      <div class="guide-body">
        <!-- 左侧导航 -->
        <nav class="guide-nav">
          <button
            v-for="(section, index) in guideSections"
            :key="section.id"
            :class="['guide-nav-btn', { active: activeIndex === index }]"
            @click="activeIndex = index"
          >
            <span class="guide-nav-icon">{{ section.icon }}</span>
            <span class="guide-nav-text">{{ section.title }}</span>
          </button>
        </nav>

        <!-- 右侧内容 -->
        <div class="guide-content">
          <div class="guide-content-inner" v-html="renderedContent"></div>

          <!-- 底部操作栏 -->
          <div class="guide-footer">
            <button class="guide-start-btn" @click="emitClose">
              开始对话 →
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { guideSections } from '../constants/guideContent.js'
import { renderMarkdown } from '../utils/markdownRenderer.js'

const activeIndex = ref(0)

const renderedContent = computed(() => {
  const content = guideSections[activeIndex.value]?.content || ''
  return renderMarkdown(content)
})

const emit = defineEmits(['close'])

function emitClose() {
  emit('close')
}

function handleOverlayClick() {
  // 点击遮罩不关闭，防止误触
}
</script>

<style scoped>
.guide-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-color: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 20px;
}

.guide-modal {
  background-color: #ffffff;
  border-radius: 12px;
  box-shadow: 0 20px 60px rgba(0, 0, 0, 0.3);
  width: min(900px, 90vw);
  height: min(600px, 85vh);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* 头部 */
.guide-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #e0e0e0;
  flex-shrink: 0;
}

.guide-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #333;
}

.guide-close {
  background: none;
  border: none;
  cursor: pointer;
  color: #999;
  padding: 4px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.guide-close:hover {
  color: #333;
  background-color: #f0f0f0;
}

/* 主体 */
.guide-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

/* 左侧导航 */
.guide-nav {
  width: 200px;
  background-color: #f8f9fa;
  border-right: 1px solid #e0e0e0;
  padding: 12px 8px;
  display: flex;
  flex-direction: column;
  gap: 4px;
  flex-shrink: 0;
  overflow-y: auto;
}

.guide-nav-btn {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 16px;
  border: none;
  background: transparent;
  color: #555;
  font-size: 14px;
  cursor: pointer;
  border-radius: 8px;
  text-align: left;
  transition: all 0.15s ease;
}

.guide-nav-btn:hover {
  background-color: #e9ecef;
}

.guide-nav-btn.active {
  background-color: #e7f1ff;
  color: #007bff;
  border-left: 3px solid #007bff;
  margin-left: -3px;
}

.guide-nav-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.guide-nav-text {
  line-height: 1.3;
}

/* 右侧内容 */
.guide-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.guide-content-inner {
  flex: 1;
  padding: 24px 32px;
  overflow-y: auto;
  color: #333;
  line-height: 1.7;
  font-size: 14px;
}

/* Markdown 内容样式 */
.guide-content-inner :deep(p) {
  margin: 0 0 12px 0;
}

.guide-content-inner :deep(p:last-child) {
  margin-bottom: 0;
}

.guide-content-inner :deep(strong) {
  color: #1a1a1a;
  font-weight: 600;
}

.guide-content-inner :deep(ul),
.guide-content-inner :deep(ol) {
  margin: 8px 0;
  padding-left: 24px;
}

.guide-content-inner :deep(li) {
  margin-bottom: 6px;
}

.guide-content-inner :deep(table) {
  width: 100%;
  border-collapse: collapse;
  margin: 12px 0;
  font-size: 13px;
}

.guide-content-inner :deep(th),
.guide-content-inner :deep(td) {
  border: 1px solid #ddd;
  padding: 8px 12px;
  text-align: left;
}

.guide-content-inner :deep(th) {
  background-color: #f5f5f5;
  font-weight: 600;
}

.guide-content-inner :deep(tr:nth-child(even)) {
  background-color: #fafafa;
}

.guide-content-inner :deep(pre) {
  background-color: #f6f8fa;
  border-radius: 6px;
  padding: 12px 16px;
  overflow-x: auto;
  margin: 12px 0;
}

.guide-content-inner :deep(code) {
  font-family: 'SF Mono', Monaco, monospace;
  font-size: 13px;
}

.guide-content-inner :deep(pre code) {
  background: none;
  padding: 0;
}

.guide-content-inner :deep(blockquote) {
  margin: 12px 0;
  padding-left: 16px;
  border-left: 3px solid #ddd;
  color: #666;
}

/* 底部 */
.guide-footer {
  padding: 16px 32px;
  border-top: 1px solid #e0e0e0;
  display: flex;
  justify-content: flex-end;
  flex-shrink: 0;
  background-color: #fff;
}

.guide-start-btn {
  padding: 10px 24px;
  background-color: #007bff;
  color: white;
  border: none;
  border-radius: 20px;
  cursor: pointer;
  font-size: 14px;
  font-weight: 500;
  transition: background-color 0.15s ease;
}

.guide-start-btn:hover {
  background-color: #0056b3;
}

/* 移动端适配 */
@media (max-width: 600px) {
  .guide-modal {
    width: 95vw;
    height: 90vh;
    border-radius: 8px;
  }

  .guide-nav {
    width: 160px;
    padding: 8px 4px;
  }

  .guide-nav-btn {
    padding: 10px 12px;
    font-size: 13px;
  }

  .guide-content-inner {
    padding: 16px;
  }

  .guide-footer {
    padding: 12px 16px;
  }
}
</style>
