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

    /** Returns metadata for every .js file discovered in the plugins directory,
     *  whether or not it is currently loaded/enabled. Plugins appear even when
     *  globally disabled — the UI uses this to populate the manager. */
    public static List<PluginInfo> listAll() {
        List<PluginInfo> out = new ArrayList<>();
        File dir = pluginDir();
        if (dir == null || !dir.isDirectory()) return out;
        File[] files = dir.listFiles();
        if (files == null) return out;
        java.util.Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File f : files) {
            if (!f.isFile()) continue;
            if (!f.getName().endsWith(".js")) continue;
            PluginInfo info = parseMetadata(f);
            info.enabled = isEnabled(info.fileName);
            info.loaded = isLoaded(info.fileName);
            out.add(info);
        }
        return out;
    }

    public static boolean isLoaded(String fileName) {
        for (PluginHolder h : PLUGINS) if (h.fileName.equals(fileName)) return true;
        return false;
    }

    public static boolean isEnabled(String fileName) {
        try {
            return ApplicationLoader.applicationContext
                .getSharedPreferences("valgallov_plugins", Context.MODE_PRIVATE)
                .getBoolean("enabled_" + fileName, true);
        } catch (Throwable t) {
            return true;
        }
    }

    public static void setEnabled(String fileName, boolean enabled) {
        try {
            ApplicationLoader.applicationContext
                .getSharedPreferences("valgallov_plugins", Context.MODE_PRIVATE)
                .edit().putBoolean("enabled_" + fileName, enabled).apply();
        } catch (Throwable ignore) {}
    }

    /** Names of .js files bundled inside the APK under
     *  assets/valgallov_builtin_plugins/. These can be "installed" with one tap
     *  without the user having to download anything. */
    public static List<PluginInfo> listBuiltins() {
        List<PluginInfo> out = new ArrayList<>();
        try {
            android.content.res.AssetManager am = ApplicationLoader.applicationContext.getAssets();
            String[] names = am.list("valgallov_builtin_plugins");
            if (names == null) return out;
            java.util.Arrays.sort(names);
            for (String n : names) {
                if (!n.endsWith(".js")) continue;
                PluginInfo info = parseMetadataFromAsset(am, "valgallov_builtin_plugins/" + n);
                info.fileName = n;
                if (info.name == null || info.name.isEmpty()) info.name = n;
                info.loaded = isLoaded(n);
                info.enabled = isEnabled(n);
                // Mark builtins so UI can distinguish them
                info.description = (info.description == null ? "" : info.description + "  ·  ") + "встроенный";
                // reuse enabled flag — if file already installed, UI will show same row
                out.add(info);
            }
        } catch (Throwable ignore) {}
        return out;
    }

    private static PluginInfo parseMetadataFromAsset(android.content.res.AssetManager am, String path) {
        PluginInfo info = new PluginInfo();
        try (BufferedReader br = new BufferedReader(new java.io.InputStreamReader(am.open(path)))) {
            String line;
            int scanned = 0;
            while ((line = br.readLine()) != null && scanned < 40) {
                scanned++;
                String t = line.trim();
                if (!t.startsWith("//")) {
                    if (scanned > 5 && !t.isEmpty()) break;
                    continue;
                }
                t = t.substring(2).trim();
                int at = t.indexOf('@');
                if (at < 0) continue;
                String rest = t.substring(at + 1).trim();
                int sp = rest.indexOf(' ');
                if (sp < 0) sp = rest.indexOf('\t');
                if (sp < 0) continue;
                String key = rest.substring(0, sp).toLowerCase();
                String val = rest.substring(sp + 1).trim();
                if ("name".equals(key)) info.name = val;
                else if ("author".equals(key)) info.author = val;
                else if ("version".equals(key)) info.version = val;
                else if ("description".equals(key)) info.description = val;
            }
        } catch (Throwable ignore) {}
        return info;
    }

    /** Copies a bundled plugin out of assets into the plugin dir so it gets loaded. */
    public static boolean installBuiltin(String fileName) {
        try {
            android.content.res.AssetManager am = ApplicationLoader.applicationContext.getAssets();
            File out = new File(pluginDir(), fileName);
            try (java.io.InputStream in = am.open("valgallov_builtin_plugins/" + fileName);
                 java.io.FileOutputStream fo = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
            }
            setEnabled(fileName, true);
            return true;
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            return false;
        }
    }

    /** Copies an arbitrary input stream into the plugin dir under the given name.
     *  Used by the "tap a .js file → install" intent handler. */
    public static boolean installFromStream(String fileName, java.io.InputStream in) {
        if (fileName == null || in == null) return false;
        if (!fileName.endsWith(".js")) fileName = fileName + ".js";
        try {
            File out = new File(pluginDir(), fileName);
            try (java.io.FileOutputStream fo = new java.io.FileOutputStream(out)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) fo.write(buf, 0, n);
            }
            setEnabled(fileName, true);
            return true;
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) FileLog.e(t);
            return false;
        }
    }

    public static boolean isInstalled(String fileName) {
        return new File(pluginDir(), fileName).isFile();
    }

    public static File pluginDirPublic() { return pluginDir(); }

    public static boolean delete(String fileName) {
        File f = new File(pluginDir(), fileName);
        boolean ok = false;
        try { ok = f.delete(); } catch (Throwable ignore) {}
        if (ok) {
            try {
                ApplicationLoader.applicationContext
                    .getSharedPreferences("valgallov_plugins", Context.MODE_PRIVATE)
                    .edit().remove("enabled_" + fileName).apply();
            } catch (Throwable ignore) {}
        }
        return ok;
    }

    /** Parse UserScript-style metadata from a plugin file header, e.g.:
     *    // @name    My plugin
     *    // @author  sadf4r32
     *    // @version 1.0
     *    // @description Short line
     */
    private static PluginInfo parseMetadata(File f) {
        PluginInfo info = new PluginInfo();
        info.fileName = f.getName();
        info.sizeBytes = f.length();
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            int scanned = 0;
            while ((line = br.readLine()) != null && scanned < 40) {
                scanned++;
                String t = line.trim();
                if (!t.startsWith("//")) {
                    // stop scanning once we fall out of the leading comment block,
                    // but only after at least a few lines so blank lines are tolerated.
                    if (scanned > 5 && !t.isEmpty()) break;
                    continue;
                }
                t = t.substring(2).trim();
                int at = t.indexOf('@');
                if (at < 0) continue;
                String rest = t.substring(at + 1).trim();
                int sp = rest.indexOf(' ');
                if (sp < 0) sp = rest.indexOf('\t');
                if (sp < 0) continue;
                String key = rest.substring(0, sp).toLowerCase();
                String val = rest.substring(sp + 1).trim();
                if ("name".equals(key)) info.name = val;
                else if ("author".equals(key)) info.author = val;
                else if ("version".equals(key)) info.version = val;
                else if ("description".equals(key)) info.description = val;
            }
        } catch (Throwable ignore) {}
        if (info.name == null || info.name.isEmpty()) info.name = info.fileName;
        return info;
    }

    public static final class PluginInfo {
        public String fileName;
        public String name;
        public String author;
        public String version;
        public String description;
        public long sizeBytes;
        public boolean enabled;
        public boolean loaded;
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
            if (!isEnabled(name)) continue;   // per-plugin disable
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

        // ----- Unicode text transformers (Valgallov.text.*) -----
        public TextHelpers getText() {
            return TEXT_HELPERS;
        }
    }

    private static final TextHelpers TEXT_HELPERS = new TextHelpers();

    /**
     * Pure Unicode-remapping helpers — no entities, useful for plugins that
     * want to transform text in-place via onSend's return value.
     */
    public static final class TextHelpers {
        // Mathematical Alphanumeric Symbols block. Not every font renders them,
        // but Telegram Android does. Works for ASCII a-z, A-Z, 0-9 only.
        public String bold(String s)   { return mapAZaz09(s, 0x1D400, 0x1D41A, 0x1D7CE); }
        public String italic(String s) { return mapAZaz09(s, 0x1D434, 0x1D44E, -1); }
        public String mono(String s)   { return mapAZaz09(s, 0x1D670, 0x1D68A, 0x1D7F6); }
        public String serif(String s)  { return s; /* the input is already serif-ish */ }

        public String strike(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length() * 2);
            for (int i = 0; i < s.length(); i++) { sb.append(s.charAt(i)); sb.append('\u0336'); }
            return sb.toString();
        }

        public String underline(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length() * 2);
            for (int i = 0; i < s.length(); i++) { sb.append(s.charAt(i)); sb.append('\u0332'); }
            return sb.toString();
        }

        public String caps(String s) {
            return s == null ? "" : s.toUpperCase();
        }

        public String alt(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                sb.append((i & 1) == 0 ? Character.toLowerCase(c) : Character.toUpperCase(c));
            }
            return sb.toString();
        }

        public String mock(String s) {
            if (s == null) return "";
            java.util.Random r = new java.util.Random();
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                sb.append(r.nextBoolean() ? Character.toLowerCase(c) : Character.toUpperCase(c));
            }
            return sb.toString();
        }

        public String reverse(String s) {
            if (s == null) return "";
            return new StringBuilder(s).reverse().toString();
        }

        public String space(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length() * 2);
            for (int i = 0; i < s.length(); i++) {
                if (i > 0) sb.append(' ');
                sb.append(s.charAt(i));
            }
            return sb.toString();
        }

        // Cyrillic → Latin transliteration (ГОСТ-ish)
        public String translit(String s) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length());
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                String m = translitChar(c);
                sb.append(m != null ? m : String.valueOf(c));
            }
            return sb.toString();
        }

        private static String mapAZaz09(String s, int baseUpper, int baseLower, int baseDigit) {
            if (s == null) return "";
            StringBuilder sb = new StringBuilder(s.length() * 2);
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                if (c >= 'A' && c <= 'Z') sb.appendCodePoint(baseUpper + (c - 'A'));
                else if (c >= 'a' && c <= 'z') sb.appendCodePoint(baseLower + (c - 'a'));
                else if (baseDigit > 0 && c >= '0' && c <= '9') sb.appendCodePoint(baseDigit + (c - '0'));
                else sb.append(c);
            }
            return sb.toString();
        }

        private static String translitChar(char c) {
            switch (c) {
                case 'а': return "a"; case 'б': return "b"; case 'в': return "v";
                case 'г': return "g"; case 'д': return "d"; case 'е': return "e";
                case 'ё': return "yo"; case 'ж': return "zh"; case 'з': return "z";
                case 'и': return "i"; case 'й': return "y"; case 'к': return "k";
                case 'л': return "l"; case 'м': return "m"; case 'н': return "n";
                case 'о': return "o"; case 'п': return "p"; case 'р': return "r";
                case 'с': return "s"; case 'т': return "t"; case 'у': return "u";
                case 'ф': return "f"; case 'х': return "h"; case 'ц': return "ts";
                case 'ч': return "ch"; case 'ш': return "sh"; case 'щ': return "sch";
                case 'ъ': return ""; case 'ы': return "y"; case 'ь': return "";
                case 'э': return "e"; case 'ю': return "yu"; case 'я': return "ya";
                case 'А': return "A"; case 'Б': return "B"; case 'В': return "V";
                case 'Г': return "G"; case 'Д': return "D"; case 'Е': return "E";
                case 'Ё': return "Yo"; case 'Ж': return "Zh"; case 'З': return "Z";
                case 'И': return "I"; case 'Й': return "Y"; case 'К': return "K";
                case 'Л': return "L"; case 'М': return "M"; case 'Н': return "N";
                case 'О': return "O"; case 'П': return "P"; case 'Р': return "R";
                case 'С': return "S"; case 'Т': return "T"; case 'У': return "U";
                case 'Ф': return "F"; case 'Х': return "H"; case 'Ц': return "Ts";
                case 'Ч': return "Ch"; case 'Ш': return "Sh"; case 'Щ': return "Sch";
                case 'Ъ': return ""; case 'Ы': return "Y"; case 'Ь': return "";
                case 'Э': return "E"; case 'Ю': return "Yu"; case 'Я': return "Ya";
                default: return null;
            }
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
