import { ref, onMounted, onUnmounted } from 'vue'

export interface EndpointInfo {
  label: string
  url: string
  reachable: boolean
}

export interface WatchStatus {
  level: string
  summary: string
  detail: string
}

export interface StateSnapshot {
  serviceRunning: boolean
  syncBusy: boolean
  watchStatus: WatchStatus | null
  pendingDrafts: number
  warningCount: number
  lastSyncLabel: string
  previewText: string
  warningText: string
  primaryEndpoint: EndpointInfo | null
  lanEndpoint: EndpointInfo | null
  pairingUrl: string
  logLines: string[]
}

export const defaultSnapshot: StateSnapshot = {
  serviceRunning: false,
  syncBusy: false,
  watchStatus: null,
  pendingDrafts: 0,
  warningCount: 0,
  lastSyncLabel: '',
  previewText: '',
  warningText: '',
  primaryEndpoint: null,
  lanEndpoint: null,
  pairingUrl: '',
  logLines: [],
}

export function useIpc() {
  const state = ref<StateSnapshot>({ ...defaultSnapshot })
  let cleanup: (() => void) | null = null

  onMounted(async () => {
    const api = (window as any).electronAPI
    if (!api) return

    try {
      const initial = await api.invoke('get-state')
      if (initial) state.value = initial
    } catch { /* use defaults */ }

    if (api.onStateUpdate) {
      cleanup = api.onStateUpdate((snapshot: StateSnapshot) => {
        state.value = snapshot
      })
    }
  })

  onUnmounted(() => {
    cleanup?.()
  })

  return { state }
}
