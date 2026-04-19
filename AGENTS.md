# Repository Guidelines

## Project Structure & Module Organization
This repository is a Maven-based IntelliJ IDEA plugin (JDK 17).

- `src/main/java/com/example/idearange/`: plugin logic (`CopySelectionRangeAction`).
- `src/main/resources/META-INF/plugin.xml`: plugin ID, action registration, shortcut, menu placement.
- `src/main/resources/messages/`: message bundles (currently `MyBundle.properties`).
- `src/assembly/plugin-zip.xml`: ZIP packaging layout for IDEA installation.
- `target/`: build artifacts (`.jar` and `copy-selection-range-plugin.zip`).
- `.m2repo/`: optional local Maven repository cache for restricted environments.

## Build, Test, and Development Commands
- `mvn clean package`  
  Compiles the plugin JAR and creates `target/copy-selection-range-plugin.zip`.
- `mvn -Dmaven.repo.local=.m2repo clean package`  
  Uses the project-local Maven cache when global cache is unavailable.
- `mvn test`  
  Runs test phase (currently no test sources; keep this command green as tests are added).

Install locally in IDEA from `target/copy-selection-range-plugin.zip`.

## Coding Style & Naming Conventions
- Java 17, UTF-8, 4-space indentation, braces on same line.
- Class names: `PascalCase` (for example, `CopySelectionRangeAction`).
- Methods/fields: `camelCase`; constants: `UPPER_SNAKE_CASE`.
- Keep package names lowercase and stable (`com.example.idearange` unless intentionally migrated).
- Keep plugin/action IDs in `plugin.xml` backward-compatible when possible.

## Testing Guidelines
- No dedicated test suite is committed yet.
- Add tests under `src/test/java` when introducing logic-heavy behavior.
- Prefer deterministic unit tests for path/range formatting and OS-branch behavior.
- Suggested naming: `*Test` (for example, `CopySelectionRangeActionTest`).

## Commit & Pull Request Guidelines
Git history is not available in this workspace snapshot (`.git` is missing), so no existing convention can be inferred here.

- Use clear, imperative commit subjects (recommended: Conventional Commits, e.g., `feat: add Linux clipboard fallback`).
- PRs should include:
  - what changed and why,
  - manual verification steps (IDEA version, OS, shortcut/menu path),
  - screenshots or short terminal snippets when behavior/UI changed.

## Security & Configuration Tips
- The action can trigger PowerShell window automation on Windows; keep this behavior opt-in by code path and avoid broad command execution changes.
- Do not commit personal IDEA settings or machine-specific secrets.
