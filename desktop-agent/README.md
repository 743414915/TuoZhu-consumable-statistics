# Desktop Sync Agent Prototype

这个原型是一个 Windows PowerShell 同步器，用于把桌面端打印任务转换成手机端可消费的草稿任务。
当前它只在项目目录内用 JSON 文件收发数据，还没有直接接到 Android 端运行时。

## Directory Layout

- `inbox/print-history.sample.json`
  - 模拟桌面侧打印任务输入
- `state/state.json`
  - 去重和确认状态
- `outbox/desktop-outbox.json`
  - 产出的草稿任务
- `outbox/confirmation-log.json`
  - 手机端确认后可回写的确认记录
- `run-sync-agent.ps1`
  - 主同步脚本

## Run

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop-agent\run-sync-agent.ps1 -UseSample
```

真实 Bambu Studio `.gcode` 自动同步：

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop-agent\run-sync-agent.ps1 `
  -UseBambuGcode `
  -GcodePaths 'C:\Users\Administrator\Desktop\.23028.0.gcode'
```

按目录扫描：

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop-agent\run-sync-agent.ps1 `
  -UseBambuGcode `
  -GcodeSearchRoots 'C:\Users\Administrator\Desktop',"$env:LOCALAPPDATA\Temp\bamboo_model"
```

如果不传 `-InboxPath` 且项目里没有 `inbox/print-history.json`，脚本会自动进入 Bambu `.gcode` 扫描模式。

执行后会：

1. 读取 `inbox` 中的打印任务
2. 按 UTF-8 解析输入，并校验 `source`、`targetMaterial`、`estimatedUsageGrams`、`createdAt`
3. 以 `externalJobId` 做幂等和更新
4. 输出未确认任务到 `outbox/desktop-outbox.json`
5. 读取 `confirmation-log.json`，把已确认任务同步进本地状态

在 `.gcode` 模式下还会：

1. 扫描指定文件或目录里的 Bambu Studio `.gcode`
2. 从文件头提取打印时长、耗材重量、材料信息、打印机型号
3. 生成标准草稿 JSON 到 `inbox/print-history.generated.json`
4. 再按统一协议流入 `outbox` 和 `state`

## Accepted Values

- `source`: `DESKTOP_AGENT`、`CLOUD`
- `targetMaterial`: `PLA Basic`、`PETG Basic`、`PLA Silk`
- `estimatedUsageGrams`: 必须大于 0
- `createdAt` / `confirmedAt`: 需要是合理时间戳

非法任务不会导致整批同步失败，而是被忽略并记录到 `state/state.json` 的 `warnings` 字段。

## Bambu G-code Mapping

当前版本已验证 Bambu Studio 文件头中的以下字段：

- `total filament weight [g]`
- `model printing time`
- `printer_model`
- `filament_settings_id`
- `filament_type`
- `default_filament_profile`

映射规则：

- `externalJobId`
  - 优先从类似 `.23028.0.gcode` 的文件名解析成 `bambu-gcode-23028-0`
  - 如果文件名不符合规律，则退回到头信息指纹哈希
- `modelName`
  - 优先从 `filament_settings_id` 里的 `(... .3mf)` 项提取
  - 提取不到则回退到文件名
- `estimatedUsageGrams`
  - 取 `total filament weight [g]`
  - 当前会向上取整成整数，例如 `42.24g -> 43g`
- `targetMaterial`
  - 优先取 `filament_settings_id`
  - 其次取 `filament_type`
  - 最后才取 `default_filament_profile`
  - 若多个字段冲突，会保留更高优先级结果并写 warning
- `createdAt`
  - 取 `.gcode` 文件最后修改时间（UTC 毫秒时间戳）

## Confirmation Log Format

```json
[
  {
    "externalJobId": "desktop-demo-benchy",
    "confirmedAt": 1775806500000,
    "targetRollId": 1
  }
]
```

## Notes

- 这个原型目前不直接和手机通信，只负责在项目目录内生产标准草稿文件。
- `outbox/desktop-outbox.json` 始终写成 JSON 数组，即使只剩 1 条草稿。
- 手机端最终确认时仍然依赖“活动耗材卷”；如果没有活动卷，确认动作应被 UI 拦截或提示用户先设置活动卷。
- 真实 `.gcode` 自动同步已接入桌面侧解析，但 Android 端仍未直接读取 PC 文件；当前仍需要通过后续传输层把 outbox 草稿送到手机。
- 后续如果接入 HTTP、局域网服务或云端，只需要替换传输层，不需要改草稿模型。
