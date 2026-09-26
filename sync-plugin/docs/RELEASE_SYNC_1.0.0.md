# PackWeaver Sync v1.0.0 Release Notes（v1.6.0 仓库版本）

发布日期：2026-09-26

依据规划书第 19.1 / 20 章实现的**服务端同步插件**：Paper/Purpur/Spigot 服务端不装 Mod 即可被 PackWeaver 桌面端同步数据包——部署、热重载、双向同步、性能监控。

## ✨ 功能

### HTTP 桥接（与 Fabric Mod 同协议，带 CORS）
- `GET /pw/ping` —— 连通性（返回服务端类型）
- `GET /pw/stats` —— MSPT / TPS / 在线人数
- `GET /pw/list` —— 列出存档 datapacks 目录（双向同步）
- `GET /pw/pack?name=xx` —— 下载数据包 zip
- `POST /pw/eval` —— 主线程执行控制台命令（5 秒超时保护）
- `POST /pw/reload` —— 数据包热重载（`minecraft:reload`，玩家无感知，不重启服务器）
- `POST /pw/deploy?ns=xx` —— 上传数据包 zip → 写入存档 → 自动重载

### TCP 桥接（与 Mod v1.0 完全同协议）
- 按行 JSON：`ping` / `eval` / `reload` / `stats`

### 服务端命令
- `/pws reload` —— 数据包热重载
- `/pws stats` —— 性能报告
- `/pws bridge` —— 桥接端口状态

### 安全设计
- 仅监听 `127.0.0.1` 回环地址，外网不可访问
- eval/reload 在主线程执行并带超时保护，桥接线程不碰 Bukkit API
- 文件名白名单清洗（`[^a-zA-Z0-9_-]`）+ normalize 校验，防路径穿越
- 下载接口仅允许 zip 后缀

### 兼容性
- 纯 Bukkit 调度实现性能统计，Spigot / Paper / Purpur / Folia 通用
- `api-version: 1.20`，Java 17
- 与 Fabric Mod 可同时使用（Mod 管客户端体验，插件管服务端同步；部署文件名同为 `packweaver-<ns>.zip` 体系）

## 📦 安装

1. `packweaver-sync-1.0.0.jar` 放入服务器 `plugins/` 目录并重启
2. `/pws bridge` 确认桥接已启动
3. 端口可在 `plugins/PackWeaverSync/config.yml` 调整

## 🐛 已知限制

- eval 的命令输出进服务器控制台日志，HTTP 应答只返回执行成功与否
- 需要与其他占用 32005/32006 端口的程序错开（与 Fabric Mod 客户端同机时，客户端 Mod 占用同一端口——插件仅装在服务端，正常部署不冲突）

## 🔗 相关链接

- 仓库：<https://github.com/leo20081112/PackWeaver>
- v1.5.0：积木块扩充（55 个）+ 蓝图原生
