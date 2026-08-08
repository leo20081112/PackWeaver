# PackWeaver

> **PackWeaver — In-Game Datapack Studio**
> 在 Minecraft 1.21.1 游戏内编辑、管理与热重载数据包的多平台项目（Fabric 客户端 mod + Paper 服务端插件 + 跨平台共享库）。支持类 Scratch 积木编辑与 VSCode 风格 IDE 双模式、中文自动补全、文本组件可视化编辑、内置原版数据包手册、新手模板与一键重载。

## 功能特性

### 双模式编辑器
- **积木模式**：类 Scratch，拖拽「事件 / 条件 / 动作」积木组装数据包逻辑，画布缩放/平移/分组
- **IDE 文本模式**：VSCode 风格，文件树 + 行号 + 语法高亮（mcfunction/JSON）+ 多标签 + 自动补全浮层
- 两模式一键切换（默认 `M`），状态双向同步

### 自动补全（含中文解释）
- 函数名 / 命名空间、NBT 路径 / 记分板、文本组件字段
- 每个候选项附中文说明与示例

### 文本组件可视化编辑器
- 样式面板（颜色 / 加粗 / 斜体 / 删除线 / 字体）、clickEvent / hoverEvent 绑定（含命令合法性校验）
- 底层 JSON 双向同步、实时预览

### 内置原版数据包手册
- 离线可搜索的命令 / 方块 / 物品 / 实体 / 标签 / NBT / 文本组件格式参考（中文说明 + 示例）
- 可选联网查询 Minecraft Wiki

### 新手优化
- 首次打开分步引导、预设数据包模板（每刻通知 / 玩家加入欢迎 / 击杀计数奖励）
- 中文友好错误提示与修复建议、积木 tooltip

### 一键重载 + 冲突协调
- 编辑器内按钮 / 快捷键（默认 `R`）完成「编译 → 写入 → 热重载」
- 按环境路由：单机本地重载；连接装插件的服务端由服务端统一重载（并发请求串行化合并）；未装插件则提示
- 服务端 `/dpe reload` 同样走串行化队列

### 可配置按键绑定
- 设置屏幕自定义所有快捷键，持久化到 `config/packweaver/config.json`，可重置默认

## 模块

| 模块 | 路径 | 平台 | 构建插件 |
|------|------|------|----------|
| common | `common/` | 纯 Java 库 | `java-library` |
| mod | `mod/` | Fabric 客户端 | `fabric-loom` |
| plugin | `plugin/` | Paper 服务端 | `io.papermc.paperweight.userdev` |

## 关键版本

- Minecraft：1.21.1
- Yarn mappings：1.21.1+build.3
- Fabric Loader：0.16.9
- Fabric API：0.105.0+1.21.1
- Fabric Loom：1.7-SNAPSHOT
- Paperweight：1.7.3
- Java：21（通过 Gradle toolchain + `foojay-resolver-convention` 自动下载 JDK 21）

## 构建命令

```bash
# 构建全部模块
./gradlew build

# 仅构建 common
./gradlew :common:build

# 构建 Fabric mod
./gradlew :mod:build

# 构建 Paper plugin
./gradlew :plugin:build
```

构建产物：

- mod jar：`mod/build/libs/*.jar`
- plugin jar：`plugin/build/libs/*.jar`

## 使用方法

### 客户端 mod（单机 / 客户端）
1. 安装 Fabric Loader + Fabric API 到 Minecraft 1.21.1 客户端
2. 把 `mod/build/libs/packweaver-*.jar` 放进 `.minecraft/mods/`
3. 进游戏后按 `K` 打开编辑器（键位可在设置屏幕自定义）
4. `M` 切换积木/IDE 模式，`R` 一键重载，`P` 切换调色板，`F1` 帮助

### 服务端插件（Paper）
1. 把 `plugin/build/libs/packweaver-*.jar` 放进服务端 `plugins/`
2. 重启服务器（需 op 权限 `dpe.use`）
3. 命令：
   ```
   /dpe                      # 打开编辑器（mod 客户端弹 GUI；无 mod 走聊天菜单）
   /dpe chat                 # 可点击聊天菜单
   /dpe add <schemaId>       # 添加积木
   /dpe list                 # 列出当前积木
   /dpe reload               # 一键重载（编译→写入→热重载）
   /dpe wiki [关键词] [页]    # 查询原版手册
   /dpe template list        # 列出预设模板
   /dpe template <id>        # 加载模板
   /dpe help                 # 帮助
   ```
   别名：`/datapackeditor`、`/dpe`、`/packweaver`、`/pw`

### 客户端 mod + 服务端插件协同
- 客户端按 `K` 用原生 GUI 编辑，操作通过 `dpe:msg` 通道同步到服务端
- 重载由服务端统一执行（避免双重重载），多玩家编辑状态实时同步

## 目录结构

```
PackWeaver/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew / gradlew.bat
  gradle/wrapper/
  common/   src/main/java/com/dpe/common/  (model/block/compile/complete/text/editor/protocol/config/manual/template/parse/reload)
  mod/      src/main/java/com/dpe/client/  + resources/fabric.mod.json + assets/packweaver/lang/
  plugin/   src/main/java/com/dpe/server/  + resources/plugin.yml
  .github/workflows/  build.yml, release.yml
```

## 发布到 GitHub

本仓库已关联远程 `origin`。如需重新关联：
```bash
gh repo create PackWeaver --source=. --remote=origin --push
```

## 许可证

MIT，详见 [LICENSE](LICENSE)。
