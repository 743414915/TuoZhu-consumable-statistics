<template>
  <div class="draft-panel">
    <div class="draft-panel__header">
      <div class="draft-panel__header-left">
        <h3 class="draft-panel__title">待处理草稿</h3>
        <StatusBadge :variant="count > 0 ? 'accent' : 'muted'">
          {{ count }} 条
        </StatusBadge>
      </div>
      <span v-if="count > 0" class="draft-panel__hint mono">
        手机端拉取时自动同步
      </span>
    </div>

    <!-- Draft cards -->
    <div v-if="count > 0" class="draft-panel__cards">
      <TransitionGroup name="slide-up">
        <div
          v-for="(draft, i) in parsedDrafts"
          :key="draft.jobId"
          class="draft-card"
          :style="{ transitionDelay: `${i * 40}ms` }"
        >
          <div class="draft-card__left">
            <div class="draft-card__model-icon">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
                <path d="M21 16V8a2 2 0 00-1-1.73l-7-4a2 2 0 00-2 0l-7 4A2 2 0 003 8v8a2 2 0 001 1.73l7 4a2 2 0 002 0l7-4A2 2 0 0021 16z" />
              </svg>
            </div>
          </div>
          <div class="draft-card__body">
            <span class="draft-card__model">{{ draft.modelName }}</span>
            <div class="draft-card__meta">
              <span class="draft-card__tag">{{ draft.material }}</span>
              <span class="draft-card__tag draft-card__tag--grams">{{ draft.grams }}g</span>
            </div>
            <code class="draft-card__job-id mono">{{ draft.jobId }}</code>
          </div>
        </div>
      </TransitionGroup>
    </div>

    <!-- Empty state -->
    <div v-else class="draft-panel__empty">
      <div class="draft-panel__empty-icon">
        <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.4" stroke-linecap="round" stroke-linejoin="round">
          <path d="M14 2H6a2 2 0 00-2 2v16a2 2 0 002 2h12a2 2 0 002-2V8z" />
          <polyline points="14 2 14 8 20 8" />
          <line x1="16" y1="13" x2="8" y2="13" />
          <line x1="16" y1="17" x2="8" y2="17" />
        </svg>
      </div>
      <p class="draft-panel__empty-title">暂无待处理草稿</p>
      <p class="draft-panel__empty-desc">服务运行后，手机拉取的结果会显示在这里</p>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import StatusBadge from './StatusBadge.vue'

interface ParsedDraft {
  modelName: string
  material: string
  grams: string
  jobId: string
}

const props = defineProps<{
  text: string
  count: number
}>()

const parsedDrafts = computed<ParsedDraft[]>(() => {
  if (!props.text.trim()) return []
  return props.text
    .split('\n\n')
    .map((block) => {
      const lines = block.split('\n')
      const extract = (prefix: string) =>
        lines
          .find((l) => l.startsWith(prefix))
          ?.replace(prefix, '')
          ?.trim() ?? '—'

      return {
        modelName: extract('模型：'),
        material: extract('材料：'),
        grams: extract('用量：').replace('g', ''),
        jobId: extract('任务 ID：'),
      }
    })
    .filter((d) => d.jobId !== '—')
})
</script>

<style scoped>
.draft-panel {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
  transition: border-color var(--duration-fast);
}
.draft-panel:hover {
  border-color: var(--border-default);
}

.draft-panel__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-lg);
  border-bottom: 1px solid var(--border-subtle);
}
.draft-panel__header-left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.draft-panel__title {
  font-size: var(--font-size-base);
  font-weight: 700;
}
.draft-panel__hint {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}

/* Draft cards */
.draft-panel__cards {
  padding: var(--space-sm);
  display: flex;
  flex-direction: column;
  gap: 2px;
  max-height: 400px;
  overflow-y: auto;
}

.draft-card {
  display: flex;
  gap: var(--space-md);
  padding: var(--space-md);
  border-radius: var(--radius-md);
  background: transparent;
  transition: all var(--duration-fast) var(--ease-out);
  cursor: default;
}
.draft-card:hover {
  background: var(--bg-elevated);
}

.draft-card__left {
  flex-shrink: 0;
}
.draft-card__model-icon {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-sm);
  background: var(--accent-muted);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
}

.draft-card__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.draft-card__model {
  font-size: var(--font-size-base);
  font-weight: 600;
  color: var(--text-primary);
}
.draft-card__meta {
  display: flex;
  gap: var(--space-sm);
  flex-wrap: wrap;
}
.draft-card__tag {
  display: inline-block;
  padding: 2px 10px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-xs);
  font-weight: 500;
  background: var(--bg-overlay);
  color: var(--text-secondary);
}
.draft-card__tag--grams {
  background: var(--accent-muted);
  color: var(--accent);
}
.draft-card__job-id {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  margin-top: 2px;
}

/* Empty */
.draft-panel__empty {
  padding: 40px var(--space-lg);
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
}
.draft-panel__empty-icon {
  color: var(--text-muted);
  opacity: 0.5;
  margin-bottom: var(--space-md);
}
.draft-panel__empty-title {
  font-size: var(--font-size-base);
  color: var(--text-secondary);
  font-weight: 500;
  margin-bottom: 4px;
}
.draft-panel__empty-desc {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
}
</style>
