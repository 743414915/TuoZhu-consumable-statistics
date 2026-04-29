<template>
  <span class="badge" :class="`badge--${variant}`">
    <span v-if="showDot" class="badge__dot"></span>
    <slot />
  </span>
</template>

<script setup lang="ts">
withDefaults(
  defineProps<{
    variant: 'success' | 'warning' | 'danger' | 'muted' | 'accent' | 'info'
    showDot?: boolean
  }>(),
  {
    showDot: false,
  },
)
</script>

<style scoped>
.badge {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 4px 14px;
  border-radius: var(--radius-full);
  font-size: var(--font-size-sm);
  font-weight: 600;
  letter-spacing: 0.02em;
  white-space: nowrap;
  line-height: 1.4;
  transition: all var(--duration-fast) var(--ease-out);
}

.badge__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* Variants */
.badge--success {
  background: var(--success-muted);
  color: var(--success);
}
.badge--success .badge__dot {
  background: var(--success);
  box-shadow: 0 0 6px rgba(107, 175, 138, 0.5);
}

.badge--warning {
  background: var(--warning-muted);
  color: var(--warning);
}
.badge--warning .badge__dot {
  background: var(--warning);
  box-shadow: 0 0 6px rgba(240, 184, 77, 0.4);
}

.badge--danger {
  background: var(--danger-muted);
  color: var(--danger);
}
.badge--danger .badge__dot {
  background: var(--danger);
}

.badge--info {
  background: var(--info-muted);
  color: var(--info);
}
.badge--info .badge__dot {
  background: var(--info);
}

.badge--muted {
  background: var(--bg-overlay);
  color: var(--text-secondary);
}

.badge--accent {
  background: var(--accent-muted);
  color: var(--accent);
}
.badge--accent .badge__dot {
  background: var(--accent);
}
</style>
