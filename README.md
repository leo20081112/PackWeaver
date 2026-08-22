# PackWeaver Bridge —— 全部在游戏内的数据包 IDE

依据《PackWeaver v1.0 完整规划书》实施。双模式（技术模式积木 / IDE 模式代码）、诊断、模板、Wiki、调试监视、版本快照、3D 区域预览、热重载全部内置于 Minecraft，无需任何外部程序。

| 项目 | 说明 |
|---|---|
| 加载器 | Fabric（1.20.1，Java 17+） |
| Mod ID | `packweaver` |
| 版本 | 1.2.0 |
| 许可证 | MIT |

## 快速开始

1. 安装 Fabric Loader ≥ 0.14.21 与 Fabric API（1.20.1），放入本 Mod
2. 进入世界，输入 `/pw project` → 填项目名/命名空间 → 选模板（7 个内置）→ 创建
3. `/pw edit` 搭积木（技术模式），底部实时显示生成的命令；「保存运行」写入 datapacks 并热重载
4. `/pw code <命名空间>` 用 IDE 模式直接改 mcfunction/JSON（语法高亮、片段插入、Tab 补全、Ctrl+S 保存重载）
5. `/pw diag` 看诊断报告并一键修复；`/pw wiki` 查命令文档/拆解命令；`/pw debug` 监视分数与性能
6. `/pw preview` 打开 3D 区域预览线框；`/pw snapshot save` 存版本快照
7. F12：游戏内多窗口叠加层；Shift+F12 编辑窗口布局

## 命令总表

| 命令 | 功能 | 章节 |
|---|---|---|
| `/pw project` | 项目管理（新建/模板/打开/导出/快照/删除） | 2.4 / 12 |
| `/pw edit [ns]` | 技术模式：积木编辑器 | 9-11 |
| `/pw code <ns>` | IDE 模式：代码/JSON 编辑器（片段 + Tab 补全） | 4 / 5 / 8.1 |
| `/pw diag [ns]` | 智能诊断 + 快速修复 | 17 |
| `/pw wiki` | 命令 Wiki + 命令拆解 | 6 / 扩展C |
| `/pw debug` | 计分板/性能/桥接监视 | 16.4 |
| `/pw run <ns> [fn]` | ▶ 运行：重载并执行项目函数 | 2.4 |
| `/pw export [ns]` | 导出数据包 zip | 12 |
| `/pw snapshot save/list/restore` | 版本控制快照 | 7 |
| `/pw preview` | 3D 区域预览线框开关 | 18.4 |
| `/pw level [unlock]` | 渐进式等级查看 / 解锁 | 11.1 |
| `/pw reload` | 热重载数据包 | 18 |
| `/pw stats` | MSPT/TPS 性能 | 8.3 |
| `/pw bridge status` | TCP 桥接状态 | 20 |
| `/pw copier give` | 坐标复制器（右键复制坐标/NBT） | 14.3 |
| `/pw blocks reload` | 重载自定义积木 | 19 |
| `/pw templates` | 内置模板列表 | 12 |

## 双模式架构（第 1.3 章）

项目存于存档 `datapacks/packweaver-<ns>/`：`pack.mcmeta`、`data/` 与积木 AST 元数据 `packweaver.json` 共同构成唯一数据源。积木保存时生成合法 mcfunction（if/循环自动拆子函数、tick/load 自动注册 minecraft 标签、玩家加入/死亡自动生成进度触发器并带防重复守卫），IDE 模式手写的同名文件优先生效，两边随时互切不丢数据。

积木参数支持「📍 取当前坐标」——站在目标位置一键填入 XYZ（第 10.1 章「从游戏获取」）；「高级/自定义」分类随等级解锁（Lv.2 学徒，或 `/pw level unlock` 关闭限制，第 11.1 章）。

## 桥接协议（第 20 章）

- **TCP** `127.0.0.1:32005`（按行 JSON）：`ping` / `eval` / `reload` / `stats`
- **HTTP** `http://127.0.0.1:32006/pw/`（CORS）：`GET ping|stats`，`POST eval|reload|deploy?ns=xx`（部署 zip 进存档并重载）

两者均仅监听回环地址，外网不可访问。

## 自定义积木（第 19 章）

`config/packweaver/blocks/*.json`：

```json
{"type": "distance", "name": "计算距离", "command": "data get storage my_plugin:calc {key}"}
```

`/pw blocks reload` 后出现在「自定义」分类，`{key}` 成为可编辑参数。

## 构建

```bash
gradle build   # Gradle 8.7+ / JDK 17，产物 build/libs/packweaver-bridge-1.2.0.jar
```

## 目录结构

```
src/main/java/dev/packweaver/bridge/
├── PackWeaverBridge(.Client).java   # 入口：注册物品/命令/性能/双桥接/客户端命令
├── pack/
│   ├── BlockNode / BlockDefs        # 积木 AST 与定义注册表（含自定义积木）
│   ├── CodeGen                      # 积木⇄mcfunction 双向转换
│   ├── PackProject                  # 项目磁盘模型 / pack.mcmeta / zip 导出
│   ├── PackSnapshots                # 版本控制快照（保存/列表/恢复）
│   ├── Templates                    # 7 个内置模板
│   ├── Diag                         # 诊断引擎 + 快速修复
│   └── CommandExplainer             # 命令拆解
├── gui/                             # 项目/积木/参数/代码/诊断/Wiki/调试 7 个界面
├── bridge/                          # TCP + HTTP 桥接
├── client/                          # F12 叠加层 / 客户端命令 / 3D 区域预览 / 等级系统
├── command/ perf/ tools/            # /pw 服务端命令 / 性能 / 坐标复制器
```

## 路线图

- v1.3：断点/单步调试（游戏内执行轨迹）、积木自由拖拽画布
- v1.4：多人协作编辑、社区模板市场
- v1.5：Paper/Purpur/Folia 服务端适配
