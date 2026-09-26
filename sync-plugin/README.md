# PackWeaver Sync — 服务端同步插件

依据《PackWeaver v1.0 完整规划书》第 19.1 / 20 章：「插件承担 API 调用层，替代 Mod」。本插件让 **Paper / Purpur / Spigot** 服务端无需安装任何 Mod 即可被 PackWeaver 桌面端 / 外部工具同步数据包：部署 → 热重载 → 状态查询，全程不重启服务器。

| 项目 | 说明 |
|---|---|
| 平台 | Paper / Purpur / Spigot 1.20.x（api-version 1.20，Java 17） |
| 插件名 | PackWeaverSync |
| 版本 | 1.0.0 |

## 功能

- **HTTP 桥接** `http://127.0.0.1:<port>/pw/`（与 Fabric Mod 同协议，带 CORS）
  - `GET ping` / `stats`（MSPT/TPS/在线人数）
  - `GET list` —— 列出存档 datapacks 目录内容
  - `GET pack?name=xx` —— 下载数据包 zip（双向同步）
  - `POST eval` —— 主线程执行控制台命令
  - `POST reload` —— 数据包热重载（`minecraft:reload`，**不是** Bukkit 插件 reload）
  - `POST deploy?ns=xx` —— 上传数据包 zip 写入存档并自动重载
- **TCP 桥接**（与 Mod v1.0 完全同协议）：按行 JSON `ping` / `eval` / `reload` / `stats`
- **性能统计**：纯 Bukkit 实现，Spigot 也能用（100 tick 滚动窗口）
- **/pws 命令**：`/pws reload`、`/pws stats`、`/pws bridge`
- **安全**：仅监听 `127.0.0.1` 回环；文件名白名单清洗防路径穿越；下载仅限 zip

## 安装

1. 服务端放入 `plugins/`，重启或用 PlugManX 加载
2. `plugins/PackWeaverSync/config.yml` 可改端口（默认 HTTP 32006 / TCP 32005）
3. 验证：`/pws bridge`，或浏览器打开 `http://127.0.0.1:32006/pw/ping`

## 与 Fabric Mod 的关系

| | Fabric Mod（packweaver-bridge） | Sync 插件（packweaver-sync） |
|---|---|---|
| 环境 | 客户端/服务端 Fabric 1.20.1 | 服务端 Paper/Spigot 1.20.x |
| 游戏内积木/代码编辑 | ✔ | ✘（无客户端界面） |
| HTTP/TCP 桥接、部署热重载 | ✔ | ✔（协议相同，客户端可通用） |
| F12 叠加层、坐标复制器 | ✔ | ✘（需客户端 Mod） |

规划书定位：**服务端用插件承担 API 层，玩家无需安装任何 Mod**。

## 构建

```bash
# 在仓库根目录
build.bat build   # 需先 cd sync-plugin；或：
gradle -p sync-plugin build   # 产物 sync-plugin/build/libs/packweaver-sync-1.0.0.jar
```
