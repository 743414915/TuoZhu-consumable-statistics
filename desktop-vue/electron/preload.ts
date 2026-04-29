import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('electronAPI', {
  invoke: (channel: string, ...args: any[]): Promise<any> => {
    const allowed = ['get-state', 'start-service', 'stop-service', 'manual-scan', 'update-config', 'open-directory', 'copy-to-clipboard']
    if (allowed.includes(channel)) return ipcRenderer.invoke(channel, ...args)
    return Promise.reject(new Error(`IPC channel not allowed: ${channel}`))
  },
  onStateUpdate: (callback: (snapshot: any) => void): (() => void) => {
    const handler = (_event: any, snapshot: any) => callback(snapshot)
    ipcRenderer.on('state-update', handler)
    return () => { ipcRenderer.removeListener('state-update', handler) }
  },
})
