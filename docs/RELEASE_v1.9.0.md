# PackWeaver Bridge v1.9.0 Release Notes

发布日期：2026-10-01

**设计工具与调试增强**（规划书第 5.1 / 14.1 / 14.6 / 16.2 章）：配方设计器、条件日志断点、JSON 格式化、正则测试器。全部功能经真实游戏自动化验证（9/9 新功能 + 16/16 回归 + 11/11 扩展 + 注入验证）。

## ✨ 新增功能

### 1. 配方设计器（规划书 5.1）
- `/pw recipe [ns]` 打开：3x3 材料网格 + 产物/数量/配方名
- 三种类型：有序合成（自动裁剪空行空列、相同材料合并 key 字母）/ 无序合成 / 熔炼
- 一键生成 1.20.1 配方 JSON 写入项目 `data/<ns>/recipes/<名>.json`，reload 后即生效
- 材料可留空，自动归一化 `minecraft:` 前缀

### 2. 条件日志断点（规划书 16.2 条件断点）
- 调试控制界面新增「断点条件」输入（execute if 语法）
- 注入行变为 `execute if <条件> if entity @a[tag=pw_debugger] run tellraw ...`
- 例：`score @s kills matches 3..` —— 只在分数 ≥3 时输出该行断点

### 3. JSON 格式化（规划书 14.1）
- IDE 模式「格式化」按钮：解析当前 JSON → prettyPrint 重排（自动去尾随逗号）
- 非 JSON 文件提示不支持

### 4. 正则测试器（规划书 14.6）
- `/pw regex` 打开：正则 + 测试文本实时匹配
- 匹配段绿底高亮、计数、分组明细（$1/$2…）、5 个常用模板（坐标/选择器参数/物品ID/NBT路径/数字）

## ✅ 质量验证

- 新功能套件 9/9：三种配方 JSON 磁盘断言、条件断点注入断言、断点清除、双界面打开、清理
- 全量回归：基础 16/16 + 扩展 11/11 + 注入验证，无回归
- `create_recipe` 支持项目不存在时自动创建

## 📦 安装

Fabric Loader ≥ 0.14.21 + Fabric API（1.20.1），放入 `mods/`。仅支持 **Fabric 1.20.x**。

## 🔗 相关链接

- 仓库：<https://github.com/leo20081112/PackWeaver>
- Wiki：<https://github.com/leo20081112/PackWeaver/wiki>
