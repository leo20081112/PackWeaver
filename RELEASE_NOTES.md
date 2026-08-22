# PackWeaver Bridge v1.0.0 Release Notes

发布日期：2026-08-22

PackWeaver Bridge 是依据《PackWeaver v1.0 完整规划书》实现的游戏内配套 Mod（Fabric 1.20.1 / Java 17），为 PackWeaver 桌面端提供游戏内叠加层、热重载桥接、性能分析与坐标/NBT 工具。

## ✨ 新增功能

### 1. F12 游戏内多窗口叠加层（规划书第 13.1 / 扩展 D 章）
- `F12` 一键显示/隐藏半透明叠加层，不打断游戏
- 内置四个信息窗口：**性能**（FPS/MSPT/TPS）、**坐标**（XYZ/维度）、**桥接日志**（最近 50 条）、**帮助**
- `Shift+F12` 打开布局编辑器：拖拽标题栏移动窗口、按钮逐个开关
- 布局持久化到 `config/packweaver-overlay.json`，重启后保留

### 2. 热重载桥接服务器（规划书第 18 / 20 章）
- 本机 TCP JSON 协议（`127.0.0.1:32005`），供桌面端 PackWeaver 连接
- 指令：`ping` / `eval`（执行任意命令）/ `reload`（主线程触发数据包重载）/ `stats`
- 安全设计：仅监听回环地址，外部网络无法访问

### 3. 性能分析器（规划书第 8.3 / 14.7 章）
- 基于 100 tick 滚动窗口统计 MSPT 平均值/峰值、估算 TPS
- 三处输出：`/pw stats` 命令、F12 叠加层、桥接 `stats` 指令
- 自动分级：good（<40ms）/ warn（<50ms）/ bad

### 4. 坐标复制器（规划书第 14.3 / 14.4 章）
- 新物品「坐标复制器」，`/pw copier give` 获取
- 右键方块 → 复制 `X Y Z` 坐标到剪贴板
- Shift+右键 → 复制方块完整 NBT（含方块实体数据）
- 自带中英文 tooltip，客户端逻辑与服务器逻辑隔离，专用服务器可安全加载

### 5. `/pw` 命令系统
- `/pw reload` —— 数据包热重载
- `/pw stats` —— 性能报告
- `/pw bridge status` —— 桥接服务器状态
- `/pw copier give` —— 发放坐标复制器

## 📦 安装

1. 安装 Fabric Loader ≥ 0.14.21 与 Fabric API（1.20.1 版）
2. 从本 Release 下载 `packweaver-bridge-1.0.0.jar` 放入 `.minecraft/mods/`
3. 启动游戏，按 `F12` 验证叠加层出现

或从源码构建：`gradle build`（需要 Gradle 8.7+ 与 JDK 17）。

## 🐛 已知限制

- 叠加层为信息展示型窗口；穿透/捕获双模式将在 v1.1 提供
- `eval` 桥接指令拥有控制台权限，仅限本机开发环境使用（回环监听已保证这一点）
- 需要与其他绑定 F12 的 Mod 避免按键冲突（可在控制设置中改键）

## 🔗 相关链接

- 仓库：<https://github.com/leo20081112/-mod>
- 规划书：PackWeaver v1.0 完整规划书（第一至七部分 + 扩展 A–G）
