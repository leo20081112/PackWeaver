# PackWeaver Sync v1.1.0 + Bridge v1.9.1 — 远程部署与安全（v1.10.0）

发布日期：2026-10-01

服务端插件补齐规划书第 20 章的**远程部署与安全**能力，并首次在**真实 Paper 服务端**上完成端到端运行时验证（12/12 开放模式 + 4/4 鉴权模式）。

## ✨ PackWeaver Sync v1.1.0（Paper/Purpur/Spigot）

### 远程访问安全
- **鉴权令牌**：`auth-token` 配置后，所有 `/pw` 端点要求请求头 `X-PW-Token`（错误/缺失 → 401）
- **监听地址**：`bind-address` 配置——`127.0.0.1`（默认，仅本机）/ `0.0.0.0`（局域网远程部署，务必配合 token）

### 部署备份
- 同名重复部署前**自动备份旧包**到 `plugins/PackWeaverSync/backups/<ns>/`（时间戳命名）
- `GET /pw/backups?ns=xx` —— 列出备份
- `GET /pw/backup?ns=xx&file=时间戳.zip` —— 下载旧版本
- 回滚流程 = 下载备份 → 重新 deploy；`backups-keep` 配置保留数量（默认 10）

### 修复
- **POST 请求体预读**：route 层提前消费请求体导致 deploy 拿到空 body（与 Mod 侧同款缺陷），改为端点内读取
- **pack 路径匹配**：`getWorldFolder()` 路径含 `.` 段时 `startsWith` 误判 404，改为双方 normalize 后比较

## 🔧 Bridge Mod v1.9.1

- 坐标复制器新增 **Ctrl+右键 = JSON 格式**（`{"x":..,"y":..,"z":..}`；普通右键仍为 `X Y Z`，Shift= NBT）
- HTTP `deploy` 同名替换前自动备份旧包（`config/packweaver/backups/deploy-<ns>/`，保留 5 份）

## ✅ 验证（真实 Paper 1.20.1 build 196）

- 插件加载无 severe，服务器 3.2s 启动
- 开放模式 12/12：ping/stats/deploy/list/pack/backups/backup/eval/reload/部署函数执行/防穿越/二次部署替换
- 鉴权模式 4/4：无 token 401 / 错 token 401 / 正确 token 200 / stats 带 token 200
- 部署数据包 reload 后 `function pw_e2e:load` 可执行；优雅停机验证

## 📦 下载

| 文件 | 适用 |
|---|---|
| `packweaver-sync-1.1.0.jar` | Paper/Purpur/Spigot 1.20.x 服务端 |
| `packweaver-bridge-1.9.1.jar` | Fabric 1.20.1 客户端/服务端 |

## 🔗 相关链接

- 仓库：<https://github.com/leo20081112/PackWeaver>
- Wiki：<https://github.com/leo20081112/PackWeaver/wiki>
