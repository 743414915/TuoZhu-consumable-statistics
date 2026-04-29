import { app, BrowserWindow, ipcMain, shell, clipboard } from 'electron'
import http from 'http'
import fs from 'fs'
import path from 'path'
import os from 'os'
import { spawn, ChildProcess } from 'child_process'
import chokidar from 'chokidar'

// ── Types ──
interface GuiConfig { agentRoot: string; port: number; maxAgeDays: number; useSample: boolean; gcodeRoots: string[] }
interface StateSnapshot {
  serviceRunning: boolean; syncBusy: boolean
  watchStatus: { level: string; summary: string; detail: string }
  pendingDrafts: number; warningCount: number; lastSyncLabel: string
  previewText: string; warningText: string
  primaryEndpoint: { label: string; url: string; reachable: boolean } | null
  lanEndpoint: { label: string; url: string; reachable: boolean } | null
  pairingUrl: string; logLines: string[]
}
type WatchLevel = 'IDLE' | 'INFO' | 'SUCCESS' | 'WARNING' | 'ERROR'
interface EndpointCandidate { label: string; url: string; score: number; kind: 'TAILSCALE' | 'LAN' | 'OTHER'; reachable: boolean }

// ── Default config ──
function resolveAgentRoot(): string {
  if (app.isPackaged) return path.join(process.resourcesPath, 'desktop-agent')
  let cursor: string = app.getAppPath()
  for (let d = 0; d < 8 && cursor; d++) {
    try { if (fs.statSync(path.join(cursor, 'desktop-agent')).isDirectory()) return path.join(cursor, 'desktop-agent') } catch { /* up */ }
    const parent = path.dirname(cursor); if (parent === cursor) break; cursor = parent
  }
  return path.join(app.getAppPath(), '..', 'desktop-agent')
}
function defaultGcodeRoots(): string[] {
  const r: string[] = []
  if (process.env.USERPROFILE) r.push(path.join(process.env.USERPROFILE, 'Desktop'))
  if (process.env.LOCALAPPDATA) r.push(path.join(process.env.LOCALAPPDATA, 'Temp', 'bamboo_model'))
  return r
}
const defaultConfig: GuiConfig = { agentRoot: resolveAgentRoot(), port: 8823, maxAgeDays: 7, useSample: false, gcodeRoots: defaultGcodeRoots() }

// ── Module state ──
let config: GuiConfig = { ...defaultConfig }
let serviceRunning = false, syncProcess: ChildProcess | null = null, queuedFullSync = false, queuedExplicitPaths: string[] = []
let watchStatus: { level: WatchLevel; summary: string; detail: string } = { level: 'IDLE', summary: '未启用', detail: '桌面监听线程尚未启动' }
let watcher: chokidar.FSWatcher | null = null, httpServer: http.Server | null = null
let logLines: string[] = [], mainWindow: BrowserWindow | null = null, refreshTimer: ReturnType<typeof setInterval> | null = null
const MAX_LOG = 600

// ── Utilities ──
function log(line: string): void {
  const ts = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  logLines.push(`[${ts}] ${line}`)
  if (logLines.length > MAX_LOG) logLines = logLines.slice(-MAX_LOG)
  console.log(`[${ts}] ${line}`)
}
function ensureDir(p: string): void { try { fs.mkdirSync(p, { recursive: true }) } catch { /* ok */ } }
function readText(p: string): string { try { if (fs.existsSync(p)) return fs.readFileSync(p, 'utf-8') } catch { /* ok */ } return '' }
function writeText(p: string, c: string): void { ensureDir(path.dirname(p)); fs.writeFileSync(p, c, 'utf-8') }
function jsonSafe<T>(raw: string, fb: T): T { try { const t = raw.trim(); return t ? JSON.parse(t) : fb } catch { return fb } }
function escJson(v: string): string { return v.replace(/\\/g, '\\\\').replace(/"/g, '\\"').replace(/\r/g, '\\r').replace(/\n/g, '\\n').replace(/\t/g, '\\t') }

// ── Config persistence ──
function cfgPath(): string { return path.join(config.agentRoot, 'state', 'gui-config.json') }
function loadConfig(): void {
  const raw = readText(cfgPath()); if (!raw) return
  try {
    const l = JSON.parse(raw) as Partial<GuiConfig>
    if (typeof l.agentRoot === 'string' && l.agentRoot) config.agentRoot = l.agentRoot
    if (typeof l.port === 'number' && l.port > 0 && l.port < 65536) config.port = l.port
    if (typeof l.maxAgeDays === 'number' && l.maxAgeDays > 0) config.maxAgeDays = l.maxAgeDays
    if (typeof l.useSample === 'boolean') config.useSample = l.useSample
    if (Array.isArray(l.gcodeRoots) && l.gcodeRoots.length > 0) config.gcodeRoots = l.gcodeRoots.filter((r): r is string => typeof r === 'string' && r.length > 0)
  } catch { /* use defaults */ }
}
function persistConfig(): void { try { writeText(cfgPath(), JSON.stringify(config, null, 2)) } catch (e) { log('save config failed: ' + e) } }

// ── PowerShell sync ──
function psExe(): string {
  const sr = process.env.SystemRoot
  if (sr) { const c = path.join(sr, 'System32', 'WindowsPowerShell', 'v1.0', 'powershell.exe'); if (fs.existsSync(c)) return c }
  return 'powershell.exe'
}
function psQuote(s: string): string { return `'${s.replace(/'/g, "''")}'` }
function psArray(items: string[]): string { return `@(${items.map(psQuote).join(',')})` }
function buildPsCmd(script: string, args: string[]): string {
  const pre = '$utf8=[System.Text.UTF8Encoding]::new($false);[Console]::InputEncoding=$utf8;[Console]::OutputEncoding=$utf8;$OutputEncoding=$utf8;'
  const lit = psQuote(script)
  const parts = args.map(a => {
    if (/^-[A-Za-z][A-Za-z0-9]*$/.test(a)) return a
    if (a.startsWith('@(')) return a
    return psQuote(a)
  })
  return `${pre}& ${lit} ${parts.join(' ')}`
}
function syncArgs(explicit: string[] = []): string[] {
  const a = ['-MaxFileAgeDays', String(config.maxAgeDays)]
  if (config.useSample) { a.push('-UseSample') } else {
    a.push('-UseBambuGcode')
    if (explicit.length) { a.push('-GcodePaths', psArray(explicit.map(p => path.resolve(p)))) }
    else if (config.gcodeRoots.length) { a.push('-GcodeSearchRoots', psArray(config.gcodeRoots)) }
  }
  return a
}
function isSyncBusy(): boolean { return syncProcess !== null && syncProcess.exitCode === null }

function spawnSync(reason: string, explicitPaths: string[] = []): boolean {
  if (isSyncBusy()) {
    if (explicitPaths.length) { for (const p of explicitPaths) queuedExplicitPaths.push(path.resolve(p)); log(`[监听] 同步进行中，已排队 ${explicitPaths.length} 个文件。`) }
    else { queuedFullSync = true; log(`后台同步仍在运行，已排队（${reason}）。`) }
    return false
  }
  const cmd = buildPsCmd(path.join(config.agentRoot, 'run-sync-agent.ps1'), syncArgs(explicitPaths))
  try {
    const child = spawn(psExe(), ['-NoProfile', '-ExecutionPolicy', 'Bypass', '-Command', cmd], { cwd: path.dirname(config.agentRoot), stdio: ['ignore', 'pipe', 'pipe'], windowsHide: true })
    syncProcess = child
    log(explicitPaths.length ? `后台同步已启动（${reason}），目标 ${explicitPaths.length} 个文件。` : `后台同步已启动（${reason}）。`)
    child.stdout?.setEncoding('utf-8'); child.stdout?.on('data', (c: string) => { for (const l of c.split(/\r?\n/)) if (l.trim()) log('[同步] ' + l) })
    child.stderr?.setEncoding('utf-8'); child.stderr?.on('data', (c: string) => { for (const l of c.split(/\r?\n/)) if (l.trim()) log('[同步/stderr] ' + l) })
    child.on('close', (ec) => { log(`后台同步已结束，退出码 ${ec}。`); if (syncProcess === child) syncProcess = null; drainSyncQueue() })
    child.on('error', (e) => { log(`后台同步异常：${e.message}`); if (syncProcess === child) syncProcess = null; drainSyncQueue() })
    return true
  } catch (e) { log(`启动后台同步失败：${e}`); return false }
}

function drainSyncQueue(): void {
  if (isSyncBusy()) return
  if (queuedExplicitPaths.length) { const e = [...queuedExplicitPaths]; queuedExplicitPaths = []; spawnSync('queued-watch', e) }
  else if (queuedFullSync) { queuedFullSync = false; spawnSync('queued') }
}

// ── HTTP server ──
function setCors(res: http.ServerResponse): void { res.setHeader('Access-Control-Allow-Origin', '*'); res.setHeader('Access-Control-Allow-Methods', 'GET, POST, OPTIONS'); res.setHeader('Access-Control-Allow-Headers', 'Content-Type') }
function sendJson(res: http.ServerResponse, code: number, body: string): void { const p = Buffer.from(body, 'utf-8'); res.writeHead(code, { 'Content-Type': 'application/json; charset=utf-8', 'Content-Length': String(p.length) }); res.end(p) }
function readBody(req: http.IncomingMessage): Promise<string> { return new Promise((resolve) => { const c: Buffer[] = []; req.on('data', (d) => c.push(d)); req.on('end', () => resolve(Buffer.concat(c).toString('utf-8'))); req.on('error', () => resolve('')) }) }

function writeConfirmation(receipt: { externalJobId: string; confirmedAt: number; targetRollId?: number | null }): void {
  const dp = path.join(config.agentRoot, 'outbox'); const cp = path.join(dp, 'confirmation-log.json')
  const existing = jsonSafe<object[]>(readText(cp), [])
  const updated = existing.filter((item: any) => item.externalJobId !== receipt.externalJobId)
  updated.push(receipt); ensureDir(dp)
  const tmp = path.join(dp, 'confirmation-log.json.tmp')
  writeText(tmp, JSON.stringify(updated))
  try { fs.renameSync(tmp, cp) } catch { fs.copyFileSync(tmp, cp); try { fs.unlinkSync(tmp) } catch { /* ok */ } }
}

function handleHealth(_r: http.IncomingMessage, res: http.ServerResponse): void {
  setCors(res); sendJson(res, 200, JSON.stringify({ status: 'ok', source: 'DESKTOP_AGENT', serverTime: Date.now(), syncBusy: isSyncBusy() }))
}

function handlePull(_r: http.IncomingMessage, res: http.ServerResponse): void {
  setCors(res); spawnSync('pull')
  const ob = path.join(config.agentRoot, 'outbox', 'desktop-outbox.json'), sp = path.join(config.agentRoot, 'state', 'state.json')
  const outboxRaw = readText(ob), stateRaw = readText(sp); const stateObj = jsonSafe<any>(stateRaw, {})
  const draftJobsJson = outboxRaw.trim() || '[]'; const warnings: string[] = Array.isArray(stateObj.warnings) ? stateObj.warnings : []
  const updatedAt = typeof stateObj.updatedAt === 'number' ? stateObj.updatedAt : Date.now()
  const inputMode = typeof stateObj.inputMode === 'string' ? stateObj.inputMode : undefined
  const busy = isSyncBusy(); const hasCache = fs.existsSync(ob) || fs.existsSync(sp)
  let status: string, msg: string
  if (!hasCache && busy) { msg = '桌面端正在准备同步数据，请稍后再试。'; status = 'IDLE' }
  else if (busy) { msg = '已返回当前缓存，桌面端正在后台刷新。'; status = 'SUCCESS' }
  else { const dc = jsonSafe<any[]>((outboxRaw.trim() || '[]'), []).length; msg = `桌面同步完成，待确认任务 ${dc} 条。`; status = 'SUCCESS' }
  const body = JSON.stringify({
    status, source: 'DESKTOP_AGENT', syncedAt: updatedAt, message: msg, draftJobs: JSON.parse(draftJobsJson),
    warnings, inputMode: inputMode ?? null, syncBusy: busy,
  })
  sendJson(res, 200, body)
}

async function handleConfirm(req: http.IncomingMessage, res: http.ServerResponse): Promise<void> {
  setCors(res)
  let receipt: any
  try { receipt = JSON.parse(await readBody(req)) } catch { sendJson(res, 400, '{"status":"ERROR","message":"Invalid JSON body."}'); return }
  if (!receipt?.externalJobId || String(receipt.externalJobId).trim() === '') { sendJson(res, 400, '{"status":"ERROR","message":"externalJobId is required."}'); return }
  writeConfirmation({ externalJobId: String(receipt.externalJobId).trim(), confirmedAt: typeof receipt.confirmedAt === 'number' ? receipt.confirmedAt : Date.now(), targetRollId: receipt.targetRollId ?? null })
  spawnSync('confirm')
  sendJson(res, 200, JSON.stringify({ status: 'SUCCESS', source: 'DESKTOP_AGENT', syncedAt: Date.now(), message: 'Confirmation recorded: ' + escJson(String(receipt.externalJobId).trim()) }))
}

function createServer(): http.Server {
  return http.createServer((req, res) => {
    const m = (req.method || '').toUpperCase(), u = req.url || '/'
    if (m === 'OPTIONS') { setCors(res); res.writeHead(204); res.end(); return }
    if (m === 'GET' && u === '/health') handleHealth(req, res)
    else if (m === 'GET' && u === '/api/sync/pull') handlePull(req, res)
    else if (m === 'POST' && u === '/api/sync/confirm') handleConfirm(req, res)
    else { setCors(res); sendJson(res, 404, '{"status":"ERROR","message":"Not found."}') }
  })
}
function startHttp(): void {
  if (httpServer) return; httpServer = createServer()
  httpServer.listen(config.port, '0.0.0.0', () => { log(`桌面同步服务已启动：http://0.0.0.0:${config.port}/`); log('GET  /health /api/sync/pull / POST /api/sync/confirm') })
  httpServer.on('error', (e) => { log('HTTP error: ' + e.message); httpServer = null })
}
function stopHttp(): void { if (!httpServer) return; try { httpServer.close() } catch { /* ok */ } httpServer = null }

// ── G-code watcher ──
const pendingFiles = new Map<string, ReturnType<typeof setTimeout>>()

function onGcodeFile(filePath: string, reason: string): void {
  if (!filePath.toLowerCase().endsWith('.gcode')) return
  const np = path.resolve(filePath)
  const t = pendingFiles.get(np); if (t) clearTimeout(t)
  let s: fs.Stats | null = null; try { s = fs.statSync(np) } catch { return }; if (!s.isFile()) return
  const fp = { size: s.size, mtimeMs: s.mtimeMs }
  watchStatus = { level: 'INFO', summary: '稳定性判定中', detail: path.basename(np) + '，' + reason }
  pendingFiles.set(np, setTimeout(() => {
    pendingFiles.delete(np)
    let ns: fs.Stats | null = null; try { ns = fs.statSync(np) } catch { watchStatus = { level: 'SUCCESS', summary: '监听中', detail: '等待新的切片文件' }; return }
    if (!ns.isFile()) { watchStatus = { level: 'SUCCESS', summary: '监听中', detail: '等待新的切片文件' }; return }
    if (ns.size === fp.size && ns.mtimeMs === fp.mtimeMs) {
      watchStatus = { level: 'SUCCESS', summary: '已捕获切片', detail: path.basename(np) }
      log('[监听] 文件稳定，准备同步：' + np); spawnSync('watcher', [np])
    } else { watchStatus = { level: 'SUCCESS', summary: '监听中', detail: '等待新的切片文件' } }
  }, 1200))
}

function startWatcher(): void {
  stopWatcher()
  if (config.useSample) { watchStatus = { level: 'WARNING', summary: '示例模式', detail: '当前未监听真实切片目录' }; return }
  const roots = config.gcodeRoots.filter(r => r && r.trim())
  if (!roots.length) { watchStatus = { level: 'WARNING', summary: '未启用', detail: '尚未配置 G-code 监听目录' }; return }
  watchStatus = { level: 'INFO', summary: '初始化中', detail: '正在准备 G-code 监听目录' }
  watcher = chokidar.watch(roots, {
    ignored: (p: string) => /[/\\]Metadata([/\\]|$)/i.test(p),
    depth: 6, ignoreInitial: true, persistent: true, awaitWriteFinish: false,
  })
  watcher.on('add', (fp: string) => onGcodeFile(fp, '检测到切片文件'))
  watcher.on('change', (fp: string) => onGcodeFile(fp, '切片文件变更'))
  watcher.on('ready', () => { watchStatus = { level: 'SUCCESS', summary: '监听中', detail: '正在等待新的切片文件' }; log('[监听] 已启动，共 ' + roots.length + ' 个目录。') })
  watcher.on('error', (e) => { watchStatus = { level: 'ERROR', summary: '监听错误', detail: e.message }; log('[监听] 错误：' + e.message) })
}

function stopWatcher(): void {
  for (const t of pendingFiles.values()) clearTimeout(t); pendingFiles.clear()
  if (watcher) { try { watcher.close() } catch { /* ok */ } watcher = null }
  watchStatus = { level: 'IDLE', summary: '未启用', detail: '桌面监听线程已停止' }
}

// ── Endpoint discovery ──
function isTailscale(name: string, ip: string): boolean {
  if (name.toLowerCase().includes('tailscale')) return true
  const p = ip.split('.'); if (p.length !== 4) return false
  const a = parseInt(p[0], 10), b = parseInt(p[1], 10); return a === 100 && b >= 64 && b <= 127
}
const RFC1918 = /^(10\.|172\.(1[6-9]|2\d|3[01])\.|192\.168\.)/
function isSiteLocal(ip: string): boolean { return RFC1918.test(ip) }
function scoreIface(name: string, ip: string): number {
  const lo = name.toLowerCase(); let s = 0
  if (isTailscale(name, ip)) s += 500
  if (isSiteLocal(ip)) s += 200
  if (/wlan|wi-fi|wifi|wireless/.test(lo)) s += 150
  if (/ethernet|^lan\b/.test(lo)) s += 120
  if (ip.startsWith('192.168.')) s += 80; else if (ip.startsWith('10.')) s += 40; else if (ip.startsWith('172.')) s += 20
  if (/virtual|vmware|hyper-v|vethernet|wsl|^tap|^tun|vpn|bluetooth|sectap|atrust/.test(lo)) { s -= 250 }
  else if (/veth|docker|^br-/.test(lo)) s -= 200
  return s
}
function endpointKind(name: string, ip: string): 'TAILSCALE' | 'LAN' | 'OTHER' { return isTailscale(name, ip) ? 'TAILSCALE' : isSiteLocal(ip) ? 'LAN' : 'OTHER' }

function listEndpoints(): EndpointCandidate[] {
  const c: EndpointCandidate[] = []; const p = config.port
  for (const [n, addrs] of Object.entries(os.networkInterfaces())) {
    if (!addrs) continue
    for (const a of addrs) {
      if (a.family !== 'IPv4' || a.internal || a.address.startsWith('169.254.')) continue
      const score = scoreIface(n, a.address), kind = endpointKind(n, a.address)
      const label = kind === 'TAILSCALE' ? '推荐使用（Tailscale/MagicDNS）' : kind === 'LAN' ? '局域网兼容地址' : '其他可用网络地址'
      c.push({ label, url: `http://${a.address}:${p}`, score, kind, reachable: false })
    }
  }
  c.sort((a, b) => b.score - a.score)
  const seen = new Set<string>(); return c.filter(x => !seen.has(x.url) && seen.add(x.url))
}

async function probe(url: string): Promise<boolean> {
  if (!serviceRunning) return false
  const ctl = new AbortController(); const t = setTimeout(() => ctl.abort(), 300)
  try { const r = await fetch(`${url}/health`, { signal: ctl.signal, headers: { Accept: 'application/json' } }); if (!r.ok) return false; const txt = await r.text(); return txt.includes('"status":"ok"') && txt.includes('"source":"DESKTOP_AGENT"') } catch { return false } finally { clearTimeout(t) }
}

async function selectEndpoints(): Promise<{ primary: EndpointCandidate | null; lan: EndpointCandidate | null }> {
  const raw = listEndpoints()
  const cands = await Promise.all(raw.map(async c => { const r = await probe(c.url); return { ...c, reachable: r } }))
  const primary = cands.length ? cands.reduce((best, cur) => {
    const cp = (cur.kind === 'TAILSCALE' ? 1000 : cur.kind === 'LAN' ? 500 : 0) + (cur.reachable ? 200 : 0) + cur.score
    const bp = (best.kind === 'TAILSCALE' ? 1000 : best.kind === 'LAN' ? 500 : 0) + (best.reachable ? 200 : 0) + best.score
    return cp > bp ? cur : best
  }) : null
  const lan = cands.filter(c => c.kind === 'LAN' && c.url !== primary?.url).sort((a, b) => ((b.reachable ? 1000 : 0) + b.score) - ((a.reachable ? 1000 : 0) + a.score))[0] || null
  return { primary, lan }
}

// ── State snapshot ──
function fmtTime(ms: number): string { const d = new Date(ms); const p = (n: number) => String(n).padStart(2, '0'); return `${p(d.getMonth()+1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}` }
function buildPreview(jobs: any[]): string {
  if (!jobs.length) return '暂无待处理草稿。服务运行后，手机可拉取的结果会显示在这里。'
  return jobs.map(j => `模型：${j.modelName || '-'}\n用量：${j.estimatedUsageGrams ?? '0'}g\n材料：${j.targetMaterial || '未指定'}\n任务 ID：${j.externalJobId || '-'}`).join('\n\n')
}

async function snapshot(): Promise<StateSnapshot> {
  try {
    const ob = readText(path.join(config.agentRoot, 'outbox', 'desktop-outbox.json'))
    const sr = readText(path.join(config.agentRoot, 'state', 'state.json'))
    const stateObj = jsonSafe<any>(sr, {})
    const jobs = jsonSafe<any[]>((ob.trim() || '[]'), [])
    const warnings: string[] = Array.isArray(stateObj.warnings) ? stateObj.warnings : []
    const ua: number = typeof stateObj.updatedAt === 'number' ? stateObj.updatedAt : 0

    let primary: EndpointCandidate | null = null
    let lan: EndpointCandidate | null = null
    try {
      const eps = await selectEndpoints()
      primary = eps.primary
      lan = eps.lan
    } catch (e) {
      log('端点发现失败: ' + e)
    }

    return {
      serviceRunning,
      syncBusy: isSyncBusy(),
      watchStatus,
      pendingDrafts: jobs.length,
      warningCount: warnings.length,
      lastSyncLabel: ua > 0 ? fmtTime(ua) : '尚未同步',
      previewText: buildPreview(jobs),
      warningText: warnings.length ? warnings.join('\n') : '暂无警告。',
      primaryEndpoint: primary ? { label: primary.label, url: primary.url, reachable: primary.reachable } : null,
      lanEndpoint: lan ? { label: lan.label, url: lan.url, reachable: lan.reachable } : null,
      pairingUrl: primary ? primary.url : '',
      logLines: [...logLines],
    }
  } catch (e) {
    log('snapshot 整体异常: ' + e)
    return {
      serviceRunning, syncBusy: isSyncBusy(), watchStatus: null,
      pendingDrafts: 0, warningCount: 0,
      lastSyncLabel: '读取异常', previewText: '', warningText: '',
      primaryEndpoint: null, lanEndpoint: null,
      pairingUrl: '', logLines: [...logLines],
    }
  }
}

// ── IPC handlers ──
function registerIpc(): void {
  ipcMain.handle('get-state', async () => snapshot())
  ipcMain.handle('start-service', () => { if (serviceRunning) return; try { startHttp(); startWatcher(); serviceRunning = true; spawnSync('startup'); log('服务已启动。') } catch (e) { log('启动失败：' + e); serviceRunning = false } })
  ipcMain.handle('stop-service', () => { if (!serviceRunning) return; log('正在停止服务...'); stopHttp(); stopWatcher(); if (syncProcess) { try { syncProcess.kill() } catch { /* ok */ } syncProcess = null } queuedExplicitPaths = []; queuedFullSync = false; serviceRunning = false })
  ipcMain.handle('manual-scan', () => { log('[扫描] 手动触发...'); spawnSync('manual') })
  ipcMain.handle('get-config', () => ({
    agentRoot: config.agentRoot,
    port: config.port,
    maxAgeDays: config.maxAgeDays,
    useSample: config.useSample,
    gcodeRoots: [...config.gcodeRoots],
  }))
  ipcMain.handle('update-config', (_e, partial: Partial<GuiConfig>) => {
    if (!partial || typeof partial !== 'object') return
    if (typeof partial.agentRoot === 'string' && partial.agentRoot) config.agentRoot = partial.agentRoot
    if (typeof partial.port === 'number' && partial.port > 0 && partial.port < 65536) config.port = partial.port
    if (typeof partial.maxAgeDays === 'number' && partial.maxAgeDays > 0) config.maxAgeDays = partial.maxAgeDays
    if (typeof partial.useSample === 'boolean') config.useSample = partial.useSample
    if (Array.isArray(partial.gcodeRoots) && partial.gcodeRoots.length > 0) config.gcodeRoots = partial.gcodeRoots.filter((r): r is string => typeof r === 'string' && r.length > 0)
    persistConfig(); log('[配置] 已更新。'); if (serviceRunning) { stopWatcher(); startWatcher() }
  })
  ipcMain.handle('open-directory', async (_e, p: string) => { if (!p) return; try { ensureDir(p); await shell.openPath(p) } catch (e) { log('无法打开目录：' + e) } })
  ipcMain.handle('copy-to-clipboard', (_e, t: string) => { if (t) clipboard.writeText(t) })
}

// ── Refresh timer ──
function startRefresh(): void { if (refreshTimer) return; refreshTimer = setInterval(async () => { if (!mainWindow || mainWindow.isDestroyed()) return; try { mainWindow.webContents.send('state-update', await snapshot()) } catch (e) { log('[刷新] 失败：' + e) } }, 2500) }
function stopRefresh(): void { if (refreshTimer) { clearInterval(refreshTimer); refreshTimer = null } }

// ── Window & lifecycle ──
function createWindow(): void {
  mainWindow = new BrowserWindow({ width: 1200, height: 800, minWidth: 960, minHeight: 680, backgroundColor: '#0D0D0D', title: '拓竹桌面同步', show: false, webPreferences: { nodeIntegration: false, contextIsolation: true, sandbox: false, preload: path.join(__dirname, 'preload.js') } })
  if (process.env.VITE_DEV_SERVER_URL) mainWindow.loadURL(process.env.VITE_DEV_SERVER_URL)
  else mainWindow.loadFile(path.join(app.getAppPath(), 'dist', 'index.html'))
  mainWindow.once('ready-to-show', () => mainWindow?.show())
  mainWindow.on('closed', () => { mainWindow = null })
}

app.whenReady().then(() => { loadConfig(); registerIpc(); createWindow(); startRefresh(); app.on('activate', () => { if (BrowserWindow.getAllWindows().length === 0) createWindow() }) })
app.on('window-all-closed', () => { stopRefresh(); stopHttp(); stopWatcher(); if (syncProcess) { try { syncProcess.kill() } catch { /* ok */ } syncProcess = null } if (process.platform !== 'darwin') app.quit() })
