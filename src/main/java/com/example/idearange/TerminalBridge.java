package com.example.idearange;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Bridges to IDEA's built-in terminal via reflection to avoid compile-time dependency.
 */
class TerminalBridge {
    private static final Logger LOG = Logger.getInstance(TerminalBridge.class);

    private static final List<String> CLI_KEYWORDS = List.of(
            "claude", "codex", "aider", "opencode", "qwen"
    );

    private static final List<String> TERMINAL_MANAGER_CLASS_NAMES = List.of(
            "org.jetbrains.plugins.terminal.TerminalToolWindowManager",
            "com.intellij.terminal.TerminalToolWindowManager"
    );

    private static final List<String> TERMINAL_UTIL_CLASS_NAMES = List.of(
            "org.jetbrains.plugins.terminal.TerminalUtil",
            "com.intellij.terminal.TerminalUtil"
    );

    private static final String CLI_PATTERN = String.join("|", CLI_KEYWORDS);

    /**
     * Send text to a terminal tab running a CLI tool (claude, codex, etc.).
     * Returns true if the text was successfully sent.
     */
    boolean sendToCliTerminal(Project project, String text) {
        try {
            Object manager = findTerminalManager(project);
            if (manager == null) {
                return false;
            }

            List<Object> widgets = collectWidgets(manager);
            if (widgets.isEmpty()) {
                return false;
            }

            // First try tabs that look like a CLI session.
            for (Object widget : widgets) {
                if (!isLikelyCliWidget(widget)) {
                    continue;
                }
                if (sendTextToWidget(widget, text)) {
                    return true;
                }
            }

            // Fallback: send to any terminal tab that accepts command input.
            for (Object widget : widgets) {
                if (sendTextToWidget(widget, text)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            LOG.debug("Terminal bridge failed", e);
            return false;
        }
    }

    private Object findTerminalManager(Project project) {
        for (String className : TERMINAL_MANAGER_CLASS_NAMES) {
            InvocationResult result = invokeStatic(className, "getInstance", project);
            if (result.invoked() && result.value() != null) {
                return result.value();
            }
        }
        return null;
    }

    private List<Object> collectWidgets(Object manager) {
        Set<Object> widgets = new LinkedHashSet<>();

        addWidget(widgets, invokeMethod(manager, "getActiveWidget").value());
        addWidget(widgets, invokeMethod(manager, "getSelectedWidget").value());
        addWidgets(widgets, invokeMethod(manager, "getWidgets").value());
        addWidgets(widgets, invokeMethod(manager, "getTerminalWidgets").value());
        addWidgets(widgets, invokeMethod(manager, "getAllWidgets").value());
        addWidget(widgets, invokeMethod(manager, "getLocalShellWidget").value());

        return new ArrayList<>(widgets);
    }

    private void addWidgets(Set<Object> widgets, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addWidget(widgets, item);
            }
            return;
        }
        if (value.getClass().isArray()) {
            int len = Array.getLength(value);
            for (int i = 0; i < len; i++) {
                addWidget(widgets, Array.get(value, i));
            }
            return;
        }
        addWidget(widgets, value);
    }

    private void addWidget(Set<Object> widgets, Object value) {
        if (value != null) {
            widgets.add(value);
        }
    }

    private boolean isLikelyCliWidget(Object widget) {
        if (hasCliProcess(widget)) {
            return true;
        }

        String marker = (widget.getClass().getName() + " " + widget).toLowerCase(Locale.ROOT);
        return CLI_KEYWORDS.stream().anyMatch(marker::contains);
    }

    private boolean hasCliProcess(Object shellWidget) {
        try {
            Object connector = getConnector(shellWidget);
            if (connector == null) {
                return false;
            }

            Object process = invokeMethod(connector, "getProcess").value();
            if (!(process instanceof Process p)) {
                return false;
            }

            long pid = p.pid();

            // Strategy 1: ProcessHandle API (fast, cross-platform)
            if (hasCliDescendant(pid, 0)) {
                return true;
            }

            // Strategy 2: Windows fallback via PowerShell (checks full command lines)
            if (isWindows()) {
                return checkCliDescendantsViaPS(pid);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasCliDescendant(long pid, int depth) {
        if (depth > 5) {
            return false;
        }
        return ProcessHandle.of(pid)
                .map(ph -> ph.children().anyMatch(child ->
                        isCliProcessHandle(child) || hasCliDescendant(child.pid(), depth + 1)))
                .orElse(false);
    }

    private boolean isCliProcessHandle(ProcessHandle ph) {
        String cmd = ph.info().command().orElse("").toLowerCase(Locale.ROOT);
        if (CLI_KEYWORDS.stream().anyMatch(cmd::contains)) {
            return true;
        }

        String args = String.join(" ", ph.info().arguments().orElse(new String[0])).toLowerCase(Locale.ROOT);
        return CLI_KEYWORDS.stream().anyMatch(args::contains);
    }

    private boolean checkCliDescendantsViaPS(long shellPid) {
        String script =
                "$pids=@(" + shellPid + ");"
                        + "for($i=0;$i -lt 5;$i++){$n=@();"
                        + "foreach($x in $pids){"
                        + "Get-CimInstance Win32_Process -Filter ('ParentProcessId='+$x) -EA 0|%%{"
                        + "if($_.CommandLine -match '" + CLI_PATTERN + "'){exit 0};"
                        + "$n+=$_.ProcessId}};$pids=$n};exit 1";

        for (String runner : new String[]{"pwsh.exe", "powershell.exe"}) {
            try {
                Process p = new ProcessBuilder(runner, "-NoProfile", "-NonInteractive",
                        "-Command", script).start();
                if (!p.waitFor(5, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                    continue;
                }
                return p.exitValue() == 0;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    private boolean sendTextToWidget(Object widget, String text) {
        if (invokeMethod(widget, "sendCommandToExecute", text).invoked()) {
            return true;
        }
        if (invokeMethod(widget, "executeCommand", text).invoked()) {
            return true;
        }
        if (invokeMethod(widget, "sendCommand", text).invoked()) {
            return true;
        }

        if (sendTextViaTty(widget, text)) {
            return true;
        }

        return sendViaTerminalUtil(widget, text);
    }

    private boolean sendTextViaTty(Object widget, String text) {
        Object connector = getConnector(widget);
        if (connector == null) {
            return false;
        }

        String payload = text + "\n";
        if (invokeMethod(connector, "sendString", payload).invoked()) {
            return true;
        }
        if (invokeMethod(connector, "write", payload).invoked()) {
            return true;
        }
        return invokeMethod(connector, "sendBytes", payload.getBytes(StandardCharsets.UTF_8)).invoked();
    }

    private Object getConnector(Object widget) {
        for (String name : new String[]{"getProcessTtyConnector", "getTtyConnector", "getConnector"}) {
            InvocationResult result = invokeMethod(widget, name);
            if (result.invoked() && result.value() != null) {
                return result.value();
            }
        }
        return null;
    }

    private boolean sendViaTerminalUtil(Object widget, String text) {
        for (String className : TERMINAL_UTIL_CLASS_NAMES) {
            Class<?> utilClass = findClass(className);
            if (utilClass == null) {
                continue;
            }

            for (Method method : utilClass.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                if (!"sendCommandToExecute".equals(method.getName()) || method.getParameterCount() != 2) {
                    continue;
                }
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (!isAssignable(parameterTypes[0], widget) || !isAssignable(parameterTypes[1], text)) {
                    continue;
                }
                try {
                    method.invoke(null, widget, text);
                    return true;
                } catch (Exception e) {
                    LOG.debug("TerminalUtil.sendCommandToExecute failed", e);
                }
            }
        }
        return false;
    }

    // --- Reflection helpers ---

    private static InvocationResult invokeStatic(String className, String methodName, Object... args) {
        try {
            Class<?> cls = findClass(className);
            if (cls == null) {
                return InvocationResult.notInvoked();
            }
            for (Method m : cls.getMethods()) {
                if (!m.getName().equals(methodName)
                        || !Modifier.isStatic(m.getModifiers())
                        || !isCompatible(m.getParameterTypes(), args)) {
                    continue;
                }
                try {
                    return new InvocationResult(true, m.invoke(null, args));
                } catch (Exception e) {
                    LOG.debug("Reflection: " + className + "." + methodName + " failed", e);
                }
            }
        } catch (Exception e) {
            LOG.debug("Reflection: " + className + "." + methodName + " failed", e);
        }
        return InvocationResult.notInvoked();
    }

    private static InvocationResult invokeMethod(Object obj, String methodName, Object... args) {
        if (obj == null) {
            return InvocationResult.notInvoked();
        }
        try {
            for (Method m : obj.getClass().getMethods()) {
                if (!m.getName().equals(methodName) || !isCompatible(m.getParameterTypes(), args)) {
                    continue;
                }
                try {
                    return new InvocationResult(true, m.invoke(obj, args));
                } catch (Exception e) {
                    LOG.debug("Reflection: " + obj.getClass().getSimpleName() + "." + methodName + " failed", e);
                }
            }
        } catch (Exception e) {
            LOG.debug("Reflection: " + obj.getClass().getSimpleName() + "." + methodName + " failed", e);
        }
        return InvocationResult.notInvoked();
    }

    private static Class<?> findClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static boolean isWindows() {
        String os = System.getProperty("os.name");
        return os != null && os.toLowerCase().contains("win");
    }

    private static boolean isCompatible(Class<?>[] parameterTypes, Object[] args) {
        if (parameterTypes.length != args.length) {
            return false;
        }
        for (int i = 0; i < parameterTypes.length; i++) {
            if (!isAssignable(parameterTypes[i], args[i])) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAssignable(Class<?> parameterType, Object arg) {
        if (arg == null) {
            return !parameterType.isPrimitive();
        }
        return box(parameterType).isInstance(arg);
    }

    private static Class<?> box(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return Void.class;
    }

    private record InvocationResult(boolean invoked, Object value) {
        static InvocationResult notInvoked() {
            return new InvocationResult(false, null);
        }
    }
}
