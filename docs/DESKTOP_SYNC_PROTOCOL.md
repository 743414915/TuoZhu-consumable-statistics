# 桌面同步协议

## 目标

桌面端负责把最近切片生成的打印任务转换为“待确认草稿”，手机端只在用户确认后才真正扣减耗材卷。

当前正式链路：

- 桌面 GUI：`desktop-app`
- 内置 HTTP 服务：`EmbeddedSyncService`
- 解析引擎：`desktop-agent/run-sync-agent.ps1`
- Android 同步实现：`DesktopAgentSyncCoordinator`

## HTTP 接口

### `GET /health`

用于手机端或用户手动探测桌面服务是否可达。

示例响应：

```json
{
  "status": "ok",
  "source": "DESKTOP_AGENT",
  "serverTime": 1776070800109,
  "syncBusy": false
}
```

### `GET /api/sync/pull`

返回当前草稿任务、告警、输入模式和同步状态。

示例响应：

```json
{
  "status": "SUCCESS",
  "source": "DESKTOP_AGENT",
  "syncedAt": 1775806800000,
  "message": "桌面同步完成，待确认任务 1 条。",
  "draftJobs": [],
  "warnings": [],
  "inputMode": "bambu-gcode",
  "syncBusy": false
}
```

说明：

- 当 `syncBusy=true` 且已有缓存时，桌面端可能返回“已返回当前缓存，桌面端正在后台刷新”。
- 这不是错误，表示先给手机端旧缓存，同时后台继续刷新。

### `POST /api/sync/confirm`

手机端确认打印后回写回执。

请求体：

```json
{
  "externalJobId": "bambu-gcode-23028-0",
  "confirmedAt": 1775806800000,
  "targetRollId": 1
}
```

确认成功后，桌面端会把该回执写入 `desktop-agent/outbox/confirmation-log.json`，并立刻触发一次后台同步。

## 草稿任务结构

```json
{
  "externalJobId": "bambu-gcode-23028-0",
  "source": "DESKTOP_AGENT",
  "modelName": "Benchy Demo",
  "estimatedUsageGrams": 28,
  "targetMaterial": "PLA Basic",
  "note": "来自 Bambu Studio 切片文件 .23028.0.gcode",
  "createdAt": 1775805900000
}
```

字段约束：

- `externalJobId`：必须唯一，用于桌面和手机两端幂等。
- `source`：当前允许 `DESKTOP_AGENT`、`CLOUD`。
- `modelName`：不能为空。
- `estimatedUsageGrams`：必须大于 `0`。
- `targetMaterial`：当前只允许 `PLA Basic`、`PETG Basic`、`PLA Silk`，也可以为空。
- `createdAt`：必须是合理时间戳。

`desktop-outbox.json` 必须始终保持 JSON 数组格式。

## G-code 映射规则

桌面同步引擎从 Bambu Studio `.gcode` 文件头读取以下字段：

- `total filament weight [g]`
- `model printing time`
- `printer_model`
- `filament_settings_id`
- `filament_type`
- `default_filament_profile`

映射规则：

- `estimatedUsageGrams`
  - 来自 `total filament weight [g]`
  - 当前按向上取整处理
- `targetMaterial`
  - 优先级：
    1. `filament_settings_id`
    2. `filament_type`
    3. `default_filament_profile`
  - 如果多个字段冲突，保留高优先级结果并记录 warning
- `modelName`
  - 优先从 `filament_settings_id` 中的 `(... .3mf)` 提取
  - 提取不到时回退到文件名
- `externalJobId`
  - 优先从文件名如 `.23028.0.gcode` 解析为 `bambu-gcode-23028-0`
  - 再退回到基于内容指纹生成的稳定 ID
- `createdAt`
  - 使用 `.gcode` 文件最后修改时间

## 幂等规则

### 桌面端

- 以 `externalJobId` 为主键更新本地状态。
- 已确认任务不会再次变回草稿。
- 重复草稿会刷新内容，不会无限新增重复任务。

### Android 端

- 同样以 `externalJobId` 保证幂等。
- 已确认任务只允许写入一次打印消耗事件。
- 确认时会校验活动卷材料是否匹配、剩余克重是否足够。

## 确认链路

1. 手机端拉取草稿任务。
2. 用户选择目标耗材卷或使用当前活动卷。
3. Android 端本地写入一次 `PRINT_USAGE` 事件并把任务标记为 `CONFIRMED`。
4. Android 端调用 `POST /api/sync/confirm`。
5. 桌面端写入确认回执。
6. 后台同步再次运行，已确认草稿从桌面 outbox 中消失。

## 运行时文件

- `desktop-agent/inbox/print-history.generated.json`
- `desktop-agent/outbox/desktop-outbox.json`
- `desktop-agent/outbox/confirmation-log.json`
- `desktop-agent/state/state.json`

这些文件属于当前协议的一部分，但它们是运行时状态，不是用户直接操作入口。
