# 拓竹耗材管家

面向没有 AMS 的拓竹 A1 mini 用户，用手动称重方式管理每卷耗材剩余克重的 Android 应用。

## MVP 功能

- 新增耗材卷，记录品牌、材料、颜色、满卷克重和当前剩余克重
- 手动登记每次打印消耗的克重
- 通过称重重新校准某卷耗材的剩余克重
- 首页查看所有耗材卷的剩余状态、低库存提醒和最近操作记录
- 本地离线存储，不依赖登录或云端

## 技术栈

- Kotlin
- Jetpack Compose + Material 3
- Room
- ViewModel + StateFlow

## 当前已知限制

- 当前仓库已经内置项目级 `.tools` 构建环境，可通过 `powershell -ExecutionPolicy Bypass -File .\scripts\build-debug.ps1` 重新打包
- `gradle-wrapper.jar` 仍未补入，因此优先使用项目内脚本而不是 `gradlew`
- 首版暂未加入导出、备份、打印任务导入、耗材品牌预设等增强能力

## APK 输出

- 调试 APK: `app/build/outputs/apk/debug/app-debug.apk`
- 可分发副本: `dist/tuozhu-consumable-manager-debug.apk`

## 下一步建议

1. 补齐 Gradle Wrapper 与本机 Android 构建环境
2. 增加删除或归档耗材卷、筛选和搜索
3. 加入 CSV/JSON 备份恢复
4. 后续可扩展到按打印任务估算耗材消耗
