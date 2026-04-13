## 概要
面向拓竹 A1 Mini 且无 AMS 的用户，首版目标是以 Kotlin + Jetpack Compose 迅速建立“耗材管理” MVP，在有限硬件信息前提下帮助用户手动录入每卷耗材的重量与领用记录，并通过本地存储与状态管控保持数据一致。

## 技术选型
- **语言与 UI**：Kotlin + Jetpack Compose（Material3），Compose 提供声明式界面便于快速迭代，Kotlin 可与 Jetpack 组件无缝协作。
- **结构化应用框架**：采用 AndroidX Lifecycle + ViewModel + Kotlin Coroutines/Flow 提供 MVVM 与响应式状态控制。
- **本地存储**：Room 作为主存储，Schema 包含耗材卷、耗材类型与历史更新记录；DataStore 用于保存偏好设置（如单位、提醒阈值）。
- **依赖注入**：Hilt 让仓库层与 ViewModel 解耦；若项目需要可以替代为 Koin，但此处首选 Hilt 与 AndroidX 生态一致。
- **后台任务**：WorkManager 用于 audit 定时提醒或后台同步（未来升级时）。

## 模块与包结构
- `com.tuozhu.consumable`（根包）
  - `ui`：Compose 页面与导航，按功能子包划分（`home`、`detail`、`settings`）；每个页面由 `Screen` + `ViewModel` 组成。
  - `feature`：可选封装更复杂流程，如“耗材补给计划”、“打印量统计”；
  - `data`：Room Entity、Dao、Repository、DataStore 访问对象，负责存储与缓存。
  - `domain`：用例接口（如 `TrackConsumableUseCase`）与模型（`ConsumableVolume`、`ConsumableType`），向上游提供纯 Kotlin 逻辑。
  - `core`：公共状态（`UiState`）、协程调度器、异常处理。

## 状态管理
- **MVVM + Flow**：每个 `Screen` 绑定 `ViewModel`，使用 `MutableStateFlow` 暴露 `UiState`，Compose 通过 `collectAsState` 驱动。
- **状态模型**：`UiState` 由 `LoadStatus` 和 `ConsumableSummary` 组成，统一处理加载/成功/错误，避免界面多重状态分散。
- **事件流**：`SharedFlow` 用于短暂事件（如提示），`ViewModel` 负责将用户输入与仓库操作串起来，所有数据变更最终落到 Flow 上。

## 离线存储方案
- **Room Schema**：
  - `ConsumableRoll`（id、typeId、initialWeight、remainingWeight、lastUpdated）
  - `ConsumableType`（id、name、unitWeightHint、colorTag）
  - `ConsumableLog`（rollId、timestamp、changeAmount、note）
- **同步策略**：每次用户更新重量即写 Room，同时更新 `remainingWeight`。
- **设置与提醒**：借助 DataStore 存偏好（`lowThresholdPercent`、`notificationEnabled`），后续可配合 WorkManager 驱动本地通知。

## 扩展性考虑
- **打印机支持扩展**：将硬件特性抽象为接口（如 `PrinterProfile` + `ConsumableReader`），首版仅手动入参，未来可以接入更多打印机或云端数据。
- **多卷管理策略**：将耗材按类型分层，支持自定义规则（如“每卷分段提醒”）；
- **业务监控**：设计 `AnalyticsReporter`（可用 Firebase /本地日志）记录用户操作以辅助后续优化。
- **跨设备同步**：为后期远程同步留出 `RemoteRepository` 接口与 `NetworkBoundResource` 样板。

## 交付建议
1. 首版保持 “数据录入 → 进度查看 → 提醒阈值” 主流程清晰、简单。
2. 接口与模块应有明确分界，避免 ViewModel 直接操作 Room，所有数据库访问通过 Repository。
3. 后续优先引入自动化测试（Room + Compose UI 测试）和多语言（简中 + 英语）。
