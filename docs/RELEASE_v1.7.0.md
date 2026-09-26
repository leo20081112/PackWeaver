# PackWeaver v1.7.0 — 稳定性修复轮

发布日期：2026-09-26

对 Mod（v1.5.2）与服务端插件（v1.0.2）做了一轮系统性 bug 排查，修复 3 个确认缺陷并优化部署链路。

## 🐛 Mod 修复（v1.5.2）

1. **诊断一键修复崩溃**：JSON001「修复」按钮的正则缺少右括号，点击必抛 `PatternSyntaxException`——快速修复功能此前完全不可用，已修复
2. **LOG003 漏检**：函数**直接调用自己**（最常见的死循环写法）检测不到，条件表达式已修正，现在任何形式的循环调用都会报告
3. **部署接口 500**：Mod 与插件的 `sanitize` 同一处笔误（用清洗前长度对清洗后字符串做 substring），URL 携带非法字符时抛 `StringIndexOutOfBoundsException`
4. **JSON 保存规范化**：IDE 模式保存 JSON 时自动去除尾随逗号后写入（此前只校验不落盘规范化）

## ⚙ 服务端插件优化（v1.0.2）

1. **原子部署**：zip 先写 `.tmp` 临时文件再原子替换（ATOMIC_MOVE，不支持时降级 REPLACE），服务器重载时永远不会读到半截数据包
2. **体积上限**：`max-deploy-mb` 配置（默认 64MB），Content-Length 超限直接 413，防异常请求占满内存
3. **`GET /pw/list` 增强**：同时列出文件夹型数据包（新增 `type: zip|dir` 字段）
4. 配置文件新增 `max-deploy-mb` 项

## 📦 下载

| 组件 | 文件 | 适用 |
|---|---|---|
| Fabric Mod | `packweaver-bridge-1.5.2.jar` | 客户端/服务端 Fabric 1.20.1 |
| 服务端插件 | `packweaver-sync-1.0.2.jar` | Paper/Purpur/Spigot 1.20.x |

升级：删除旧 jar 后放入新 jar。Wiki：https://github.com/leo20081112/PackWeaver/wiki
