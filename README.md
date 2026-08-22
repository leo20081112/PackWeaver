# PackWeaver Bridge（游戏内配套 Mod）

依据《PackWeaver v1.0 完整规划书》实施的游戏内配套 Mod。PackWeaver 桌面端（IDE 模式 / 技术模式）负责数据包的可视化开发，本 Mod 负责游戏内一侧：**多窗口叠加层、热重载桥接、性能分析、坐标/NBT 工具**。

| 项目 | 说明 |
|---|---|
| 加载器 | Fabric（1.20.1，Java 17+） |
| Mod ID | `packweaver` |
| 版本 | 1.0.0 |
| 许可证 | MIT |

## 功能一览（对应规划书章节）

- **F12 多窗口叠加层**（第 13.1 / 扩展 D 章）：游戏中按 `F12` 显示/隐藏半透明信息窗口；`Shift+F12` 打开布局编辑界面，可拖拽窗口、逐个开关，布局保存在 `config/packweaver-overlay.json`。内置窗口：性能、坐标、桥接日志、帮助。
- **热重载桥接**（第 18 / 20 章）：本机 TCP `127.0.0.1:32005` 上的按行 JSON 协议，供 PackWeaver 桌面端连接，实现修改即重载、命令执行、数据查询。仅监听回环地址，不暴露到外网。
- **性能分析**（第 8.3 / 14.7 章）：基于服务器 tick 间隔估算 MSPT / TPS / 峰值，通过 `/pw stats`、叠加层、桥接 `stats` 指令三处输出，并给出 good/warn/bad 状态。
- **坐标复制器**（第 14.3 / 14.4 章）：`/pw copier give` 获得道具；右键方块复制 `X Y Z`，Shift+右键复制方块 NBT，直接进剪贴板。
- **`/pw` 命令系统**：`/pw reload`、`/pw stats`、`/pw bridge status`、`/pw copier give`。

## 桥接协议

客户端连接 `127.0.0.1:32005`，每行发送一条 JSON 请求，服务端逐行返回 JSON 应答：

```json
{"action": "ping"}
{"action": "eval", "command": "say hello"}
{"action": "reload"}
{"action": "stats"}
```

应答示例：

```json
{"ok": true, "stats": {"mspt_avg": 3.21, "mspt_max": 8.9, "tps": 20.0, "status": "good"}}
```

快速测试（PowerShell）：

```powershell
$client = New-Object Net.Sockets.TcpClient('127.0.0.1', 32005)
$stream = $client.GetStream()
$writer = New-Object IO.StreamWriter($stream)
$reader = New-Object IO.StreamReader($stream)
$writer.WriteLine('{"action":"ping"}'); $writer.Flush()
$reader.ReadLine()
```

## 构建

```bash
gradle build        # 需要 Gradle 8.7+，产物在 build/libs/packweaver-bridge-1.0.0.jar
```

首次构建会自动下载 Minecraft 依赖与 Yarn 映射。安装时把 jar 放入 `.minecraft/mods/`，需同时安装 [Fabric API](https://modrinth.com/mod/fabric-api) 与 Fabric Loader ≥ 0.14.21。

## 目录结构

```
packweaver-bridge/
├── build.gradle / gradle.properties / settings.gradle   # Fabric Loom 构建配置
└── src/main/
    ├── java/dev/packweaver/bridge/
    │   ├── PackWeaverBridge.java          # 主入口：注册物品/命令/性能/桥接
    │   ├── PackWeaverBridgeClient.java    # 客户端入口：F12 按键与 HUD
    │   ├── bridge/BridgeServer.java       # TCP JSON 桥接服务器
    │   ├── client/                        # 叠加层窗口管理/渲染/布局编辑
    │   ├── command/PWCommands.java        # /pw 命令
    │   ├── perf/PerfTracker.java          # MSPT/TPS 性能追踪
    │   └── tools/CoordinateCopierItem.java# 坐标复制器物品
    └── resources/
        ├── fabric.mod.json
        └── assets/packweaver/（语言文件、物品模型）
```

## 路线图（依据规划书后续版本）

- v1.1：增量重载（函数级热替换）、游戏内预览窗口（3D 区域高亮）
- v1.2：多人调试（单玩家作用域）、危险命令二次确认
- v1.3：Paper/Purpur/Folia 服务端适配插件
