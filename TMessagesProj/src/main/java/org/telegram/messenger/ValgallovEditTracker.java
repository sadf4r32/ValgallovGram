package org.telegram.messenger;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Tracks previous versions of edited messages.
 *
 * Storage: per-dialog SharedPreferences entry with format
 *   {msgId} | {timestamp} | {text}
 * separated by US-control char (\u001F) for fields, RS (\u001E) for records.
 *
 * Capped at MAX_PER_DIALOG records per dialog (oldest evicted).
 */
public final class ValgallovEditTracker {

    public static final class Version {
        public final int msgId;
        public final long timestamp;
        public final String text;

        public Version(int msgId, long timestamp, String text) {
            this.msgId = msgId;
            this.timestamp = timestamp;
            this.text = text;
        }
    }

    private static final String PREFS = "valgallov_edits_v1";
    private static final int MAX_PER_DIALOG = 1000;
    private static final char FIELD_SEP = '\u001F';
    private static final char RECORD_SEP = '\u001E';

    /** dialogId -> ordered list (oldest first) of versions for that dialog. */
    private static final HashMap<Long, ArrayList<Version>> sCache = new HashMap<>();
    private static volatile boolean sLoaded = false;

    private ValgallovEditTracker() {}

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    private static synchronized void ensureLoaded() {
        if (sLoaded) return;
        SharedPreferences prefs = getPrefs();
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            if (!(val instanceof String)) continue;
            String body = (String) val;
            if (body.isEmpty()) continue;
            try {
                if (!key.startsWith("d:")) continue;
                long dialogId = Long.parseLong(key.substring(2));
                ArrayList<Version> list = parse(body);
                sCache.put(dialogId, list);
            } catch (Throwable ignore) {
            }
        }
        sLoaded = true;
    }

    private static ArrayList<Version> parse(String body) {
        ArrayList<Version> out = new ArrayList<>();
        for (String rec : body.split(String.valueOf(RECORD_SEP))) {
            if (rec.isEmpty()) continue;
            String[] parts = rec.split(String.valueOf(FIELD_SEP), 3);
            if (parts.length < 3) continue;
            try {
                int id = Integer.parseInt(parts[0]);
                long ts = Long.parseLong(parts[1]);
                out.add(new Version(id, ts, parts[2]));
            } catch (NumberFormatException ignore) {
            }
        }
        return out;
    }

    private static String serialize(ArrayList<Version> list) {
        StringBuilder sb = new StringBuilder(list.size() * 32);
        for (int i = 0; i < list.size(); i++) {
            Version v = list.get(i);
            if (i > 0) sb.append(RECORD_SEP);
            sb.append(v.msgId).append(FIELD_SEP).append(v.timestamp).append(FIELD_SEP)
              .append(v.text == null ? "" : v.text.replace(RECORD_SEP, ' ').replace(FIELD_SEP, ' '));
        }
        return sb.toString();
    }

    public static synchronized void recordPreviousVersion(long dialogId, int msgId, String text) {
        if (msgId == 0) return;
        if (text == null || text.isEmpty()) return;
        if (!SharedConfig.valgallovEditHistoryEnabled) return;
        ensureLoaded();
        ArrayList<Version> list = sCache.get(dialogId);
        if (list == null) {
            list = new ArrayList<>();
            sCache.put(dialogId, list);
        }
        // De-dup: if last version for this msgId is same text, skip
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).msgId == msgId) {
                if (text.equals(list.get(i).text)) return;
                break;
            }
        }
        list.add(new Version(msgId, System.currentTimeMillis(), text));
        while (list.size() > MAX_PER_DIALOG) {
            list.remove(0);
        }
        getPrefs().edit().putString("d:" + dialogId, serialize(list)).apply();
    }

    /** Return all versions stored for a specific message id, oldest first. */
    public static synchronized ArrayList<Version> getVersionsForMessage(long dialogId, int msgId) {
        ensureLoaded();
        ArrayList<Version> list = sCache.get(dialogId);
        ArrayList<Version> out = new ArrayList<>();
        if (list == null) return out;
        for (Version v : list) {
            if (v.msgId == msgId) out.add(v);
        }
        return out;
    }

    public static synchronized boolean hasVersions(long dialogId, int msgId) {
        ensureLoaded();
        ArrayList<Version> list = sCache.get(dialogId);
        if (list == null) return false;
        for (Version v : list) {
            if (v.msgId == msgId) return true;
        }
        return false;
    }
}
