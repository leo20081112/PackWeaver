# In-Game Datapack Editor

> 在 Minecraft 1.21.1 游戏内编辑与管理 datapack 的多平台项目（Fabric 客户端 mod + Paper 服务端插件 + 跨平台共享库）。

## 发布到 GitHub

本仓库已在本地完成 `git init`（未提交）。要发布到 GitHub：

1. 先完成 GitHub CLI 登录（交互式，需在本地终端执行）：
   ```bash
   gh auth login
   ```
2. 然后在仓库根目录执行以下命令，即可创建远程仓库并推送：
   ```bash
   gh repo create in-game-datapack-editor --source=. --remote=origin --push
   ```

该命令会用当前目录创建名为 `in-game-datapack-editor` 的 GitHub 仓库，添加 `origin` 远程并推送当前分支。

## 用途

提供在游戏内浏览、编辑、热重载 datapack 的能力：

- **客户端 mod（Fabric）**：游戏内 GUI 编辑器入口（`client` entrypoint）。
- **服务端插件（Paper）**：datapack 管理、校验、热重载，提供 `/datapackeditor`（别名 `/dpe`）命令。
- **common**：跨平台共享逻辑（纯 Java 库），被 mod 与 plugin 共同依赖。

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

> 生成/更新 Gradle Wrapper：`gradle wrapper --gradle-version 8.10`（仓库已包含 wrapper）。

## 命令 `/dpe`

服务端插件注册命令 `datapackeditor`，别名 `dpe`：

```
/dpe help      # 查看帮助
/dpe list      # 列出 datapack
/dpe reload    # 重载 datapack
```

## 目录结构

```
in-game-datapack-editor/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  gradlew / gradlew.bat
  gradle/wrapper/
  common/   src/main/java/com/dpe/common/
  mod/      src/main/java/com/dpe/client/  + resources/fabric.mod.json
  plugin/   src/main/java/com/dpe/server/  + resources/plugin.yml
  .github/workflows/  build.yml, release.yml
```

## 许可证

MIT，详见 [LICENSE](LICENSE)。
