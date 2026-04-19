package com.example.idearange;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.SelectionModel;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.AppExecutorUtil;

import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CopySelectionRangeAction extends DumbAwareAction {
    private static final int SEND_TIMEOUT_SECONDS = 2;
    private static final List<String> CLI_PROCESS_NAMES = List.of(
            "codex",
            "pwsh",
            "pwsh-preview",
            "powershell",
            "wt",
            "WindowsTerminal"
    );
    private static final List<String> CLI_WINDOW_TITLES = List.of(
            "codex",
            "Codex",
            "pwsh",
            "PowerShell 7",
            "Windows Terminal",
            "Windows PowerShell",
            "PowerShell"
    );


    @Override
    public ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent e) {
        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(editor != null && virtualFile != null && e.getProject() != null);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
        if (e.getProject() == null) {
            return;
        }

        Editor editor = e.getData(CommonDataKeys.EDITOR);
        VirtualFile virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (editor == null || virtualFile == null) {
            return;
        }

        Document document = editor.getDocument();
        SelectionModel selectionModel = editor.getSelectionModel();

        String projectBasePath = e.getProject().getBasePath();
        String absolutePath = virtualFile.getPath();
        String displayPath = toProjectRelativePath(projectBasePath, absolutePath);

        String result;
        if (selectionModel.hasSelection()) {
            int startOffset = selectionModel.getSelectionStart();
            int endOffsetInclusive = normalizeSelectionEnd(document.getTextLength(), selectionModel.getSelectionEnd());

            int startLine = document.getLineNumber(startOffset) + 1;
            int endLine = document.getLineNumber(endOffsetInclusive) + 1;

            if (startLine == endLine) {
                result = displayPath + ":" + startLine;
            } else {
                result = displayPath + ":" + startLine + "-" + endLine;
            }
        } else {
            int caretOffset = editor.getCaretModel().getOffset();
            int line = document.getLineNumber(caretOffset) + 1;
            result = displayPath + ":" + line;
        }

        CopyPasteManager.getInstance().setContents(new StringSelection(result));
        trySendToTerminalAsync(e.getProject(), result);
    }

    private String toProjectRelativePath(String projectBasePath, String absolutePath) {
        if (projectBasePath == null || projectBasePath.isBlank()) {
            return absolutePath;
        }

        Path base = new File(projectBasePath).toPath().normalize();
        Path file = new File(absolutePath).toPath().normalize();

        try {
            return base.relativize(file).toString().replace(File.separatorChar, '/');
        } catch (Exception ignored) {
            return absolutePath;
        }
    }

    private int normalizeSelectionEnd(int textLength, int selectionEnd) {
        if (textLength <= 0) {
            return 0;
        }
        if (selectionEnd <= 0) {
            return 0;
        }
        if (selectionEnd >= textLength) {
            return textLength - 1;
        }
        return selectionEnd - 1;
    }

    private void trySendToTerminalAsync(Project project, String text) {
        AppExecutorUtil.getAppExecutorService().submit(() -> {
            TerminalBridge bridge = new TerminalBridge();
            if (bridge.sendToCliTerminal(project, text)) {
                return;
            }
            if (isWindows()) {
                trySendToPowerShellWindow();
            }
        });
    }

    private void trySendToPowerShellWindow() {
        String script = buildSendScript();

        List<List<String>> candidates = List.of(
                List.of("pwsh.exe", "-NoProfile", "-STA", "-NonInteractive", "-Command", script),
                List.of("pwsh.exe", "-NoProfile", "-NonInteractive", "-Command", script),
                List.of("powershell.exe", "-NoProfile", "-STA", "-NonInteractive", "-Command", script),
                List.of("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
        );
        for (List<String> candidate : candidates) {
            if (runCommand(candidate)) {
                return;
            }
        }
    }

    private String buildSendScript() {
        String processNames = toPowerShellArray(CLI_PROCESS_NAMES);
        String windowTitles = toPowerShellArray(CLI_WINDOW_TITLES);

        return "$wshell = New-Object -ComObject WScript.Shell;"
                + " $processNames = @(" + processNames + ");"
                + " foreach ($pn in $processNames) {"
                + "   $procs = Get-Process -Name $pn -ErrorAction SilentlyContinue"
                + "     | Where-Object { $_.MainWindowHandle -ne 0 }"
                + "     | Sort-Object StartTime -Descending;"
                + "   foreach ($p in $procs) {"
                + "     if ($wshell.AppActivate($p.Id)) {"
                + "       Start-Sleep -Milliseconds 80;"
                + "       $wshell.SendKeys('^v{ENTER}');"
                + "       exit 0"
                + "     }"
                + "   }"
                + " }"
                + " $titles = @(" + windowTitles + ");"
                + " foreach ($title in $titles) {"
                + "   if ($wshell.AppActivate($title)) {"
                + "     Start-Sleep -Milliseconds 80;"
                + "     $wshell.SendKeys('^v{ENTER}');"
                + "     exit 0"
                + "   }"
                + " }"
                + " exit 1";
    }

    private String toPowerShellArray(List<String> values) {
        return values.stream()
                .map(this::toSingleQuotedPowerShellLiteral)
                .collect(Collectors.joining(", "));
    }

    private String toSingleQuotedPowerShellLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private boolean runCommand(List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).start();
            if (!process.waitFor(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name");
        return osName != null && osName.toLowerCase().contains("win");
    }
}
