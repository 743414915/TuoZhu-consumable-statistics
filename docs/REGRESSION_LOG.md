# Regression Log

## 2026-04-10

环境限制：当前机器未提供 `java`、`gradle`、`ANDROID_HOME`、`ANDROID_SDK_ROOT`，并且仓库内暂缺 `gradle/wrapper/gradle-wrapper.jar`，因此本轮无法执行真实 Gradle 构建、单元测试、模拟器测试或真机回归。

## Five-Round Regression

1. 产品范围回归：确认首版范围聚焦 A1 mini 无 AMS 场景，覆盖新增耗材卷、登记消耗、称重校准、低库存提醒、最近记录，暂不做云同步、打印机联动、导入导出。
2. 数据模型回归：检查 Room 模型和事件日志，发现历史事件如果只读当前库存会导致旧事件显示不准，已补充 `remainingAfterGrams` 字段并在新增、消耗、校准时写入。
3. 状态流回归：检查 ViewModel 状态组合，发现 `observeRolls()` 重复订阅会造成多余 Room 查询，已收敛为共享 `StateFlow`。
4. 交互边界回归：检查新增和校准弹窗，发现多输入项在小屏上可能溢出，已将弹窗表单改为可滚动。
5. 交付面回归：检查项目结构、Gradle 配置、文件清单和阻塞项；`.\gradlew.bat test` 真实执行失败，失败原因为当前环境无可用 JDK。

## Pending Runtime Validation

- 安装或配置 JDK 17。
- 补齐 Gradle Wrapper jar，或使用 Android Studio 重新生成 wrapper。
- 配置 Android SDK，并确认 `local.properties` 中 `sdk.dir` 指向有效 SDK。
- 执行 `.\gradlew.bat test`、`.\gradlew.bat assembleDebug`。
- 在模拟器或真机上回归新增耗材、记录消耗、校准余量、重启后数据持久化、低库存提醒五条路径。

## 2026-04-10 Theme And Material Picker Regression

本轮改动：重做首页主题风格，新增渐变仪表盘、耗材卡片状态色带、低库存视觉提示；新增拓竹官方材料下拉选项；新增表单校验，阻止剩余克重大于满卷克重、阈值大于满卷克重、非法 HEX 色值。

五轮回归结果：

1. Round 1：执行 `scripts/build-debug.ps1`，首次发现 `ExposedDropdownMenu` API 在当前 Material3 版本不可用，已改为兼容的 `DropdownMenu`。
2. Round 1 重跑：`test assembleDebug` 通过，APK 输出到 `app/build/outputs/apk/debug/app-debug.apk`。
3. Round 2：增量回归构建通过。
4. Round 3：增量回归构建通过。
5. Round 4：增量回归构建通过。
6. Round 5：增量回归构建通过。

验证结果：`WeightMathTest` 4 个单元测试通过，最新可安装调试包已复制到 `dist/tuozhu-consumable-manager-debug.apk`。

## 2026-04-10 Event Flow And Sync Skeleton Regression

本轮改动：

- 材料列表收敛为 `PLA Basic`、`PETG Basic`、`PLA Silk`
- 数据层切换为“最近校准值 + 事件流”估算模型
- 新增活动耗材卷
- 新增同步状态骨架与 `SyncCoordinator`
- 首页新增同步状态区和活动卷区

五轮回归结果：

1. Round 1：执行 `scripts/build-debug.ps1`，完整编译、单测、打包通过。
2. Round 2：增量构建通过。
3. Round 3：增量构建通过。
4. Round 4：增量构建通过。
5. Round 5：增量构建通过。

验证结果：

- `WeightMathTest` 4 个单元测试继续全部通过
- 最新调试包已刷新到 `dist/tuozhu-consumable-manager-debug.apk`

## 2026-04-10 Pending Print Job Confirmation Regression

本轮改动：

- `SyncCoordinator` 现在可以返回打印任务草稿
- `FilamentRepository` 支持导入草稿任务和确认任务
- 首页新增“待确认打印任务”区
- 用户确认后会向活动卷写入 `PRINT_USAGE` 事件
- 新增桌面同步协议文档 `docs/DESKTOP_SYNC_PROTOCOL.md`

五轮回归结果：

1. Round 1：完整构建、单测、打包通过。
2. Round 2：增量构建通过。
3. Round 3：增量构建通过。
4. Round 4：增量构建通过。
5. Round 5：增量构建通过。

验证结果：

- `WeightMathTest` 4 个测试继续全部通过
- 最新调试包已刷新到 `dist/tuozhu-consumable-manager-debug.apk`

## 2026-04-10 Desktop Agent Prototype And Sync Validation Regression

本轮改动：

- Android 端新增 `SyncDraftValidator`，同步草稿现在会校验材料、克重、来源和时间戳
- `FilamentRepository` 现在会过滤无效草稿、更新同 `externalJobId` 的草稿内容，并保持确认流程幂等
- 新增 `FilamentRepositoryTest` 3 个测试与 `SyncDraftValidatorTest` 2 个测试
- `desktop-agent/run-sync-agent.ps1` 改为显式 UTF-8 读取，并支持坏数据告警、确认回执、稳定数组 outbox
- 同步协议、架构和 QA 文档已按当前真实实现更新

五轮回归结果：

1. Round 1：执行 `desktop-agent/run-sync-agent.ps1 -UseSample`，成功产出 2 条草稿、0 条 warning。
2. Round 2：使用临时确认回执复跑桌面同步器，`desktop-demo-benchy` 被标记为 `CONFIRMED`，outbox 只剩 1 条草稿。
3. Round 3：使用包含非法材料 `ABS` 的临时 inbox 复跑桌面同步器，坏数据被忽略并记录 1 条 warning，有效草稿保留。
4. Round 4：执行 Android 首轮完整构建回归，`test assembleDebug` 通过；Debug 单测共 9 项通过，包括 `WeightMathTest` 4 项、`FilamentRepositoryTest` 3 项、`SyncDraftValidatorTest` 2 项。
5. Round 5：继续执行 4 轮增量构建回归，全部通过，最新 APK 已刷新。

验证结果：

- `desktop-agent/outbox/desktop-outbox.json` 在样例场景下稳定输出 JSON 数组
- `desktop-agent/state/state.json` 会持久化 `warnings`、`CONFIRMED` 状态和 `targetRollId`
- 最新调试包已刷新到 `dist/tuozhu-consumable-manager-debug.apk`

## 2026-04-10 Default PETG And Collapsible Inventory Regression

本轮改动：

- 新增耗材弹窗默认材料改为 `PETG Basic`
- 新增耗材弹窗把低频字段收纳到“高级信息”区，缩短首屏输入长度
- 首页把非活动卷放入可展开/收起的耗材货架区，卷数少时自动展开
- 新增代码审查线程参与本轮质量检查，并修复其发现的 3 个问题：隐藏字段阻塞保存、默认材料双来源、少量卷被旧收纳状态隐藏

五轮回归结果：

1. Round 1：首轮构建通过，确认默认 PETG、折叠高级信息和耗材货架交互可编译、可打包。
2. Round 2：增量构建通过。
3. Round 3：增量构建通过。
4. Round 4：增量构建通过。
5. Round 5：增量构建通过。

验证结果：

- `test assembleDebug` 五轮全部通过
- 现有 Debug/Release 单测继续通过
- 最新调试包已刷新到 `dist/tuozhu-consumable-manager-debug.apk`

## 2026-04-13 Real Bambu G-code Auto Sync Regression

本轮改动：

- `desktop-agent/run-sync-agent.ps1` 新增真实 Bambu Studio `.gcode` 头解析
- 桌面同步器现在支持三种输入：
  - `sample-json`
  - `inbox-json`
  - `bambu-gcode`
- `.gcode` 模式下新增真实字段映射：
  - 从文件名生成稳定 `externalJobId`
  - 从 `filament_settings_id` 提取模型名和材料
  - 从 `total filament weight [g]` 生成整数 `estimatedUsageGrams`
  - 从文件最后修改时间生成 `createdAt`
- 材料冲突会保留高优先级结果并写入 warning
- `desktop-agent/README.md` 与 `docs/DESKTOP_SYNC_PROTOCOL.md` 已同步更新

五轮回归结果：

1. Round 1：对真实文件 `C:\Users\Administrator\Desktop\.23028.0.gcode` 执行显式 `-GcodePaths` 扫描，成功生成 1 条草稿：
   - `externalJobId = bambu-gcode-23028-0`
   - `modelName = 机械风格-皮卡丘V2`
   - `estimatedUsageGrams = 43`
   - `targetMaterial = PETG Basic`
2. Round 2：使用同一真实 `.gcode` 和同一 `state` 重跑，仍保持 1 条草稿，验证幂等更新不重复插入。
3. Round 3：基于已有 `state` 写入确认回执后重跑，任务状态变为 `CONFIRMED`，对应 outbox 变为 `[]`。
4. Round 4：改用 `-GcodeSearchRoots 'C:\Users\Administrator\Desktop'` 目录扫描，成功自动发现并生成相同草稿。
5. Round 5：不传 `-InboxPath` 且不传 `-UseSample`，脚本自动回退到默认 Bambu `.gcode` 扫描模式，成功自动发现并生成相同草稿。

附加兼容性验证：

- `-UseSample` 旧路径继续可用，仍能生成 2 条演示草稿
- 真实 `.gcode` 存在 `filament_settings_id = PETG Basic` 与 `default_filament_profile = PLA Basic` 的冲突，脚本正确保留 `PETG Basic` 并写入 warning

验证结果：

- 真实桌面切片文件已能转换为协议草稿 JSON
- `state.json` 能正确持久化真实 `.gcode` 任务的 `DRAFT` / `CONFIRMED` 状态
- 当前桌面侧“真实自动同步解析”已打通，手机侧传输层仍待接入
