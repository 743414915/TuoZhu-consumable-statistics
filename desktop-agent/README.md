# Desktop Agent

`desktop-agent/` 是当前桌面同步方案里的“解析与状态引擎”目录，不是最终用户直接操作的主入口。

当前用户入口是：

- `desktop-app` 打包后的 `TuoZhuDesktopSync.exe`

当前引擎入口是：

- `desktop-agent/run-sync-agent.ps1`

## 目录说明

- `inbox/print-history.sample.json`
  - 示例输入数据
- `inbox/print-history.generated.json`
  - 从 `.gcode` 解析后生成的中间草稿
- `outbox/desktop-outbox.json`
  - 桌面端提供给手机端拉取的草稿任务
- `outbox/confirmation-log.json`
  - 手机端确认后的回执日志
- `state/state.json`
  - 桌面端本地状态、告警、任务快照
- `run-sync-agent.ps1`
  - 当前正式同步引擎
- `legacy/`
  - 已归档的旧独立 HTTP 服务脚本和说明

## 当前职责

`run-sync-agent.ps1` 负责：

1. 读取 Bambu Studio `.gcode` 文件。
2. 解析模型名、材料、估算克重、打印时长等信息。
3. 生成标准草稿任务。
4. 与已有状态做幂等合并。
5. 吸收确认日志，把已确认任务移出草稿队列。

## 常用运行方式

### 示例数据模式

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop-agent\run-sync-agent.ps1 -UseSample
```

### 单文件 G-code 解析

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop-agent\run-sync-agent.ps1 `
  -UseBambuGcode `
  -GcodePaths "$env:USERPROFILE\Desktop\.23028.0.gcode"
```

### 目录扫描模式

```powershell
powershell -ExecutionPolicy Bypass -File .\desktop-agent\run-sync-agent.ps1 `
  -UseBambuGcode `
  -GcodeSearchRoots "$env:USERPROFILE\Desktop","$env:LOCALAPPDATA\Temp\bamboo_model"
```

正常情况下你不需要手动执行这些命令，因为桌面 GUI 会自动调用这个脚本。

## 识别范围

当前仅支持：

- `PLA Basic`
- `PETG Basic`
- `PLA Silk`

支持的输入来源：

- `DESKTOP_AGENT`
- `CLOUD`

## 输出规则

- 草稿输出到 `outbox/desktop-outbox.json`
- 状态输出到 `state/state.json`
- 解析后的中间输入输出到 `inbox/print-history.generated.json`

所有输出都以 UTF-8 写入。

## 备注

- 旧的独立 HTTP 服务方案已归档到 `legacy/`。
- 现在桌面 HTTP 服务已经由 `desktop-app` 内置实现，不再从这里单独启动。
