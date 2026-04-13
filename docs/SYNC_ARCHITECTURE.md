# Sync Architecture

## Scope

当前版本将耗材管理升级为以下模型：

- 固定材料列表：`PLA Basic`、`PETG Basic`、`PLA Silk`
- 首页存在“活动耗材卷”概念
- 库存采用“最近校准值 + 后续事件流”的估算模型
- 自动同步先只落接口骨架，不直接接入打印机或云端

## Inventory Model

每卷耗材的当前估算余量不再直接依赖单个 `remainingWeightGrams` 字段，而是按以下规则计算：

`estimatedRemaining = lastCalibrationWeight + sum(events.delta after lastCalibrationAt)`

其中：

- `CALIBRATION`：把当前称重值设为新的基准
- `MANUAL_ADJUSTMENT`：手动登记的消耗或修正
- `PRINT_USAGE`：未来由自动同步确认后写入的打印消耗

这样可以同时满足：

- 手动称重校准
- 自动同步累计消耗
- 历史回溯
- 冲突时以校准值为最高优先级

## Entities

### `FilamentRollEntity`

- 基础元数据：品牌、材料、颜色、满卷克重、阈值
- 关键状态：
  - `lastCalibrationWeightGrams`
  - `lastCalibrationAt`
  - `isActive`

### `FilamentEventEntity`

- 事件类型：`CALIBRATION`、`PRINT_USAGE`、`MANUAL_ADJUSTMENT`
- 关键字段：
  - `deltaGrams`
  - `remainingAfterGrams`
  - `source`
  - `externalJobId`

### `PrintJobEntity`

- 作为自动同步导入的打印任务草稿
- 当前只保留 `DRAFT`、`CONFIRMED` 两个阶段
- 后续桌面同步器或云同步会优先写入这里，再由用户确认后写入事件流

### `SyncStateEntity`

- 保存最近一次同步结果
- 当前展示：
  - 最后同步来源
  - 最后同步时间
  - 状态
  - 文案

## Repository Layer

`FilamentRepository` 现在负责：

- 维护活动卷
- 写入校准事件和手动消耗事件
- 基于校准时间点计算当前估算余量
- 提供同步状态和待确认打印任务流
- 通过 `SyncCoordinator` 抽象拉取同步结果

## Sync Skeleton

当前仅实现：

- `SyncCoordinator`
- `LocalSyncCoordinator`
- `SyncPullResult`
- `SyncDraftValidator`

它的职责是先给 UI 和仓储层提供稳定接口，后续可以替换为：

- `DesktopAgentSyncCoordinator`
- `CloudSyncCoordinator`
- `LanPrinterSyncCoordinator`

当前真实状态：

- Android 端同步展示、待确认任务和确认扣减链路已经打通
- 桌面同步器原型已能在 `desktop-agent/` 目录下生成标准草稿 JSON
- Android 与桌面原型尚未真正直连，当前仍由 `LocalSyncCoordinator` 提供演示数据

## UI Rules

首页现在分成三层：

1. 顶部总览
2. 同步状态区
3. 活动耗材卷区
4. 全部耗材列表

其中活动卷是自动同步未来默认影响的目标卷；如果未来同步任务未明确指定卷，系统应先要求用户确认活动卷。

## Future Work

下一阶段建议按这个顺序推进：

1. 接入真正的 `DesktopAgentSyncCoordinator`
2. 打通手机确认回执回写到桌面端
3. 增加同步冲突策略和任务来源映射
4. 增加备份与恢复
5. 评估局域网或云端传输层
