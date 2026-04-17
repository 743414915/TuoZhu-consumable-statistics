# 桌面端使用说明

## 当前主方案

当前正式方案是 `桌面 GUI + 内置 HTTP 服务 + PowerShell 同步引擎`。

- 你平时只需要启动 `TuoZhuDesktopSync.exe`。
- 不再需要单独手动运行旧的 `start-sync-server.ps1`。
- 旧方案已经移动到 `desktop-agent/legacy/`，仅保留历史参考。

## 产物位置

- 桌面端目录：`dist/desktop/TuoZhuDesktopSync`
- 启动文件：`dist/desktop/TuoZhuDesktopSync/TuoZhuDesktopSync.exe`

## 首次使用

1. 打开 `TuoZhuDesktopSync.exe`。
2. 确认 `Desktop Agent Root` 指向同目录下的 `desktop-agent`。
3. 模式选择 `真实切片 G-code`。
4. 在 `G-code Search Roots` 中保留或添加你的切片缓存目录。

推荐保留：

- `%USERPROFILE%\Desktop`
- `%LOCALAPPDATA%\Temp\bamboo_model`

5. 端口先保持默认 `8823`。
6. `Max File Age (days)` 默认用 `7` 即可。

## 日常操作

### 启动服务

点击 `启动服务` 后，桌面端会同时做三件事：

- 启动内置 HTTP 服务。
- 启动 G-code 监听线程。
- 执行一次后台同步，刷新草稿任务。

### 手动扫描近切片

点击 `手动扫描近切片` 后，会立即扫描最近生成的 `.gcode`，适合在监听漏抓时手动补扫。

### 扫码配对

点击 `扫码配对` 后，手机端可直接扫描桌面端二维码，把同步地址写入手机设置。

### 停止服务

点击 `停止服务` 后，会同时停止：

- 内置 HTTP 服务
- G-code 监听线程
- 当前后台同步进程

## 地址显示逻辑

桌面端顶部会显示两类地址：

- `主推荐地址`
  - 如果检测到 Tailscale 地址，优先显示 Tailscale。
  - 否则显示当前最合适的局域网地址。
- `局域网备用地址`
  - 只在存在可用局域网地址时显示。

推荐规则的目标是：

- 同时支持局域网使用。
- 当你已经打通 Tailscale 时，优先给出可跨网络访问的地址。

## 网络说明

### 局域网模式

适用于手机和电脑在同一 Wi-Fi 下的场景。

要求：

- 手机和电脑可互相访问。
- Windows 允许桌面程序监听该端口。

### Tailscale 模式

适用于不在同一局域网时的跨网络访问。

要求：

- 手机和电脑都已安装并登录 Tailscale。
- 两端已经互通。

当 Tailscale 可用时，桌面端会优先推荐 Tailscale 地址，手机端直接使用该地址即可。

## G-code 监听逻辑

桌面端当前不是只扫一次目录，而是包含完整监听链路：

1. 监听 `bamboo_model` 及其最近出现的子目录。
2. 发现新目录或 `Metadata` 变化时，触发回补扫描。
3. 对新 `.gcode` 做稳定性判定，避免文件尚未写完就被解析。
4. 稳定后再触发同步引擎生成草稿任务。

这套机制专门针对 Bambu Studio 的临时缓存目录会延迟出现、会被覆盖、软件关闭后会清空的特点。

## 材料识别范围

当前只保留三种材料：

- `PLA Basic`
- `PETG Basic`
- `PLA Silk`

如果 `.gcode` 头中的多个材料字段互相冲突，系统会：

- 按预设优先级选择一个结果继续生成草稿。
- 把冲突信息写入 `Warnings`。

## 日志与状态

桌面端界面会持续显示：

- 服务状态
- 主推荐地址
- 局域网备用地址
- G-code 监听状态
- 待确认任务数
- 警告数
- 最近同步时间
- 实时日志

如果手机端提示超时，优先检查：

1. 桌面端是否已点击 `启动服务`。
2. 顶部推荐地址是否和手机配置一致。
3. 手机是否能访问桌面端 `/health`。

## 相关代码

- GUI 入口：`desktop-app/src/main/java/com/tuozhu/desktop/DesktopSyncApp.java`
- 内置服务：`desktop-app/src/main/java/com/tuozhu/desktop/EmbeddedSyncService.java`
- G-code 监听：`desktop-app/src/main/java/com/tuozhu/desktop/GcodeWatchService.java`
- 同步引擎：`desktop-agent/run-sync-agent.ps1`
