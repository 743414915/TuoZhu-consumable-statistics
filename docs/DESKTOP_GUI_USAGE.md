# 桌面同步工具使用说明

## 产物位置

- 桌面应用目录：`dist/desktop/TuoZhuDesktopSync`
- 启动文件：`dist/desktop/TuoZhuDesktopSync/TuoZhuDesktopSync.exe`
- Android 调试包：`dist/tuozhu-consumable-manager-debug.apk`

## 适用场景

这个桌面工具用于把 Bambu Studio 生成的 `.gcode` 打印记录解析成手机端可消费的草稿任务。

当前已经针对你的 A1 mini + 无 AMS 场景做了约束：

- 只保留 `PLA Basic`
- 只保留 `PETG Basic`
- 只保留 `PLA Silk`
- 真实 `.gcode` 同步优先从文件头提取材料、克重、打印时长

## 第一次使用

1. 打开 `TuoZhuDesktopSync.exe`
2. 确认左侧 `Desktop Agent Root` 指向应用目录内的 `desktop-agent`
3. 模式选择 `Real Bambu G-code`
4. 在 `G-code Search Roots` 中保留或补充你的切片输出目录

推荐保留这两个目录：

- `C:\Users\Administrator\Desktop`
- `C:\Users\74341\AppData\Local\Temp\bamboo_model`

5. 端口先保持默认 `8823`
6. `Max File Age (days)` 可以先保持 `7`

## 常用操作

### 1. 扫描一次

点击 `Scan Once`。

用途：

- 立即扫描最近生成的 `.gcode`
- 刷新草稿列表
- 刷新 warning 和最近同步时间

### 2. 启动同步服务

点击 `Start Service`。

用途：

- 在局域网内提供手机拉取接口
- 手机端可以通过桌面地址拉取待确认任务
- 手机确认后，桌面端会回写确认记录并刷新状态

启动后，顶部会显示：

- 服务状态
- 手机访问地址
- 待处理草稿数量
- warning 数量
- 最近同步时间

### 3. 停止同步服务

点击 `Stop Service`。

## 手机端地址

桌面端启动服务后，手机端同步地址填写：

`http://你的电脑局域网IP:8823`

例如：

`http://192.168.1.8:8823`

要求：

- 手机和电脑在同一个局域网
- Windows 防火墙不要拦截这个端口

## 真实 G-code 同步规则

当前桌面端会从 `.gcode` 文件头提取这些信息：

- `total filament weight [g]`
- `model printing time`
- `printer_model`
- `filament_settings_id`
- `filament_type`
- `default_filament_profile`

并映射为：

- `externalJobId`
- `modelName`
- `estimatedUsageGrams`
- `targetMaterial`
- `note`
- `createdAt`

如果材料字段有冲突，系统会：

- 仍然生成草稿
- 在 `Warnings` 面板里提示冲突来源

## 你现在最需要做的事

1. 用桌面版启动一次 `Start Service`
2. 把顶部显示的手机地址填进 Android 应用
3. 在 Bambu Studio 切片后，观察桌面端是否出现新的草稿
4. 在手机端确认一次耗材扣减，验证整条链路

## 已验证的回归项

- 桌面应用重新打包成功
- 打包后的 GUI 可以正常启动
- 样例扫描成功
- 真实 `.gcode` 扫描成功
- HTTP `health / pull / confirm` 闭环成功
