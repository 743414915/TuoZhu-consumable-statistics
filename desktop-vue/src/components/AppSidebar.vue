<template>
  <aside class="sidebar">
    <div class="sidebar__brand">
      <div class="sidebar__logo">
        <svg width="28" height="28" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.8" stroke-linecap="round" stroke-linejoin="round">
          <path d="M12 2L2 7l10 5 10-5-10-5z" />
          <path d="M2 17l10 5 10-5" />
          <path d="M2 12l10 5 10-5" />
        </svg>
      </div>
      <div class="sidebar__brand-text">
        <span class="sidebar__app-name">拓竹桌面同步</span>
        <span class="sidebar__version">v0.2.0</span>
      </div>
    </div>

    <nav class="sidebar__nav">
      <button
        class="sidebar__nav-item"
        :class="{ 'sidebar__nav-item--active': activeTab === 'dashboard' }"
        @click="$emit('navigate', 'dashboard')"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <rect x="3" y="3" width="7" height="7" rx="1" />
          <rect x="14" y="3" width="7" height="7" rx="1" />
          <rect x="3" y="14" width="7" height="7" rx="1" />
          <rect x="14" y="14" width="7" height="7" rx="1" />
        </svg>
        <span>总览</span>
      </button>
      <button
        class="sidebar__nav-item"
        :class="{ 'sidebar__nav-item--active': activeTab === 'logs' }"
        @click="$emit('navigate', 'logs')"
      >
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <polyline points="4 17 10 11 4 5" />
          <line x1="12" y1="19" x2="20" y2="19" />
        </svg>
        <span>运行日志</span>
        <span v-if="logCount > 0" class="sidebar__badge mono">{{ logCount }}</span>
      </button>
    </nav>

    <div class="sidebar__footer">
      <div class="sidebar__status" :class="`sidebar__status--${serviceRunning ? 'on' : 'off'}`">
        <span class="sidebar__status-dot"></span>
        <span class="sidebar__status-label">{{ serviceRunning ? '服务运行中' : '服务已停止' }}</span>
      </div>
      <button class="sidebar__config-btn" @click="$emit('open-config')" title="配置">
        <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <circle cx="12" cy="12" r="3" />
          <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42" />
        </svg>
        <span>设置</span>
      </button>
    </div>
  </aside>
</template>

<script setup lang="ts">
defineProps<{
  activeTab: string
  serviceRunning: boolean
  logCount: number
}>()

defineEmits<{
  navigate: [tab: string]
  'open-config': []
}>()
</script>

<style scoped>
.sidebar {
  width: var(--sidebar-width);
  height: 100%;
  background: var(--bg-surface);
  border-right: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  flex-shrink: 0;
  user-select: none;
}

/* Brand */
.sidebar__brand {
  display: flex;
  align-items: center;
  gap: var(--space-md);
  padding: var(--space-xl) var(--space-lg);
  border-bottom: 1px solid var(--border-subtle);
}
.sidebar__logo {
  width: 40px;
  height: 40px;
  border-radius: var(--radius-md);
  background: var(--accent-muted);
  color: var(--accent);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.sidebar__brand-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}
.sidebar__app-name {
  font-size: var(--font-size-base);
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: -0.01em;
}
.sidebar__version {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  font-family: var(--font-mono);
}

/* Nav */
.sidebar__nav {
  flex: 1;
  padding: var(--space-md);
  display: flex;
  flex-direction: column;
  gap: var(--space-xs);
  overflow-y: auto;
}
.sidebar__nav-item {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 10px 14px;
  border-radius: var(--radius-md);
  background: transparent;
  border: none;
  color: var(--text-secondary);
  font-size: var(--font-size-base);
  font-family: var(--font-sans);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
  position: relative;
}
.sidebar__nav-item:hover {
  background: var(--bg-overlay);
  color: var(--text-primary);
}
.sidebar__nav-item--active {
  background: var(--accent-muted);
  color: var(--accent);
}
.sidebar__nav-item--active::before {
  content: '';
  position: absolute;
  left: 0;
  top: 50%;
  transform: translateY(-50%);
  width: 3px;
  height: 18px;
  border-radius: 0 3px 3px 0;
  background: var(--accent);
}
.sidebar__badge {
  margin-left: auto;
  background: var(--accent-muted);
  color: var(--accent);
  font-size: var(--font-size-xs);
  padding: 2px 8px;
  border-radius: var(--radius-full);
  min-width: 22px;
  text-align: center;
}

/* Footer */
.sidebar__footer {
  padding: var(--space-md) var(--space-lg);
  border-top: 1px solid var(--border-subtle);
  display: flex;
  flex-direction: column;
  gap: var(--space-sm);
}
.sidebar__status {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: var(--bg-overlay);
}
.sidebar__status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;
  background: var(--text-muted);
  transition: all var(--duration-normal) var(--ease-out);
}
.sidebar__status--on .sidebar__status-dot {
  background: var(--success);
  box-shadow: 0 0 6px var(--success);
  animation: pulse 2s ease-in-out infinite;
}
.sidebar__status-label {
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
}
.sidebar__status--on .sidebar__status-label {
  color: var(--success);
}
.sidebar__config-btn {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  padding: 8px 12px;
  border-radius: var(--radius-md);
  background: transparent;
  border: 1px solid var(--border-subtle);
  color: var(--text-muted);
  font-size: var(--font-size-sm);
  font-family: var(--font-sans);
  cursor: pointer;
  transition: all var(--duration-fast) var(--ease-out);
}
.sidebar__config-btn:hover {
  background: var(--bg-overlay);
  color: var(--text-primary);
  border-color: var(--border-default);
}
</style>
