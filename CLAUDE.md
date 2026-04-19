# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

- **构建插件**: `mvn clean package` (需要 JDK 17)
- **使用本地 Maven 缓存构建**: `mvn -Dmaven.repo.local=.m2repo clean package`
- **运行测试**: `mvn test` (目前无测试源码，新增测试放在 `src/test/java` 下)
- **安装插件**: 将 `target/copy-selection-range-plugin.zip` 安装到 IDEA → Settings → Plugins → Install Plugin from Disk

## Project Overview

Maven 构建的 IntelliJ IDEA 插件 (JDK 17)，功能是将当前选区的文件路径和行号范围复制到剪贴板，格式为 `relative/path/File.java:startLine-endLine`，单行或无选区时为 `relative/path/File.java:line`。在 Windows 上还会尝试将剪贴板内容自动粘贴到已打开的 CLI 窗口 (codex、PowerShell 7、Windows Terminal)。

## Architecture

只有一个 Action 类: `CopySelectionRangeAction` (继承 `DumbAwareAction`)，所有逻辑集中在此类中:

1. **路径格式化** — `toProjectRelativePath()`: 将绝对路径转为项目相对路径，分隔符统一为 `/`
2. **选区处理** — `normalizeSelectionEnd()`: 将 selectionEnd 转为包含偏移量（减1），处理文档末尾边界
3. **剪贴板写入** — 通过 IntelliJ `CopyPasteManager` 设置内容
4. **Windows CLI 粘贴** — `trySendToCliWindowAsync()`: 异步执行 PowerShell 脚本，通过 `WScript.Shell` COM 对象激活目标窗口并发送 Ctrl+V + Enter

插件注册在 `plugin.xml` 中，Action 挂载在 `EditorPopupMenu`（编辑器右键菜单），快捷键 `Ctrl+Alt+Shift+Y`，要求 IDEA 版本 >= 243。

## Key Files

- `src/main/java/com/example/idearange/CopySelectionRangeAction.java` — 全部插件逻辑
- `src/main/resources/META-INF/plugin.xml` — 插件描述和 Action 注册
- `src/assembly/plugin-zip.xml` — Assembly 打包描述（ZIP 布局）
- `pom.xml` — 依赖 `com.jetbrains.intellij.platform:core` 和 `ide` (scope=provided)，IntelliJ Platform 版本 `243.25659.39`

## Coding Conventions

- Java 17, UTF-8, 4 空格缩进，大括号同行
- 包名: `com.example.idearange`
- 保持 `plugin.xml` 中的插件 ID 和 Action ID 向后兼容
