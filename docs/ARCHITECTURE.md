## 总览

当前主方案由 Android 客户端、Windows 桌面 GUI、PowerShell 同步引擎三层组成。

- Android 客户端负责耗材卷管理、待确认打印任务、打印历史和同步设置。
- Windows 桌面 GUI 负责展示状态、监听切片缓存、提供内置 HTTP 服务、推荐可连接地址。
- PowerShell 同步引擎负责读取 `.gcode`、映射材料与克重、维护 `outbox/state`。

旧的独立 PowerShell HTTP 服务已经归档，不再是主链路。

## 模块职责

### `app/`

- 技术栈：Kotlin、Jetpack Compose、Room、StateFlow。
- 主要职责：
  - 管理耗材卷、活动卷、归档卷。
  - 拉取桌面端草稿任务并在本地确认。
  - 记录打印消耗事件和打印历史。
  - 保存桌面同步地址、扫码配对结果等设置。

关键目录：

- `app/src/main/java/com/tuozhu/consumablestatistics/data/`
- `app/src/main/java/com/tuozhu/consumablestatistics/sync/`
- `app/src/main/java/com/tuozhu/consumablestatistics/ui/`

### `desktop-app/`

- 技术栈：Java 17、Swing、`com.sun.net.httpserver`、ZXing。
- 主要职责：
  - 提供桌面 GUI 操作界面。
  - 启动内置 HTTP 服务。
  - 根据网络接口计算推荐地址，优先 Tailscale，其次局域网。
  - 维护 G-code 监听状态、日志、二维码配对展示。
  - 在需要时拉起同步引擎执行一次扫描或后台同步。

关键类：

- `DesktopSyncApp.java`：桌面 UI、地址推荐、扫码配对、状态刷新。
- `EmbeddedSyncService.java`：桌面内置 `/health`、`/api/sync/pull`、`/api/sync/confirm` 服务。
- `GcodeWatchService.java`：监听 Bambu Studio 缓存目录、做稳定性判定、触发最近切片回补扫描。

### `desktop-agent/`

- 技术栈：Windows PowerShell。
- 主要职责：
  - 解析 Bambu Studio 生成的 `.gcode` 文件头。
  - 将切片结果转换为标准草稿任务。
  - 把草稿任务写入 `outbox/desktop-outbox.json`。
  - 把状态和告警写入 `state/state.json`。
  - 吸收手机端确认回执 `outbox/confirmation-log.json`，完成幂等更新。

关键脚本：

- `run-sync-agent.ps1`：当前正式同步引擎。
- `legacy/start-sync-server.ps1`：已归档，仅保留历史参考。

## 运行链路

### 桌面端

1. 用户启动 `TuoZhuDesktopSync.exe`。
2. GUI 根据配置启动 `EmbeddedSyncService`。
3. `EmbeddedSyncService` 启动 `GcodeWatchService`。
4. 监听到新切片或用户手动扫描时，GUI 调用 `run-sync-agent.ps1`。
5. 同步引擎写入 `desktop-agent/outbox` 和 `desktop-agent/state`。

### 手机端

1. Android 端请求 `GET /api/sync/pull`。
2. 桌面端返回当前草稿、告警、同步状态。
3. 用户确认打印任务后，Android 端本地扣减耗材卷。
4. Android 端请求 `POST /api/sync/confirm`。
5. 桌面端写入确认回执并触发后台同步，已确认草稿从 outbox 中消失。

## 数据边界

### Android 本地数据

- 耗材卷实体
- 耗材事件实体
- 打印任务实体
- 打印历史视图
- 同步设置

### 桌面端状态文件

- `desktop-agent/outbox/desktop-outbox.json`
- `desktop-agent/outbox/confirmation-log.json`
- `desktop-agent/state/state.json`
- `desktop-agent/inbox/print-history.generated.json`

这些文件属于运行时产物，不是桌面 GUI 的独立入口。

## 构建与交付

- Android 构建脚本：`scripts/build-debug.ps1`
- 桌面打包脚本：`scripts/build-desktop-app.ps1`
- 桌面成品目录：`dist/desktop/TuoZhuDesktopSync`

仓库当前仍依赖项目内 `.tools`，因为 `gradle-wrapper.jar` 尚未补齐。
