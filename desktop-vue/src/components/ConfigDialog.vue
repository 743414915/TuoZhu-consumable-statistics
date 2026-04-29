<template>
  <Transition name="fade">
    <div v-if="visible" class="config-overlay" @click.self="$emit('close')">
      <Transition name="slide-up">
        <div v-if="visible" class="config-dialog">
          <div class="config-dialog__header">
            <h2>服务配置</h2>
            <button class="config-dialog__close" @click="$emit('close')">
              <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                <line x1="18" y1="6" x2="6" y2="18" />
                <line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          </div>

          <div class="config-dialog__body">
            <!-- Network section -->
            <div class="config-section">
              <h4 class="config-section__title">网络</h4>
              <label class="field">
                <span class="field__label">监听端口</span>
                <input
                  class="field__input mono"
                  type="number"
                  :value="local.port"
                  @input="onPort"
                  min="1024"
                  max="65535"
                />
                <span class="field__hint">手机端填写的同步地址端口需与此一致（默认 8823）</span>
              </label>
            </div>

            <!-- Sync section -->
            <div class="config-section">
              <h4 class="config-section__title">同步</h4>
              <label class="field">
                <span class="field__label">文件保留天数</span>
                <input
                  class="field__input mono"
                  type="number"
                  :value="local.maxAgeDays"
                  @input="onMaxAge"
                  min="1"
                  max="90"
                />
                <span class="field__hint">超过该期限的旧 G-code 和草稿会被自动跳过</span>
              </label>

              <label class="field field--switch">
                <div class="field__switch-row">
                  <span class="field__label">示例数据模式</span>
                  <button
                    class="toggle"
                    :class="{ 'toggle--on': local.useSample }"
                    @click="local.useSample = !local.useSample"
                  >
                    <span class="toggle__knob"></span>
                  </button>
                </div>
                <span class="field__hint">关闭后使用真实 G-code 解析引擎</span>
              </label>
            </div>

            <!-- Directories section -->
            <div class="config-section">
              <h4 class="config-section__title">G-code 监听目录</h4>
              <div class="field">
                <div v-if="local.gcodeRoots.length" class="dir-list">
                  <div
                    v-for="(dir, i) in local.gcodeRoots"
                    :key="i"
                    class="dir-row"
                  >
                    <code class="dir-row__path mono">{{ dir }}</code>
                    <button class="dir-row__remove" @click="removeDir(i)" title="移除">
                      <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <line x1="18" y1="6" x2="6" y2="18" />
                        <line x1="6" y1="6" x2="18" y2="18" />
                      </svg>
                    </button>
                  </div>
                </div>
                <div class="dir-add">
                  <input
                    class="field__input mono"
                    v-model="newDir"
                    placeholder="输入目录路径后点击添加…"
                    @keydown.enter.prevent="addDir"
                  />
                  <button
                    class="btn btn--small btn--ghost"
                    @click="addDir"
                    :disabled="!newDir.trim()"
                  >
                    添加
                  </button>
                </div>
                <span class="field__hint">
                  Bambu Studio 默认切片输出到用户桌面或
                  <code class="mono">%LOCALAPPDATA%\Temp\bamboo_model</code>
                </span>
              </div>
            </div>
          </div>

          <div class="config-dialog__footer">
            <button class="btn btn--ghost" @click="$emit('close')">取消</button>
            <button class="btn btn--primary" @click="onSave">保存并应用</button>
          </div>
        </div>
      </Transition>
    </div>
  </Transition>
</template>

<script setup lang="ts">
import { ref, reactive, watch } from 'vue'

export interface GuiConfig {
  agentRoot: string
  port: number
  maxAgeDays: number
  useSample: boolean
  gcodeRoots: string[]
}

const props = defineProps<{ visible: boolean; config: GuiConfig }>()
const emit = defineEmits<{ close: []; save: [partial: Partial<GuiConfig>] }>()

const local = reactive<GuiConfig>({
  agentRoot: '',
  port: 8823,
  maxAgeDays: 7,
  useSample: false,
  gcodeRoots: [],
})
const newDir = ref('')

watch(
  () => props.visible,
  (v) => {
    if (v) {
      Object.assign(local, props.config)
      newDir.value = ''
    }
  },
)

function onPort(e: Event) {
  const v = parseInt((e.target as HTMLInputElement).value, 10)
  if (!isNaN(v) && v > 0 && v < 65536) local.port = v
}

function onMaxAge(e: Event) {
  const v = parseInt((e.target as HTMLInputElement).value, 10)
  if (!isNaN(v) && v > 0) local.maxAgeDays = v
}

function addDir() {
  const d = newDir.value.trim()
  if (d && !local.gcodeRoots.includes(d)) local.gcodeRoots.push(d)
  newDir.value = ''
}

function removeDir(i: number) {
  local.gcodeRoots.splice(i, 1)
}

function onSave() {
  emit('save', {
    port: local.port,
    maxAgeDays: local.maxAgeDays,
    useSample: local.useSample,
    gcodeRoots: [...local.gcodeRoots],
  })
}
</script>

<style scoped>
.config-overlay {
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 100;
  backdrop-filter: blur(6px);
  -webkit-backdrop-filter: blur(6px);
}

.config-dialog {
  background: var(--bg-elevated);
  border: 1px solid var(--border-default);
  border-radius: var(--radius-xl);
  width: 520px;
  max-height: 82vh;
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg), 0 0 40px rgba(0, 0, 0, 0.3);
}

.config-dialog__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--space-lg) var(--space-xl);
  border-bottom: 1px solid var(--border-subtle);
  flex-shrink: 0;
}
.config-dialog__header h2 {
  font-size: var(--font-size-xl);
  font-weight: 700;
}
.config-dialog__close {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: var(--radius-sm);
  display: flex;
  transition: all var(--duration-fast);
}
.config-dialog__close:hover {
  background: var(--bg-overlay);
  color: var(--text-primary);
}

.config-dialog__body {
  padding: var(--space-xl);
  display: flex;
  flex-direction: column;
  gap: var(--space-xl);
  overflow-y: auto;
  flex: 1;
}

.config-dialog__footer {
  display: flex;
  justify-content: flex-end;
  gap: var(--space-sm);
  padding: var(--space-md) var(--space-xl);
  border-top: 1px solid var(--border-subtle);
  flex-shrink: 0;
}

/* Section */
.config-section {
  display: flex;
  flex-direction: column;
  gap: var(--space-md);
}
.config-section__title {
  font-size: var(--font-size-xs);
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

/* Fields */
.field {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.field__label {
  font-size: var(--font-size-sm);
  font-weight: 600;
  color: var(--text-primary);
}
.field__input {
  background: var(--bg-input);
  border: 1px solid var(--border-subtle);
  border-radius: var(--radius-sm);
  padding: 10px 14px;
  color: var(--text-primary);
  font-size: var(--font-size-sm);
  font-family: var(--font-sans);
  outline: none;
  transition: border-color var(--duration-fast);
}
.field__input:focus {
  border-color: var(--accent);
}
.field__input::placeholder {
  color: var(--text-muted);
}
.field__hint {
  font-size: var(--font-size-xs);
  color: var(--text-muted);
  line-height: 1.5;
}
.field__hint code {
  background: var(--bg-overlay);
  padding: 1px 6px;
  border-radius: 3px;
  font-size: 11px;
}

/* Switch row */
.field__switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

/* Directory list */
.dir-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.dir-row {
  display: flex;
  align-items: center;
  gap: var(--space-sm);
}
.dir-row__path {
  flex: 1;
  font-size: var(--font-size-xs);
  background: var(--bg-input);
  padding: 8px 12px;
  border-radius: var(--radius-sm);
  border: 1px solid var(--border-subtle);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  color: var(--text-secondary);
}
.dir-row__remove {
  background: none;
  border: none;
  color: var(--text-muted);
  cursor: pointer;
  padding: 6px;
  border-radius: var(--radius-sm);
  display: flex;
  transition: all var(--duration-fast);
  flex-shrink: 0;
}
.dir-row__remove:hover {
  background: var(--danger-muted);
  color: var(--danger);
}
.dir-add {
  display: flex;
  gap: var(--space-sm);
}
.dir-add .field__input {
  flex: 1;
}

/* Toggle */
.toggle {
  width: 46px;
  height: 26px;
  border-radius: 13px;
  background: var(--bg-overlay);
  border: 1px solid var(--border-default);
  cursor: pointer;
  position: relative;
  transition: all var(--duration-fast) var(--ease-out);
  flex-shrink: 0;
}
.toggle--on {
  background: var(--accent);
  border-color: var(--accent);
}
.toggle__knob {
  position: absolute;
  top: 3px;
  left: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  background: #fff;
  transition: transform var(--duration-fast) var(--ease-out);
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.3);
}
.toggle--on .toggle__knob {
  transform: translateX(20px);
}

/* Buttons */
.btn {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 9px 22px;
  border-radius: var(--radius-sm);
  font-size: var(--font-size-sm);
  font-weight: 600;
  font-family: var(--font-sans);
  cursor: pointer;
  border: none;
  transition: all var(--duration-fast) var(--ease-out);
}
.btn--small {
  padding: 7px 16px;
  font-size: var(--font-size-xs);
}
.btn--primary {
  background: var(--accent);
  color: #fff;
}
.btn--primary:hover {
  background: var(--accent-hover);
  box-shadow: var(--shadow-glow);
}
.btn--ghost {
  background: transparent;
  color: var(--text-secondary);
  border: 1px solid var(--border-default);
}
.btn--ghost:hover {
  background: var(--bg-overlay);
  color: var(--text-primary);
}
.btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}
</style>
