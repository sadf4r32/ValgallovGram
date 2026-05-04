package org.telegram.messenger;

import android.content.Context;
import android.os.Build;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Catches uncaught exceptions that would crash the app and writes a small
 * crash report to Downloads/valgallov_crash.txt so the user can fish it out
 * when ValgallovGram fails to launch.
 *
 * Falls through to the previously installed handler so default OS-level
 * "App has stopped" UI still appears.
 */
public final class ValgallovCrashHandler {

    private ValgallovCrashHandler() {}

    public static void install(final Context ctx) {
        try {
            final Thread.UncaughtExceptionHandler prev = Thread.getDefaultUncaughtExceptionHandler();
            Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
                try {
                    write(ctx, t, e);
                } catch (Throwable ignore) {
                }
                if (prev != null) prev.uncaughtException(t, e);
            });
        } catch (Throwable ignore) {
        }
    }

    private static void write(Context ctx, Thread t, Throwable e) throws Exception {
        StringWriter sw = new StringWriter(4096);
        PrintWriter pw = new PrintWriter(sw);
        pw.println("ValgallovGram crash report");
        pw.println("date: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(new Date()));
        try {
            pw.println("version: v8.1");
            pw.println("device: " + Build.MANUFACTURER + " " + Build.MODEL + " (api " + Build.VERSION.SDK_INT + ")");
            pw.println("thread: " + (t == null ? "null" : t.getName()));
        } catch (Throwable ignore) {
        }
        pw.println();
        if (e != null) e.printStackTrace(pw);
        pw.flush();

        // Try external Downloads first (user-accessible without root)
        File outFile = null;
        try {
            File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
            if (dl != null) {
                if (!dl.exists()) dl.mkdirs();
                outFile = new File(dl, "valgallov_crash.txt");
            }
        } catch (Throwable ignore) {
        }
        if (outFile == null && ctx != null) {
            outFile = new File(ctx.getFilesDir(), "valgallov_crash.txt");
        }
        if (outFile == null) return;
        try (FileWriter fw = new FileWriter(outFile, false)) {
            fw.write(sw.toString());
        }
    }
}
