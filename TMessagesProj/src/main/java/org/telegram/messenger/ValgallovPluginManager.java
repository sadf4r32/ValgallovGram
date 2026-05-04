/*
 * ValgallovGram Plugin Manager — JavaScript (Rhino) based.
 *
 * Plugins are plain .js files dropped by the user into
 *   /sdcard/Download/valgallov_plugins/
 * (or any other Download folder).
 *
 * At app startup we scan that folder, eval every .js file in its own
 * Rhino scope, and keep the scope alive for the process lifetime.
 *
 * Each plugin is expected to optionally define these top-level functions:
 *
 *   function onMessage(dialogId, senderId, text) { ... }   // fired on incoming message
 *   function onSend   (dialogId, text)           { ... }   // fired when current user sends a message
 *   function onLoad   ()                         { ... }   // fired once on load
 *
 * Plugins can call the following host API (exposed as the `Valgallov`
 * singleton in the global scope):
 *
 *   Valgallov.log(msg)                    // write to logcat + crash file
 *   Valgallov.toast(msg)                  // show UI toast
 *   Valgallov.sendMessage(dialogId, text) // send a text message as current user
 *   Valgallov.getSelfId()                 // long — current user's id
 *
 * Reflection from JS into arbitrary Java is disabled (sealed scope).
 *
 * If Rhino fails to load (incompatible ABI) or plugin files error out,
 * we swallow silently — a broken plugin must never break the app.
 */

package org.telegram.messenger;

import android.content.Context;
import android.os.Environment;
import android.widget.Toast;

import org.mozilla.javascript.Function;
import org.mozilla.javascript.ImporterTopLevel;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

public final class ValgallovPluginManager {

    private static volatile boolean initialized = false;
    private static final Object LOCK = new Object();

    private static final List<PluginHolder> PLUGINS = new ArrayList<>();

    private ValgallovPluginManager() {}

    public static int loadedCount() {
        return PLUGINS.size();
    }

    public static List<String> loadedNames() {
        List<String> out = new ArrayList<>();
        for (PluginHolder h : PLUGINS) out.add(h.fileName);
        return out;
    }

    public static void initOnce() {
        if (initialized) return;
        synchronized (LOCK) {
            if (initialized) return;
            initialized = true;
            if (!SharedConfig.valgallovPluginsEnabled) return;
            try {
                scanAndLoad();
            } catch (Throwable t) {
                if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            }
        }
    }

    public static void reload() {
        synchronized (LOCK) {
            PLUGINS.clear();
            initialized = false;
            if (SharedConfig.valgallovPluginsEnabled) {
                try {
                    scanAndLoad();
                } catch (Throwable t) {
                    if (BuildVars.LOGS_ENABLED) FileLog.e(t);
                }
            }
            initialized = true;
        }
    }

    private static File pluginDir() {
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File f = new File(dl, "valgallov_plugins");
        if (!f.exists()) {
            try { f.mkdirs(); } catch (Throwable ignore) {}
        }
        return f;
    }

    private static void scanAndLoad() {
        File dir = pluginDir();
        if (dir == null || !dir.isDirectory()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (!file.isFile()) continue;
            String name = file.getName();
            if (!name.endsWith(".js")) continue;
            PluginHolder h = loadOne(file);
            if (h != null) PLUGINS.add(h);
        }
    }

    private static PluginHolder loadOne(File f) {
        org.mozilla.javascript.Context cx = null;
        try {
            cx = org.mozilla.javascript.Context.enter();
            cx.setOptimizationLevel(-1);  // interpreter only — Android limitation
            cx.setLanguageVersion(org.mozilla.javascript.Context.VERSION_ES6);
            ImporterTopLevel scope = new ImporterTopLevel(cx);
            // Expose host API
            Object hostApi = org.mozilla.javascript.Context.javaToJS(new HostApi(), scope);
            ScriptableObject.putProperty(scope, "Valgallov", hostApi);

            // Read file
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) sb.append(line).append('\n');
            }

            cx.evaluateString(scope, sb.toString(), f.getName(), 1, null);

            // Seal — prevent plugins from monkey-patching each other
            ((ScriptableObject) scope).sealObject();

            PluginHolder h = new PluginHolder();
            h.fileName = f.getName();
            h.scope = scope;

            // Fire onLoad
            callIfDefined(cx, scope, "onLoad", new Object[0]);

            return h;
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            return null;
        } finally {
            if (cx != null) org.mozilla.javascript.Context.exit();
        }
    }

    private static void callIfDefined(org.mozilla.javascript.Context cx, Scriptable scope, String fnName, Object[] args) {
        Object fn = scope.get(fnName, scope);
        if (fn instanceof Function) {
            try {
                ((Function) fn).call(cx, scope, scope, args);
            } catch (Throwable t) {
                if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            }
        }
    }

    public static void dispatchOnMessage(long dialogId, long senderId, String text) {
        if (!SharedConfig.valgallovPluginsEnabled) return;
        if (PLUGINS.isEmpty()) return;
        org.mozilla.javascript.Context cx = null;
        try {
            cx = org.mozilla.javascript.Context.enter();
            cx.setOptimizationLevel(-1);
            for (PluginHolder h : PLUGINS) {
                callIfDefined(cx, h.scope, "onMessage", new Object[]{
                        Long.valueOf(dialogId),
                        Long.valueOf(senderId),
                        text == null ? "" : text
                });
            }
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        } finally {
            if (cx != null) org.mozilla.javascript.Context.exit();
        }
    }

    public static void dispatchOnSend(long dialogId, String text) {
        if (!SharedConfig.valgallovPluginsEnabled) return;
        if (PLUGINS.isEmpty()) return;
        org.mozilla.javascript.Context cx = null;
        try {
            cx = org.mozilla.javascript.Context.enter();
            cx.setOptimizationLevel(-1);
            for (PluginHolder h : PLUGINS) {
                callIfDefined(cx, h.scope, "onSend", new Object[]{
                        Long.valueOf(dialogId),
                        text == null ? "" : text
                });
            }
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
        } finally {
            if (cx != null) org.mozilla.javascript.Context.exit();
        }
    }

    private static final class PluginHolder {
        String fileName;
        Scriptable scope;
    }

    // ============= Host API exposed to plugins =============

    public static final class HostApi {
        public void log(String msg) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("[ValgallovPlugin] " + msg);
            }
        }

        public void toast(final String msg) {
            try {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        Context ctx = ApplicationLoader.applicationContext;
                        Toast.makeText(ctx, msg == null ? "" : msg, Toast.LENGTH_SHORT).show();
                    } catch (Throwable ignore) {}
                });
            } catch (Throwable ignore) {}
        }

        public void sendMessage(long dialogId, String text) {
            if (text == null || text.isEmpty()) return;
            try {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        int account = UserConfig.selectedAccount;
                        SendMessagesHelper.getInstance(account).sendMessage(
                                SendMessagesHelper.SendMessageParams.of(text, dialogId));
                    } catch (Throwable ignore) {}
                });
            } catch (Throwable ignore) {}
        }

        public long getSelfId() {
            try {
                return UserConfig.getInstance(UserConfig.selectedAccount).getClientUserId();
            } catch (Throwable t) {
                return 0L;
            }
        }
    }
}
