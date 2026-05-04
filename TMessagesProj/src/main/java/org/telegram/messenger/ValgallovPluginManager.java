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

    private static Object callAndReturn(org.mozilla.javascript.Context cx, Scriptable scope, String fnName, Object[] args) {
        Object fn = scope.get(fnName, scope);
        if (fn instanceof Function) {
            try {
                return ((Function) fn).call(cx, scope, scope, args);
            } catch (Throwable t) {
                if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            }
        }
        return null;
    }

    /** Sentinel string returned by a plugin's onSend to cancel the outgoing message entirely. */
    public static final String CANCEL = "__VG_CANCEL__";

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

    /**
     * Dispatch onSend across all loaded plugins. Returns:
     *   - null  → no plugin changed the text; leave as-is
     *   - ""/"__VG_CANCEL__" → cancel the send
     *   - other String → replaces the outgoing text
     *
     * The first plugin that returns a non-undefined, non-null value wins;
     * subsequent plugins see the already-modified value.
     */
    public static String dispatchOnSend(long dialogId, String text) {
        if (!SharedConfig.valgallovPluginsEnabled) return null;
        if (PLUGINS.isEmpty()) return null;
        org.mozilla.javascript.Context cx = null;
        try {
            cx = org.mozilla.javascript.Context.enter();
            cx.setOptimizationLevel(-1);
            String current = text == null ? "" : text;
            boolean mutated = false;
            for (PluginHolder h : PLUGINS) {
                Object r = callAndReturn(cx, h.scope, "onSend", new Object[]{
                        Long.valueOf(dialogId),
                        current
                });
                if (r == null) continue;
                if (r == org.mozilla.javascript.Undefined.instance) continue;
                if (r instanceof org.mozilla.javascript.UniqueTag) continue;
                String s = org.mozilla.javascript.Context.toString(r);
                if (s == null) continue;
                if (CANCEL.equals(s)) {
                    return CANCEL;
                }
                current = s;
                mutated = true;
            }
            return mutated ? current : null;
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            return null;
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

        public String getMyUsername() {
            try {
                org.telegram.tgnet.TLRPC.User u = UserConfig.getInstance(UserConfig.selectedAccount).getCurrentUser();
                if (u == null || u.username == null) return "";
                return u.username;
            } catch (Throwable t) {
                return "";
            }
        }

        public String getDialogTitle(long dialogId) {
            try {
                int account = UserConfig.selectedAccount;
                if (dialogId > 0) {
                    org.telegram.tgnet.TLRPC.User u = MessagesController.getInstance(account).getUser(dialogId);
                    if (u != null) {
                        String name = "";
                        if (u.first_name != null) name += u.first_name;
                        if (u.last_name != null) name += (name.isEmpty() ? "" : " ") + u.last_name;
                        return name;
                    }
                } else {
                    org.telegram.tgnet.TLRPC.Chat c = MessagesController.getInstance(account).getChat(-dialogId);
                    if (c != null && c.title != null) return c.title;
                }
            } catch (Throwable ignore) {}
            return "";
        }

        public void deleteMessage(long dialogId, int messageId) {
            try {
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        int account = UserConfig.selectedAccount;
                        java.util.ArrayList<Integer> ids = new java.util.ArrayList<>();
                        ids.add(messageId);
                        long channelId = 0;
                        if (dialogId < 0) {
                            org.telegram.tgnet.TLRPC.Chat chat = MessagesController.getInstance(account).getChat(-dialogId);
                            if (chat != null && ChatObject.isChannel(chat)) channelId = chat.id;
                        }
                        MessagesController.getInstance(account).deleteMessages(ids, null, null, dialogId, 0, true, 0);
                    } catch (Throwable ignore) {}
                });
            } catch (Throwable ignore) {}
        }

        // ----- formatted send helpers -----

        private void sendFormatted(long dialogId, String text, String entityType) {
            if (text == null || text.isEmpty()) return;
            AndroidUtilities.runOnUIThread(() -> {
                try {
                    int account = UserConfig.selectedAccount;
                    java.util.ArrayList<org.telegram.tgnet.TLRPC.MessageEntity> entities = new java.util.ArrayList<>();
                    org.telegram.tgnet.TLRPC.MessageEntity ent = buildEntity(entityType);
                    if (ent != null) {
                        ent.offset = 0;
                        ent.length = text.length();
                        entities.add(ent);
                    }
                    SendMessagesHelper.getInstance(account).sendMessage(
                        SendMessagesHelper.SendMessageParams.of(
                            text, dialogId, null, null, null, true, entities, null, null, true, 0, 0, null, false));
                } catch (Throwable ignore) {}
            });
        }

        private org.telegram.tgnet.TLRPC.MessageEntity buildEntity(String type) {
            if ("spoiler".equals(type)) return new org.telegram.tgnet.TLRPC.TL_messageEntitySpoiler();
            if ("bold".equals(type))    return new org.telegram.tgnet.TLRPC.TL_messageEntityBold();
            if ("italic".equals(type))  return new org.telegram.tgnet.TLRPC.TL_messageEntityItalic();
            if ("code".equals(type))    return new org.telegram.tgnet.TLRPC.TL_messageEntityCode();
            if ("pre".equals(type))     return new org.telegram.tgnet.TLRPC.TL_messageEntityPre();
            if ("strike".equals(type))  return new org.telegram.tgnet.TLRPC.TL_messageEntityStrike();
            if ("underline".equals(type)) return new org.telegram.tgnet.TLRPC.TL_messageEntityUnderline();
            return null;
        }

        public void sendSpoiler (long dialogId, String text) { sendFormatted(dialogId, text, "spoiler"); }
        public void sendBold    (long dialogId, String text) { sendFormatted(dialogId, text, "bold"); }
        public void sendItalic  (long dialogId, String text) { sendFormatted(dialogId, text, "italic"); }
        public void sendCode    (long dialogId, String text) { sendFormatted(dialogId, text, "code"); }
        public void sendMono    (long dialogId, String text) { sendFormatted(dialogId, text, "pre"); }
        public void sendStrike  (long dialogId, String text) { sendFormatted(dialogId, text, "strike"); }
        public void sendUnderline(long dialogId, String text){ sendFormatted(dialogId, text, "underline"); }

        // expose cancel sentinel as a readable field in JS (Valgallov.CANCEL)
        public String getCANCEL() { return ValgallovPluginManager.CANCEL; }

        // ----- persistent per-plugin storage (shared across restarts) -----

        public PluginStorage getStorage() {
            return STORAGE;
        }
    }

    private static final PluginStorage STORAGE = new PluginStorage();

    public static final class PluginStorage {
        private android.content.SharedPreferences prefs() {
            return ApplicationLoader.applicationContext.getSharedPreferences("valgallov_plugin_storage", Context.MODE_PRIVATE);
        }

        public String get(String key) {
            try { return prefs().getString(key, null); } catch (Throwable t) { return null; }
        }

        public String getOrDefault(String key, String def) {
            try { return prefs().getString(key, def); } catch (Throwable t) { return def; }
        }

        public void set(String key, String value) {
            try { prefs().edit().putString(key, value).apply(); } catch (Throwable ignore) {}
        }

        public void remove(String key) {
            try { prefs().edit().remove(key).apply(); } catch (Throwable ignore) {}
        }

        public void clear() {
            try { prefs().edit().clear().apply(); } catch (Throwable ignore) {}
        }
    }
}
