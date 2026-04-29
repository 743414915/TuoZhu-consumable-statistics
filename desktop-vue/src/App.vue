<template>
  <div class="app-layout">
    <AppSidebar
      :active-tab="activeTab"
      :service-running="state.serviceRunning"
      :log-count="state.logLines.length"
      @navigate="activeTab = $event"
      @open-config="openConfig"
    />

    <main class="main-content">
      <!-- Header bar -->
      <header class="top-bar">
        <div class="top-bar__left">
          <h1 class="top-bar__title">{{ activeTab === 'dashboard' ? '总览' : '运行日志' }}</h1>
          <StatusBadge
            v-if="activeTab === 'dashboard'"
            :variant="state.serviceRunning ? 'success' : 'muted'"
            :show-dot="true"
          >
            {{ state.serviceRunning ? '运行中' : '已停止' }}
          </StatusBadge>
        </div>
        <div class="top-bar__actions">
          <StatusBadge
            v-if="state.syncBusy"
            variant="warning"
            :show-dot="true"
          >
            同步进行中
          </StatusBadge>
          <button
            v-if="state.serviceRunning"
            class="action-btn action-btn--ghost"
            @click="invoke('manual-scan')"
            :disabled="state.syncBusy"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <path d="M21 12a9 9 0 11-2.2-5.9M21 3v6h-6" />
            </svg>
            <span>立即扫描</span>
          </button>
          <button
            class="action-btn"
            :class="state.serviceRunning ? 'action-btn--danger' : 'action-btn--primary'"
            @click="state.serviceRunning ? invoke('stop-service') : invoke('start-service')"
          >
            <svg v-if="state.serviceRunning" width="16" height="16" viewBox="0 0 24 24" fill="currentColor" stroke="none">
              <rect x="6" y="4" width="4" height="16" rx="1" />
              <rect x="14" y="4" width="4" height="16" rx="1" />
            </svg>
            <svg v-else width="16" height="16" viewBox="0 0 24 24" fill="currentColor" stroke="none">
              <polygon points="5 3 19 12 5 21 5 3" />
            </svg>
            <span>{{ state.serviceRunning ? '停止' : '启动' }}</span>
          </button>
        </div>
      </header>

      <!-- Dashboard view -->
      <div v-if="activeTab === 'dashboard'" class="content-scroll">
        <!-- Endpoints row -->
        <section class="section">
          <h2 class="section__title">桌面地址</h2>
          <div class="endpoints-grid">
            <EndpointCard
              title="推荐地址"
              :info="state.primaryEndpoint ?? fallbackEndpoint"
              :pairing-url="state.pairingUrl"
              :show-qr="true"
              @copy="invoke('copy-to-clipboard', $event)"
            />
            <EndpointCard
              title="局域网地址"
              :info="state.lanEndpoint ?? fallbackEndpoint"
              :pairing-url="''"
              @copy="invoke('copy-to-clipboard', $event)"
            />
          </div>
        </section>

        <!-- Warnings banner -->
        <Transition name="slide-up">
          <section v-if="state.warningCount > 0" class="section">
            <div class="warnings-banner">
              <div class="warnings-banner__header">
                <span class="warnings-banner__icon">
                  <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
                    <path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
                    <line x1="12" y1="9" x2="12" y2="13" />
                    <line x1="12" y1="17" x2="12.01" y2="17" />
                  </svg>
                </span>
                <span class="warnings-banner__label">{{ state.warningCount }} 条告警</span>
              </div>
              <pre class="warnings-banner__text mono">{{ state.warningText }}</pre>
            </div>
          </section>
        </Transition>

        <!-- Draft list -->
        <section class="section">
          <h2 class="section__title">待处理草稿</h2>
          <DraftList
            :text="state.previewText"
            :count="state.pendingDrafts"
          />
        </section>
      </div>

      <!-- Logs view -->
      <LogViewer v-if="activeTab === 'logs'" :lines="state.logLines" />

      <!-- Status bar -->
      <footer class="status-bar mono">
        <span>{{ state.lastSyncLabel ? '最后同步：' + state.lastSyncLabel : '尚未同步' }}</span>
        <span class="status-bar__divider"></span>
        <span :class="{ 'status-bar__watch--active': state.serviceRunning }">
          {{ state.watchStatus?.summary ?? '未启用' }}
        </span>
      </footer>
    </main>

    <!-- Config dialog -->
    <ConfigDialog
      :visible="showConfig"
      :config="configData"
      @close="showConfig = false"
      @save="onConfigSave"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted, computed } from 'vue'
import AppSidebar from './components/AppSidebar.vue'
import StatusBadge from './components/StatusBadge.vue'
import EndpointCard from './components/EndpointCard.vue'
import DraftList from './components/DraftList.vue'
import LogViewer from './components/LogViewer.vue'
import ConfigDialog from './components/ConfigDialog.vue'
import type { GuiConfig } from './components/ConfigDialog.vue'
import type { StateSnapshot, EndpointInfo } from './composables/useIpc'
import { defaultSnapshot } from './composables/useIpc'

const activeTab = ref('dashboard')

const state = reactive<StateSnapshot>({ ...defaultSnapshot })
const showConfig = ref(false)
const configData = ref<GuiConfig>({
  agentRoot: '',
  port: 8823,
  maxAgeDays: 7,
  useSample: false,
  gcodeRoots: [],
})
let cleanup: (() => void) | null = null

const fallbackEndpoint: EndpointInfo = {
  label: '暂无可用地址',
  url: '—',
  reachable: false,
}

async function invoke<T>(channel: string, ...args: any[]): Promise<T | null> {
  const api = (window as any).electronAPI
  if (!api) return null
  try {
    return (await api.invoke(channel, ...args)) as T
  } catch {
    return null
  }
}

async function openConfig() {
  const cfg = await invoke<GuiConfig>('get-config')
  if (cfg) configData.value = cfg
  showConfig.value = true
}

async function onConfigSave(partial: Partial<GuiConfig>) {
  await invoke('update-config', partial)
  showConfig.value = false
}

onMounted(async () => {
  const api = (window as any).electronAPI
  if (!api) return

  try {
    const initial = await api.invoke('get-state')
    if (initial) Object.assign(state, initial)
  } catch {
    // use defaults
  }

  if (api.onStateUpdate) {
    cleanup = api.onStateUpdate((snapshot: StateSnapshot) => {
      Object.assign(state, snapshot)
    })
  }
})

onUnmounted(() => {
  cleanup?.()
})
</script>

<style scoped>
.app-layout {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  background: var(--bg-root);
}

/* ── Top bar ── */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-md) var(--space-xl);
  border-bottom: 1px solid var(--border-subtle);
  background: var(--bg-surface);
  flex-shrink: 0;
  -webkit-app-region: drag;
}
.top-bar__left {
  display: flex;
  align-items: center;
  gap: var(--space-md);
}
.top-bar__title {
  font-size: var(--font-size-lg);
  font-weight: 700;
  color: var(--text-primary);
}
.top-bar__actions {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  -webkit-app-region: no-drag;
}

/* Action buttons */
.action-btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 8px 18px;
  border-radius: var(--radius-md);
  font-size: var(--font-size-sm);
  font-weight: 600;
  font-family: var(--font-sans);
  border: none;
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}
.action-btn:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
.action-btn--primary {
  background: var(--accent);
  color: var(--text-on-accent);
}
.action-btn--primary:hover {
  background: var(--accent-hover);
  box-shadow: var(--shadow-glow);
}
.action-btn--danger {
  background: var(--danger-muted);
  color: var(--danger);
}
.action-btn--danger:hover {
  background: var(--danger);
  color: #fff;
}
.action-btn--ghost {
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-default);
}
.action-btn--ghost:hover {
  background: var(--bg-overlay);
  color: var(--text-primary);
  border-color: var(--border-strong);
}

/* ── Content ── */
.content-scroll {
  flex: 1;
  overflow-y: auto;
  padding: var(--space-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
}

/* ── Section ── */
.section {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.section__title {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
  padding-left: var(--space-xs);
}

/* ── Endpoints grid ── */
.endpoints-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: var(--space-md);
}

/* ── Warnings banner ── */
.warnings-banner {
  background: var(--warning-muted);
  border: 1px solid rgba(240, 184, 77, 0.2);
  border-radius: var(--radius-lg);
  padding: var(--space-lg);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.warnings-banner__header {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  color: var(--warning);
}
.warnings-banner__icon {
  display: flex;
}
.warnings-banner__label {
  font-size: var(--font-size-base);
  font-weight: 600;
}
.warnings-banner__text {
  color: var(--warning);
  opacity: 0.75;
  font-size: var(--font-size-xs);
  white-space: pre-wrap;
  word-break: break-word;
  line-height: 1.6;
}

/* ── Status bar ── */
.status-bar {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: 6px var(--space-xl);
  background: var(--bg-surface);
  border-top: 1px solid var(--border-subtle);
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  flex-shrink: 0;
}
.status-bar__divider {
  width: 1px;
  height: 12px;
  background: var(--border-default);
}
.status-bar__watch--active {
  color: var(--success);
}
</style>
