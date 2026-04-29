<template>
  <div class="ep-card" :class="{ 'ep-card--active': info.reachable }">
    <div v-if="info.reachable" class="ep-card__glow-line"></div>

    <div class="ep-card__inner">
      <div class="ep-card__header">
        <h3 class="ep-card__title">{{ title }}</h3>
        <StatusBadge
          :variant="info.reachable ? 'success' : 'muted'"
          :show-dot="info.reachable"
        >
          {{ info.reachable ? '可连接' : '未检测' }}
        </StatusBadge>
      </div>

      <p class="ep-card__label">{{ info.label }}</p>

      <div class="ep-card__url-box">
        <code class="ep-card__url mono">{{ info.url }}</code>
        <button
          class="ep-card__copy-btn"
          :class="{ 'ep-card__copy-btn--done': copied }"
          @click="onCopy"
          :title="copied ? '已复制' : '复制地址'"
        >
          <svg v-if="!copied" width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <rect x="9" y="9" width="13" height="13" rx="2" ry="2" />
            <path d="M5 15H4a2 2 0 01-2-2V4a2 2 0 012-2h9a2 2 0 012 2v1" />
          </svg>
          <svg v-else width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="20 6 9 17 4 12" />
          </svg>
          <span>{{ copied ? '已复制' : '复制' }}</span>
        </button>
      </div>

      <!-- QR section -->
      <Transition name="fade">
        <div v-if="showQr && pairingUrl && !isLocalhostUrl" class="ep-card__qr" key="qr">
          <div class="ep-card__qr-divider"></div>
          <div class="ep-card__qr-body">
            <img
              v-if="qrDataUrl"
              :src="qrDataUrl"
              class="ep-card__qr-img"
              alt="配对二维码"
            />
            <div v-else-if="qrError" class="ep-card__qr-error">
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" /><line x1="12" y1="9" x2="12" y2="13" /><line x1="12" y1="17" x2="12.01" y2="17" /></svg>
              <span>二维码生成失败</span>
            </div>
            <div class="ep-card__qr-info">
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <rect x="5" y="2" width="14" height="20" rx="2" ry="2" />
                <line x1="12" y1="18" x2="12.01" y2="18" />
              </svg>
              <span>手机扫码配对</span>
            </div>
          </div>
        </div>

        <!-- Localhost or no-endpoint state -->
        <div v-else-if="showQr && !pairingUrl" class="ep-card__qr ep-card__qr--notice" key="no-url">
          <div class="ep-card__qr-divider"></div>
          <div class="ep-card__qr-notice">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" /></svg>
            <span>请先启动服务，再扫码配对</span>
          </div>
        </div>
      </Transition>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, computed, onMounted } from 'vue'
import QRCode from 'qrcode'
import StatusBadge from './StatusBadge.vue'
import type { EndpointInfo } from '../composables/useIpc'

const props = defineProps<{
  title: string
  info: EndpointInfo
  pairingUrl: string
  showQr?: boolean
}>()

const emit = defineEmits<{ copy: [url: string] }>()

const qrDataUrl = ref('')
const qrError = ref(false)
const copied = ref(false)

const isLocalhostUrl = computed(() => {
  if (!props.pairingUrl) return false
  try {
    const host = new URL(props.pairingUrl).hostname
    return host === 'localhost' || host === '127.0.0.1' || host === '::1' || host === '0.0.0.0'
  } catch {
    return false
  }
})

function onCopy() {
  if (!props.info.url || props.info.url === '—') return
  emit('copy', props.info.url)
  copied.value = true
  setTimeout(() => (copied.value = false), 1800)
}

async function renderQr() {
  qrDataUrl.value = ''
  qrError.value = false
  if (!props.showQr || !props.pairingUrl || isLocalhostUrl.value) return

  try {
    const dataUrl = await QRCode.toDataURL(props.pairingUrl, {
      width: 280,
      margin: 2,
      color: { dark: '#000000', light: '#FFFFFF' },
    })
    qrDataUrl.value = dataUrl
  } catch (e) {
    console.error('[EndpointCard] QR error:', e)
    qrError.value = true
  }
}

watch(() => props.pairingUrl, renderQr, { immediate: true })
onMounted(renderQr)
</script>

<style scoped>
.ep-card {
  background: var(--bg-surface);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-lg);
  overflow: hidden;
  position: relative;
  transition: all var(--duration-normal) var(--ease-out);
}
.ep-card:hover {
  border-color: var(--border-default);
}
.ep-card--active {
  border-color: rgba(107, 175, 138, 0.12);
}

.ep-card__glow-line {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 2px;
  background: linear-gradient(90deg, transparent, var(--success), transparent);
  opacity: 0.6;
}

.ep-card__inner {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
  padding: var(--space-lg);
}

.ep-card__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.ep-card__title {
  font-size: var(--font-size-base);
  font-weight: 700;
  color: var(--text-primary);
}
.ep-card__label {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  line-height: 1.4;
}

/* URL row */
.ep-card__url-box {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.ep-card__url {
  flex: 1;
  background: var(--bg-input);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  font-size: var(--font-size-sm);
  color: var(--text-secondary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  transition: border-color var(--duration-fast);
}

.ep-card__copy-btn {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  flex-shrink: 0;
  background: var(--bg-overlay);
  color: var(--text-secondary);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 9px 14px;
  cursor: pointer;
  font-size: var(--font-size-sm);
  font-family: var(--font-sans);
  font-weight: 500;
  transition: all var(--duration-fast) var(--ease-out);
}
.ep-card__copy-btn:hover {
  background: var(--accent-muted);
  color: var(--accent);
  border-color: var(--accent);
}
.ep-card__copy-btn--done {
  background: var(--success-muted);
  color: var(--success);
  border-color: transparent;
}

/* QR */
.ep-card__qr {
  display: flex;
  flex-direction: column;
}
.ep-card__qr-divider {
  height: 1px;
  background: var(--border-subtle);
  margin-bottom: var(--space-md);
}
.ep-card__qr-body {
  display: flex;
  align-items: center;
  gap: var(--space-lg);
}
.ep-card__qr-img {
  width: 140px;
  height: 140px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  background: #FFFFFF;
  image-rendering: pixelated;
}
.ep-card__qr-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--space-sm);
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

/* Error */
.ep-card__qr-error {
  width: 140px;
  height: 140px;
  border-radius: var(--radius-md);
  border: 1px solid var(--border-subtle);
  background: var(--bg-input);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: var(--space-sm);
  color: var(--text-muted);
  font-size: var(--font-size-xs);
}

/* Notice (service not started) */
.ep-card__qr--notice {
  padding-bottom: 0;
}
.ep-card__qr-notice {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
  color: var(--text-muted);
  font-size: var(--font-size-sm);
}
</style>
