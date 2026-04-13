# Desktop Sync Protocol

## Goal

桌面同步器负责把打印任务草稿推送给手机端。手机端先展示为“待确认打印任务”，只有用户确认后，才会把消耗写入活动耗材卷的事件流。

当前状态说明：

- `desktop-agent/run-sync-agent.ps1` 已经能在项目目录内产出标准 JSON 草稿和确认回执状态
- `desktop-agent/run-sync-agent.ps1` 现已支持直接解析 Bambu Studio 生成的 `.gcode` 文件头
- Android 端当前仍使用 `LocalSyncCoordinator` 演示数据，还没有直接读取桌面同步器的 outbox 文件
- 后续接线时只需要新增真正的 `DesktopAgentSyncCoordinator`，不需要改草稿结构

## Draft Payload

最小字段：

```json
{
  "externalJobId": "desktop-demo-benchy",
  "source": "DESKTOP_AGENT",
  "modelName": "Benchy Demo",
  "estimatedUsageGrams": 28,
  "targetMaterial": "PLA Basic",
  "note": "来自桌面同步器演示数据",
  "createdAt": 1775805900000
}
```

字段说明：

- `externalJobId`: 来源系统里的唯一打印任务 ID
- `source`: 当前支持 `DESKTOP_AGENT`、`CLOUD`
- `modelName`: 模型名或任务名
- `estimatedUsageGrams`: 预计耗材克重，必须大于 0
- `targetMaterial`: 可选，用于手机端提示材料；当前只允许 `PLA Basic`、`PETG Basic`、`PLA Silk`
- `note`: 附加信息
- `createdAt`: 任务时间戳，必须在合理时间范围内

`desktop-outbox.json` 必须始终保持 JSON 数组格式，即使只剩 1 条草稿。

## Bambu Studio G-code Mapping

桌面同步器在 `.gcode` 模式下会读取 Bambu Studio 文件头，并映射成同一份草稿协议：

- `total filament weight [g]` -> `estimatedUsageGrams`
  - 当前按向上取整转为整数
- `filament_settings_id` / `filament_type` / `default_filament_profile` -> `targetMaterial`
  - 优先级依次为：
    1. `filament_settings_id`
    2. `filament_type`
    3. `default_filament_profile`
  - 若出现冲突，以更高优先级结果为准，并写入 warning
- 文件名 `.23028.0.gcode` -> `externalJobId = bambu-gcode-23028-0`
  - 若文件名无法解析，则退回头信息指纹哈希
- 文件最后修改时间 -> `createdAt`
- 头信息里的项目名/3mf 名称 -> `modelName`

示例：实际观测到的文件头里，`filament_settings_id` 指向 `PETG Basic`，但 `default_filament_profile` 写成了 `PLA Basic`。当前协议会保留 `PETG Basic`，并记录一条冲突 warning。

## Idempotency

手机端以 `externalJobId` 做幂等：

- 若不存在，则插入 `PrintJobEntity(status = DRAFT)`
- 若已存在且仍是 `DRAFT`，允许更新任务名、预计克重、备注、创建时间
- 若已确认，则忽略重复推送，不再创建新的耗材事件
- 桌面同步器本地状态同样以 `externalJobId` 做幂等，并把告警写入 `state.json`

## Confirmation Flow

1. 手机端拉取草稿任务
2. 用户在首页“待确认打印任务”区点击“确认并扣减”
3. 如果任务未绑定卷，则默认作用到当前活动卷；若当前没有活动卷，确认应失败并提示用户先设置活动卷
4. 手机端把 `PrintJobEntity.status` 改为 `CONFIRMED`
5. 同时新增一条 `FilamentEventEntity(type = PRINT_USAGE)`
6. 桌面侧收到确认回执后，把对应草稿标为 `CONFIRMED`，并从 outbox 中移除

## Event Mapping

确认后的事件格式：

- `type = PRINT_USAGE`
- `source = DESKTOP_AGENT`
- `deltaGrams = -estimatedUsageGrams`
- `remainingAfterGrams = clamp(calculateRemaining(roll) - estimatedUsageGrams)`
- `externalJobId = printJob.externalJobId`

## Future Extension

后续如果接入局域网服务或云端 API，建议保留相同草稿模型，只新增传输方式，不改手机端确认逻辑。
