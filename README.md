# Copy Selection Range

[中文说明](./README.zh-CN.md)

IntelliJ IDEA plugin that copies the current editor selection as:

```text
relative/path/File.java:startLine-endLine
```

If no selection exists, it copies:

```text
relative/path/File.java:line
```

## Compatibility and Requirements

| Item | Requirement |
|---|---|
| IntelliJ IDEA | **2024.3+** (IntelliJ Platform build **243+**) |
| Plugin metadata | `since-build="243"` (no `until-build` set) |
| JDK for building | **JDK 17** |
| Maven for building | Maven 3.8+ recommended |
| OS runtime | Windows / macOS / Linux |
| PowerShell (Windows fallback) | **PowerShell 7+** (`pwsh.exe`) recommended, **Windows PowerShell 5.1** (`powershell.exe`) supported |

## Runtime Behavior

1. Always copies the formatted range to clipboard first.
2. Tries to send the text to an IntelliJ IDEA Terminal tab (including CLI tabs such as `codex`).
3. On Windows, if IDEA terminal send is unavailable, it falls back to an existing CLI window by simulating `Ctrl+V` + `Enter`.
4. If no target terminal/window is found, it remains copy-only.

## Environment Setup (Build)

1. Install **JDK 17** and make sure `java -version` reports 17.
2. Install **Maven** (3.8+ recommended).
3. Use IntelliJ IDEA **2024.3+** for development and verification.

## Build

```bash
mvn clean package
```

If your global Maven local repository is unavailable:

```bash
mvn -Dmaven.repo.local=.m2repo clean package
```

Build output:

```text
target/copy-selection-range-plugin.zip
```

## Install Plugin in IDEA

1. Open `Settings` -> `Plugins`.
2. Click the gear icon.
3. Choose `Install Plugin from Disk...`.
4. Select `target/copy-selection-range-plugin.zip`.

## Action

- Name: `Copy Selection Range`
- Menu: Editor right-click menu (`EditorPopupMenu`)
- Shortcut: `Ctrl + Alt + Shift + Y`

## Example Output

- Multi-line selection: `src/main/java/com/example/Foo.java:66-72`
- Single line or caret only: `src/main/java/com/example/Foo.java:66`
