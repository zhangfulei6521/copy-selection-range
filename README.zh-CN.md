# Copy Selection Range

[English](./README.md)

这是一个 IntelliJ IDEA 插件，用于将当前编辑器选区复制为：

```text
relative/path/File.java:startLine-endLine
```

如果没有选区，则复制为：

```text
relative/path/File.java:line
```

## 兼容性与环境要求

| 项目 | 要求 |
|---|---|
| IntelliJ IDEA | **2024.3 及以上**（IntelliJ Platform build **243+**） |
| 插件声明 | `since-build="243"`（未设置 `until-build`） |
| 构建 JDK | **JDK 17** |
| 构建工具 | 建议 Maven 3.8+ |
| 运行系统 | Windows / macOS / Linux |
| PowerShell（Windows 回退路径） | 推荐 **PowerShell 7+**（`pwsh.exe`），兼容 **Windows PowerShell 5.1**（`powershell.exe`） |

## 运行行为说明

1. 先把格式化后的路径与行号复制到剪贴板。
2. 再尝试发送到 IntelliJ IDEA 的 Terminal 标签页（包括 `codex` 等 CLI 会话）。
3. 在 Windows 上，如果无法发送到 IDEA Terminal，会回退到已打开的 CLI 窗口，模拟 `Ctrl+V` + `Enter`。
4. 如果没有可用终端窗口，则仅保留复制行为。

## 构建前准备

1. 安装 **JDK 17**，并确认 `java -version` 输出为 17。
2. 安装 **Maven**（建议 3.8+）。
3. 开发和验证建议使用 IntelliJ IDEA **2024.3+**。

## 构建命令

```bash
mvn clean package
```

如果全局 Maven 本地仓库不可用，可使用项目内缓存目录：

```bash
mvn -Dmaven.repo.local=.m2repo clean package
```

构建产物：

```text
target/copy-selection-range-plugin.zip
```

## 在 IDEA 中安装插件

1. 打开 `Settings` -> `Plugins`。
2. 点击齿轮图标。
3. 选择 `Install Plugin from Disk...`。
4. 选择 `target/copy-selection-range-plugin.zip`。

## 动作信息

- 名称：`Copy Selection Range`
- 菜单位置：编辑器右键菜单（`EditorPopupMenu`）
- 快捷键：`Ctrl + Alt + Shift + Y`

## 输出示例

- 多行选区：`src/main/java/com/example/Foo.java:66-72`
- 单行或仅光标：`src/main/java/com/example/Foo.java:66`
