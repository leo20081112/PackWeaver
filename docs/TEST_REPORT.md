# PackWeaver 自动化测试报告

测试日期：2026-10-01 ｜ 实例：`F:\mc\.minecraft\versions\packweakertest`（Fabric Loader 0.14.22 + 45 mod 整合包）
被测版本：packweaver-bridge 1.5.3（含临时 MCP 调试钩子 `/pw/debug/*`，flag 门控）
测试方式：HTTP 调试钩子驱动 + QuickPlay 自动进世界 + 16 项功能断言

## 结果：16/16 通过 ✅

| # | 测试项 | 结果 | 说明 |
|---|---|---|---|
| 1 | create_project | ✅ | 你好世界模板生成 load/hello 函数 |
| 2 | run_load_function | ✅ | 集成服务器执行 `function ns:load`（result=4） |
| 3 | diag_no_fatal | ✅ | 诊断引擎真实运行（返回 BEST004 提示，无致命错误） |
| 4 | export_zip | ✅ | 导出 zip 到存档 datapacks |
| 5 | deploy_accepted | ✅ | HTTP 部署同名替换（disable/enable 释放句柄，replaced=true） |
| 6 | deployed_fn_runs | ✅ | 部署后函数可用 |
| 7 | open_graph_screen | ✅ | 蓝图画布真实打开（BlockGraphScreen） |
| 8 | open_ide_screen | ✅ | IDE 模式（CodeEditorScreen） |
| 9 | open_diag_screen | ✅ | 诊断报告（DiagScreen） |
| 10 | open_wiki_screen | ✅ | WikiScreen |
| 11 | open_project_screen | ✅ | ProjectScreen |
| 12 | toggle_overlay | ✅ | F12 叠加层开关 |
| 13 | screenshot_saved | ✅ | 截图落盘 |
| 14 | chat_pw_stats | ✅ | 玩家聊天执行 /pw stats |
| 15 | no_pw_errors | ✅ | 运行日志无 PackWeaver ERROR |
| 16 | cleanup | ✅ | 测试项目清理 |

## 本轮发现并修复的缺陷（按严重度）

1. **🔴 OverlayManager 静态初始化 NPE（v1.0.0 起所有机器启动崩溃的总根因）**：`INSTANCE` 声明在 `CONFIG` 之前，构造器读取未初始化的 CONFIG → `ExceptionInInitializerError` → 游戏启动即崩。此前"另一台机器闪退"与此同源（并非 Loader 版本问题）。修复字段顺序。
2. **🔴 `Set.of` 含重复元素（v1.1 起诊断/IDE 实际不可用）**：`Diag.COMMANDS/SELECTOR_ARGS`、`CodeEditorScreen.COMPLETIONS` 存在重复项，`Set.of` 遇重复抛异常 → 静态初始化失败 → 诊断/IDE 从未真正工作过。改为 `Set.copyOf(Arrays.asList(...))`。
3. **🟡 HTTP POST 请求体被预读**：`route()` 顶部消费请求体导致 `/pw/eval` 与调试路由拿到空 body。改为使用处读取。
4. **🟡 部署 zip 句柄锁**：已启用数据包的 zip 被服务器长期持有，同名重部署必失败。现采用 disable → 替换 → enable → reload 流程（Mod 与插件同步实现）。
5. **🟡 调试钩子提前关闭 exchange**：响应未发送连接已断。移除重复 close。

## 遗留说明

- 调试钩子为**临时功能**，仅在 `packweaver-debug.flag` 存在或 `-Dpackweaver.debug=true` 时激活（仅本机回环 32006），正式发布保留但默认关闭
- 测试脚本与报告：`E:\packweaker\test-run\`（test_suite.py / test-report.json / launch_test.bat）
