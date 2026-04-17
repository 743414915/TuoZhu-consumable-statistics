# 拓竹耗材管家

面向 `Bambu Lab A1 mini + 无 AMS` 场景的耗材管理项目。当前交付由两部分组成：

- `app/`：Android 手机端，用于管理耗材卷、确认打印任务、查看历史记录。
- `desktop-app/`：Windows 桌面端 GUI，用于监听 Bambu Studio 切片缓存、解析 `.gcode`、向手机端提供内置同步服务。

桌面端当前主方案是：

1. `desktop-app` 启动桌面 GUI。
2. GUI 内置 HTTP 服务，提供 `/health`、`/api/sync/pull`、`/api/sync/confirm`。
3. GUI 调用 `desktop-agent/run-sync-agent.ps1` 解析最近切片的 `.gcode`。
4. 解析结果写入 `desktop-agent/outbox` 和 `desktop-agent/state`。
5. Android 端拉取草稿任务，用户确认后再扣减当前耗材卷。

旧的“单独启动 PowerShell HTTP 服务”方案已归档：

- `docs/archive/`
- `desktop-agent/legacy/`

## 当前能力

- 耗材卷管理：新增、编辑、切换活动卷、归档已用完卷。
- 材料范围：`PLA Basic`、`PETG Basic`、`PLA Silk`。
- Android 端支持手动同步、扫码配对、待确认打印任务、打印历史记录。
- 桌面端支持实时监听 Bambu Studio 缓存目录，检测最近切片并自动生成同步草稿。
- 桌面端推荐地址优先展示 Tailscale，其次展示局域网地址。

## 代码入口

- Android 应用入口：`app/src/main/java/com/tuozhu/consumablestatistics/MainActivity.kt`
- Android 同步实现：`app/src/main/java/com/tuozhu/consumablestatistics/sync/`
- 桌面 GUI 入口：`desktop-app/src/main/java/com/tuozhu/desktop/DesktopSyncApp.java`
- 桌面内置服务：`desktop-app/src/main/java/com/tuozhu/desktop/EmbeddedSyncService.java`
- G-code 监听：`desktop-app/src/main/java/com/tuozhu/desktop/GcodeWatchService.java`
- G-code 解析引擎：`desktop-agent/run-sync-agent.ps1`

## 仓库结构

```text
app/                 Android 客户端
desktop-app/         Windows 桌面 GUI 与内置 HTTP 服务
desktop-agent/       G-code 解析、状态文件、同步草稿引擎
docs/                当前方案文档
docs/archive/        已归档的旧方案文档
scripts/             使用项目内 .tools 的构建脚本
```

## 构建方式

本仓库优先使用项目内的 `.tools`，避免修改电脑系统环境。

- Android 调试构建：`powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1`
- 桌面端打包：`powershell -ExecutionPolicy Bypass -File .\scripts\build-desktop-app.ps1`

注意：

- `gradle/wrapper/` 当前只有 `gradle-wrapper.properties`，缺少 `gradle-wrapper.jar`。
- 因此默认不要依赖 `gradlew`，优先使用 `scripts/` 下脚本。

## 运行桌面端

桌面打包输出目录：

- `dist/desktop/TuoZhuDesktopSync/`

直接运行：

- `dist/desktop/TuoZhuDesktopSync/TuoZhuDesktopSync.exe`

如果换一台 Windows 电脑运行，复制整个 `TuoZhuDesktopSync` 目录即可，不要只复制 `.exe`。目录内的 `runtime/`、`app/`、`desktop-agent/` 都是必需的。

## 推荐文档

- `docs/DESKTOP_GUI_USAGE.md`：桌面端日常使用
- `docs/ARCHITECTURE.md`：当前架构与代码职责
- `docs/DESKTOP_SYNC_PROTOCOL.md`：桌面和 Android 的同步协议
- `desktop-agent/README.md`：同步引擎目录说明
