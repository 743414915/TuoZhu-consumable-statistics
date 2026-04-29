# 拓竹耗材管家

面向 **Bambu Lab A1 mini（无 AMS）** 的耗材管理工具，由 Android 手机端 + Windows 桌面端两部分协同工作。

## 系统要求

| 组件 | 运行环境 |
|---|---|
| **桌面端** (`output/desktop/`) | Windows 10/11 x64 |
| **Android 端** (`output/android/`) | Android 8.0+，需摄像头（扫码配对用） |
| **网络** | 手机和电脑需在同一局域网，或通过 Tailscale 组网 |

## 快速开始

### 1. 启动桌面端

将 `output/desktop/` 整个目录复制到 Windows 电脑上（**不要只复制 .exe**），双击 `TuoZhuDesktopSync.exe`：

1. 点击 **启动服务** — 桌面端开始监听 Bambu Studio 切片输出
2. 在"推荐地址"卡片中查看本机地址
3. 手机扫描二维码完成配对

### 2. 安装 Android 端

将 `output/android/app-debug.apk` 传输到 Android 手机并安装：

1. 打开应用 → 切换到**同步**页
2. 点击 **扫码配对**，扫描桌面端的二维码
3. 点击 **立即拉取** 获取桌面端的打印草稿
4. 在卷库中管理耗材卷，确认打印任务后自动扣减

### 3. 切片自动同步

在 Bambu Studio 中切片后，gcode 文件输出到以下目录即可被桌面端自动检测：

- `桌面`（默认）
- `%LOCALAPPDATA%\Temp\bamboo_model`（Bambu Studio 缓存目录）

如需添加其他目录，在桌面端点击左下角 **设置** → "G-code 监听目录"。

---

## output/ 目录说明

```
output/
├── android/
│   └── app-debug.apk              Android 应用安装包
└── desktop/
    ├── TuoZhuDesktopSync.exe      桌面端主程序
    ├── resources/
    │   ├── app.asar               Vue 前端
    │   └── desktop-agent/         G-code 解析引擎
    └── ...                         Electron 运行库
```

换电脑时复制整个 `output/` 目录即可，无需安装任何依赖。

---

## 源码结构

```
├── app/                            Android 客户端（Kotlin + Jetpack Compose + Room）
│   └── src/main/java/.../ui/
│       ├── screen/                 4 个页面：Overview / Inventory / Sync / History
│       ├── ConsumableViewModel.kt  全局状态管理
│       └── theme/                  配色与字体
├── desktop-vue/                    Windows 桌面端（Electron + Vue 3 + TypeScript）
│   ├── electron/main.ts           主进程（HTTP 服务、G-code 监听、同步调度）
│   ├── src/                       渲染进程（Vue 组件）
│   └── src/components/            UI 组件
├── desktop-agent/                  PowerShell G-code 解析引擎
│   ├── run-sync-agent.ps1          主脚本（解析 .gcode → 生成草稿）
│   ├── outbox/                     桌面端输出（草稿 + 确认回执）
│   └── state/                      运行状态文件
├── scripts/                        构建脚本
└── docs/                           架构与协议文档
```

---

## 从源码构建

详见 `CLAUDE.md`，摘要：

**Android**：
```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1
```
APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`

**桌面端**：
```bash
cd desktop-vue
npm install
npm run electron:build
```
成品输出到 `dist-electron/win-unpacked/`

### 构建依赖

- **Android**：JDK 17、Android SDK（项目内 `.tools/` 已包含）
- **桌面端**：Node.js 18+、npm（需自行安装）
