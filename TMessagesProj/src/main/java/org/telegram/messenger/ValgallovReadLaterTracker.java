package org.telegram.messenger;

import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.Map;

/**
 * Local message bookmarks ("Прочитать позже").
 * Stores bookmarked messages in SharedPreferences.
 * Format: dialogId:msgId -> senderName|date|text
 */
public final class ValgallovReadLaterTracker {

    public static final class Bookmark {
        public final long dialogId;
        public final int msgId;
        public final String text;
        public final int date;
        public final String senderName;
        public final String chatName;

        public Bookmark(long dialogId, int msgId, String text, int date, String senderName, String chatName) {
            this.dialogId = dialogId;
            this.msgId = msgId;
            this.text = text;
            this.date = date;
            this.senderName = senderName;
            this.chatName = chatName;
        }
    }

    private static final String PREFS = "valgallov_readlater_v1";
    private static final char SEP = '\u001F';

    private ValgallovReadLaterTracker() {}

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    public static void save(long dialogId, int msgId, String text, int date, String senderName, String chatName) {
        if (text == null || text.isEmpty()) return;
        String sanitized = text.replace(SEP, ' ').replace('\n', '\u2028');
        String sName = senderName != null ? senderName.replace(SEP, ' ') : "";
        String cName = chatName != null ? chatName.replace(SEP, ' ') : "";
        String value = date + String.valueOf(SEP) + sName + SEP + cName + SEP + sanitized;
        getPrefs().edit().putString(dialogId + ":" + msgId, value).apply();
    }

    public static void remove(long dialogId, int msgId) {
        getPrefs().edit().remove(dialogId + ":" + msgId).apply();
    }

    public static boolean isBookmarked(long dialogId, int msgId) {
        return getPrefs().contains(dialogId + ":" + msgId);
    }

    public static ArrayList<Bookmark> getAll() {
        ArrayList<Bookmark> out = new ArrayList<>();
        try {
            Map<String, ?> all = getPrefs().getAll();
            for (Map.Entry<String, ?> e : all.entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();
                if (!(val instanceof String)) continue;
                try {
                    int colonIdx = key.indexOf(':');
                    if (colonIdx < 0) continue;
                    long did = Long.parseLong(key.substring(0, colonIdx));
                    int mid = Integer.parseInt(key.substring(colonIdx + 1));
                    Bookmark bm = parse(did, mid, (String) val);
                    if (bm != null) out.add(bm);
                } catch (Throwable ignore) {}
            }
        } catch (Throwable ignore) {}
        // Sort by date descending (newest first)
        java.util.Collections.sort(out, (a, b) -> Integer.compare(b.date, a.date));
        return out;
    }

    public static int count() {
        try {
            return getPrefs().getAll().size();
        } catch (Throwable e) {
            return 0;
        }
    }

    private static Bookmark parse(long dialogId, int msgId, String val) {
        String[] parts = val.split(String.valueOf(SEP), 4);
        if (parts.length < 4) return null;
        int date = 0;
        try { date = Integer.parseInt(parts[0]); } catch (Throwable ignore) {}
        String senderName = parts[1].isEmpty() ? null : parts[1];
        String chatName = parts[2].isEmpty() ? null : parts[2];
        String text = parts[3].replace('\u2028', '\n');
        return new Bookmark(dialogId, msgId, text, date, senderName, chatName);
    }
}
