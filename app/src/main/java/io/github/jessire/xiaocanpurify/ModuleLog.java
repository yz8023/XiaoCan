package io.github.jessire.xiaocanpurify;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Locale;

import io.github.libxposed.api.XposedInterface;

/**
 * File-backed logger for the XiaoCanPurify module.
 *
 * <p>Logging is <b>disabled by default</b> and can be turned on from the module app's settings.
 * The setting lives in the framework remote preferences group {@link #PREFS_GROUP}
 * ({@link #KEY_ENABLED}), written from {@link MainActivity} and read back (read-only) in the
 * target process.</p>
 *
 * <p>When enabled, entries are written to
 * {@code Android/data/com.realtech.xiaocan/files/XiaoCanPurify.log} (plus a best-effort copy in
 * Downloads and the app-private files dir), and an uncaught-exception handler persists fatal stack
 * traces before the process dies.</p>
 */
public final class ModuleLog {
    public static final String PREFS_GROUP = "xiaocanpurify";
    public static final String KEY_ENABLED = "enable_log";

    private static final String TAG = "XiaoCanPurify";
    private static final String FILE_NAME = "XiaoCanPurify.log";
    private static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final int BUFFER_LIMIT = 1000;

    private static final Object LOCK = new Object();
    private static final ArrayDeque<String> PENDING = new ArrayDeque<>();
    private static final SimpleDateFormat STAMP =
            new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US);

    private static volatile XposedInterface xposed;
    private static volatile Context appContext;
    private static volatile File[] sinks;
    private static volatile boolean crashHandlerInstalled;
    private static volatile Boolean enabled;

    private ModuleLog() {}

    /** Called once the module entry is loaded so the remote setting can be read. */
    public static void bind(XposedInterface x) {
        xposed = x;
        enabled = null;
    }

    /** Whether logging is currently enabled; reads the module's remote preference. */
    public static boolean isEnabled() {
        Boolean e = enabled;
        if (e == null) {
            enabled = e = readEnabled();
        }
        return e;
    }

    private static boolean readEnabled() {
        XposedInterface x = xposed;
        if (x == null) {
            return false;
        }
        try {
            SharedPreferences prefs = x.getRemotePreferences(PREFS_GROUP);
            return prefs.getBoolean(KEY_ENABLED, false);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Called as soon as a usable {@link Context} is available in the target process. */
    public static void attachContext(Context context) {
        if (context == null || appContext != null) {
            return;
        }
        try {
            Context app = context.getApplicationContext();
            appContext = app != null ? app : context;
            if (!isEnabled()) {
                return;
            }
            installCrashHandler();
            synchronized (LOCK) {
                sinks = resolveSinks(appContext);
                flushPendingLocked();
            }
            log("Log file: " + describeSinks());
        } catch (Throwable ignored) {
        }
    }

    public static void log(String message) {
        write("I", message, null);
    }

    public static void log(String message, Throwable tr) {
        write(tr == null ? "I" : "E", message, tr);
    }

    private static void write(String level, String message, Throwable tr) {
        if (!isEnabled()) {
            return;
        }
        String line;
        try {
            line = format(level, message, tr);
        } catch (Throwable t) {
            line = level + ": " + message;
        }
        if ("E".equals(level)) {
            Log.e(TAG, message, tr);
        } else {
            Log.i(TAG, message, tr);
        }
        synchronized (LOCK) {
            if (sinks == null) {
                if (appContext == null) {
                    if (PENDING.size() >= BUFFER_LIMIT) {
                        PENDING.pollFirst();
                    }
                    PENDING.addLast(line);
                    return;
                }
                sinks = resolveSinks(appContext);
                flushPendingLocked();
            }
            appendLocked(line);
        }
    }

    private static void flushPendingLocked() {
        if (sinks == null && appContext != null) {
            sinks = resolveSinks(appContext);
        }
        if (sinks == null) {
            return;
        }
        while (!PENDING.isEmpty()) {
            appendLocked(PENDING.pollFirst());
        }
    }

    private static void appendLocked(String line) {
        File[] targets = sinks;
        if (targets == null) {
            return;
        }
        for (File file : targets) {
            try {
                if (file.length() > MAX_BYTES) {
                    FileWriter reset = new FileWriter(file, false);
                    reset.write("");
                    reset.close();
                }
                FileWriter writer = new FileWriter(file, true);
                writer.write(line);
                writer.write('\n');
                writer.close();
            } catch (Throwable ignored) {
            }
        }
    }

    private static File[] resolveSinks(Context context) {
        ArrayDeque<File> files = new ArrayDeque<>();
        try {
            File dir = context.getExternalFilesDir(null);
            if (dir != null) {
                if (!dir.exists()) {
                    dir.mkdirs();
                }
                if (dir.isDirectory()) {
                    files.add(new File(dir, FILE_NAME));
                }
            }
        } catch (Throwable ignored) {
        }
        try {
            File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (downloads != null && downloads.isDirectory() && downloads.canWrite()) {
                files.add(new File(downloads, FILE_NAME));
            }
        } catch (Throwable ignored) {
        }
        try {
            File internal = context.getFilesDir();
            if (internal != null) {
                files.add(new File(internal, FILE_NAME));
            }
        } catch (Throwable ignored) {
        }
        return files.isEmpty() ? null : files.toArray(new File[0]);
    }

    private static String describeSinks() {
        File[] targets = sinks;
        if (targets == null || targets.length == 0) {
            return "(unresolved)";
        }
        StringBuilder sb = new StringBuilder();
        for (File file : targets) {
            if (sb.length() > 0) {
                sb.append(" | ");
            }
            sb.append(file.getAbsolutePath());
        }
        return sb.toString();
    }

    private static void installCrashHandler() {
        if (crashHandlerInstalled) {
            return;
        }
        crashHandlerInstalled = true;
        try {
            final Thread.UncaughtExceptionHandler previous = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
                try {
                    write("E", "FATAL uncaught exception on thread \"" + thread.getName() + "\"", throwable);
                } catch (Throwable ignored) {
                }
                if (previous != null) {
                    previous.uncaughtException(thread, throwable);
                }
            });
        } catch (Throwable ignored) {
        }
    }

    private static String format(String level, String message, Throwable tr) {
        StringBuilder sb = new StringBuilder();
        synchronized (STAMP) {
            sb.append(STAMP.format(new Date()));
        }
        sb.append(' ').append(level).append('/').append(Thread.currentThread().getName())
                .append(": ").append(message);
        if (tr != null) {
            sb.append('\n').append(stackTrace(tr));
        }
        return sb.toString();
    }

    private static String stackTrace(Throwable tr) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        tr.printStackTrace(pw);
        pw.flush();
        return sw.toString();
    }
}