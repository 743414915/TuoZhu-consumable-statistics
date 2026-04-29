<template>
  <div class="log-panel">
    <div class="log-panel__header">
      <div class="log-panel__header-left">
        <h3 class="log-panel__title">运行日志</h3>
        <StatusBadge variant="muted">{{ lines.length }} 行</StatusBadge>
      </div>
      <button
        v-if="lines.length > 0"
        class="log-panel__clear-btn"
        @click="autoScroll = !autoScroll"
        :title="autoScroll ? '已开启自动滚动' : '已暂停自动滚动'"
      >
        <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline v-if="autoScroll" points="6 9 12 15 18 9" />
          <polyline v-else points="6 15 12 9 18 15" />
        </svg>
        <span>{{ autoScroll ? '自动滚动' : '已暂停' }}</span>
      </button>
    </div>

    <div ref="scroller" class="log-panel__body">
      <div v-if="lines.length > 0" class="log-panel__lines">
        <TransitionGroup name="fade">
          <div
            v-for="(line, i) in lines"
            :key="i"
            class="log-line"
            :class="`log-line--${logLevel(line)}`"
          >
            <span class="log-line__ts mono">{{ tsPart(line) }}</span>
            <span class="log-line__msg">{{ msgPart(line) }}</span>
          </div>
        </TransitionGroup>
      </div>
      <div v-else class="log-panel__empty">
        <div class="log-panel__empty-icon">
          <svg width="36" height="36" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="4 17 10 11 4 5" />
            <line x1="12" y1="19" x2="20" y2="19" />
          </svg>
        </div>
        <p>暂无日志输出</p>
        <span>启动服务后日志会实时显示在这里</span>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick } from 'vue'
import StatusBadge from './StatusBadge.vue'

const props = defineProps<{ lines: string[] }>()

const scroller = ref<HTMLElement | null>(null)
const autoScroll = ref(true)

function tsPart(line: string): string {
  const m = line.match(/^\[([^\]]+)\]/)
  return m ? m[1] : ''
}

function msgPart(line: string): string {
  return line.replace(/^\[[^\]]+\]\s*/, '')
}

function logLevel(line: string): 'info' | 'dim' | 'warn' | 'error' {
  const lower = line.toLowerCase()
  if (lower.includes('error') || lower.includes('失败') || lower.includes('异常')) return 'error'
  if (lower.includes('warning') || lower.includes('warn') || lower.includes('告警')) return 'warn'
  if (line.includes('[监听]') || line.includes('[同步]') || line.includes('[刷新]')) return 'dim'
  return 'info'
}

watch(
  () => props.lines.length,
  async () => {
    await nextTick()
    if (autoScroll.value && scroller.value) {
      scroller.value.scrollTop = scroller.value.scrollHeight
    }
  },
)
</script>

<style scoped>
.log-panel {
  flex: 1;
  display: flex;
  flex-direction: column;
  background: var(--bg-surface);
  border-top: 1px solid var(--border-subtle);
  min-height: 0;
}

.log-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-xl);
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
}
.log-panel__header-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.log-panel__title {
  font-size: var(--font-size-base);
  font-weight: 700;
}
.log-panel__clear-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  background: transparent;
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  color: var(--text-muted);
  font-size: var(--font-size-xs);
  font-family: var(--font-sans);
  padding: 4px 10px;
  cursor: pointer;
  transition: all var(--duration-fast);
}
.log-panel__clear-btn:hover {
  background: var(--bg-overlay);
  color: var(--text-secondary);
}

.log-panel__body {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
  background: var(--bg-root);
}

.log-panel__lines {
  padding: var(--space-sm) 0;
}

.log-line {
  display: flex;
  gap: var(--space-md);
  padding: 3px var(--space-xl);
  font-family: var(--font-mono);
  font-size: var(--font-size-xs);
  line-height: 1.7;
  transition: background var(--duration-fast);
}
.log-line:hover {
  background: var(--bg-elevated);
}

.log-line__ts {
  flex-shrink: 0;
  color: var(--text-muted);
  opacity: 0.6;
  min-width: 70px;
}

.log-line__msg {
  color: var(--text-secondary);
  white-space: pre-wrap;
  word-break: break-all;
  flex: 1;
}

/* Log levels */
.log-line--info .log-line__msg { color: var(--text-secondary); }
.log-line--dim .log-line__msg { color: var(--text-muted); }
.log-line--warn .log-line__msg { color: var(--warning); }
.log-line--error .log-line__msg { color: var(--danger); }
.log-line--warn { background: rgba(240, 184, 77, 0.03); }
.log-line--error { background: rgba(201, 74, 62, 0.04); }

/* Empty */
.log-panel__empty {
  padding: 48px 24px;
  display: flex;
  flex-direction: column;
  align-items: center;
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  gap: 4px;
}
.log-panel__empty-icon {
  margin-bottom: var(--space-sm);
  opacity: 0.4;
}
</style>
