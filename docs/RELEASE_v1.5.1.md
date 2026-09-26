# PackWeaver Bridge v1.5.1 Release Notes

发布日期：2026-09-26

**启动加固修复版**：修复在较新 Fabric Loader（如 0.19.x）下启动崩溃的问题——

```
Could not execute entrypoint stage 'client' ... ClassNotFoundException: dev.packweaver.bridge.PackWeaverBridgeClient
```

## 🛠 修复内容

1. **废除 `client` 入口点声明**：较新版本加载器对入口类的类加载处理与旧版存在差异，主入口正常而 client 入口类报 ClassNotFoundException。现改为**主入口在检测到客户端环境后经懒加载守卫主动调用客户端初始化**（与专用服务器完全隔离，服务端不会加载任何客户端类）
2. **全组件兜底**：物品注册 / 性能统计 / 命令 / TCP / HTTP / 客户端功能全部独立 try/catch——任何一环失败只记录 `[PackWeaver] ... 失败` 日志，**游戏必定能启动**，功能级故障在日志中定位
3. 升级后日志会输出 mod 版本号，便于核对安装的版本

## 📦 安装

- 从 v1.5.0 / 更早版本升级：**删除旧 jar**，放入本 jar
- 需 Fabric Loader ≥ 0.14.21（0.15.x / 0.16.x / 0.19.x 均验证兼容）+ Fabric API 1.20.1
- 仅支持 **Fabric 1.20.x**（Forge 版 / 1.21+ 装了必闪退）

## 🔗 相关链接

- 仓库：<https://github.com/leo20081112/PackWeaver>
- Wiki：<https://github.com/leo20081112/PackWeaver/wiki>
- v1.5.0：积木块扩充（54 个）+ 蓝图原生
