package org.telegram.messenger;

import android.content.SharedPreferences;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Lightweight in-memory tracker of message IDs that the server has asked us to
 * delete locally. Persists to a single SharedPreferences string per dialog
 * (CSV of message ids, capped at MAX_PER_DIALOG entries) so the keyspace stays
 * tiny no matter how many deletions accumulate.
 */
public class ValgallovDeletedTracker {

    /** Stored content of a deleted message. */
    public static final class DeletedMessage {
        public final long dialogId;
        public final int msgId;
        public final String text;
        public final int date;
        public final boolean outgoing;
        public final String senderName;
        public final long senderId;
        public final String mediaType; // null, "sticker", "photo", "video", "voice", "video_note", "gif", "document"

        public DeletedMessage(long dialogId, int msgId, String text, int date, boolean outgoing,
                             String senderName, long senderId, String mediaType) {
            this.dialogId = dialogId;
            this.msgId = msgId;
            this.text = text;
            this.date = date;
            this.outgoing = outgoing;
            this.senderName = senderName;
            this.senderId = senderId;
            this.mediaType = mediaType;
        }
    }

    private static final String PREFS_NAME = "valgallov_deleted_v2";
    private static final String CONTENT_PREFS = "valgallov_deleted_content_v2";
    private static final int MAX_PER_DIALOG = 5000;

    /** Right-side suffix for deleted messages (Norse rune Hagalaz). */
    private static final String SUFFIX = "  ᚺ";
    /** Red color for the deletion rune marker. */
    private static final int MARKER_COLOR = 0xFFD44040;
    /** Tone for the message body when deleted (faded grey-blue, italic). */
    private static final int BODY_COLOR = 0xFF8995A6;

    /** dialogId -> set of deleted msgIds (LRU-ish via LinkedHashSet). */
    private static final java.util.HashMap<Long, LinkedHashSet<Integer>> sCache = new java.util.HashMap<>();
    /** Flat fallback for non-channel deletions whose dialogId we never learn. */
    private static final LinkedHashSet<Integer> sGlobalCache = new LinkedHashSet<>();
    private static volatile boolean sLoaded = false;

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS_NAME, 0);
    }

    private static synchronized void ensureLoaded() {
        if (sLoaded) return;
        SharedPreferences prefs = getPrefs();
        for (java.util.Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            String key = e.getKey();
            Object val = e.getValue();
            if (!(val instanceof String)) continue;
            String csv = (String) val;
            if (csv.isEmpty()) continue;
            try {
                LinkedHashSet<Integer> ids = parseCsv(csv);
                if (key.equals("global")) {
                    sGlobalCache.addAll(ids);
                } else if (key.startsWith("d:")) {
                    long dialogId = Long.parseLong(key.substring(2));
                    sCache.put(dialogId, ids);
                }
            } catch (Throwable ignore) {
            }
        }
        sLoaded = true;
    }

    private static LinkedHashSet<Integer> parseCsv(String csv) {
        LinkedHashSet<Integer> out = new LinkedHashSet<>();
        for (String s : csv.split(",")) {
            if (s.isEmpty()) continue;
            try {
                out.add(Integer.parseInt(s.trim()));
            } catch (NumberFormatException ignore) {
            }
        }
        return out;
    }

    private static String toCsv(LinkedHashSet<Integer> set) {
        StringBuilder sb = new StringBuilder(set.size() * 8);
        boolean first = true;
        for (Integer i : set) {
            if (!first) sb.append(',');
            sb.append(i);
            first = false;
        }
        return sb.toString();
    }

    public static synchronized void markDeleted(long dialogId, int msgId) {
        if (msgId == 0) return;
        ensureLoaded();
        addInternal(dialogId, msgId);
        flushDialog(dialogId);
    }

    public static synchronized void markDeleted(long dialogId, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        ensureLoaded();
        for (Integer id : ids) {
            if (id == null) continue;
            addInternal(dialogId, id);
        }
        flushDialog(dialogId);
    }

    private static void addInternal(long dialogId, int msgId) {
        if (dialogId == 0) {
            sGlobalCache.add(msgId);
            trim(sGlobalCache);
        } else {
            LinkedHashSet<Integer> set = sCache.get(dialogId);
            if (set == null) {
                set = new LinkedHashSet<>();
                sCache.put(dialogId, set);
            }
            set.add(msgId);
            trim(set);
        }
    }

    private static void trim(LinkedHashSet<Integer> set) {
        while (set.size() > MAX_PER_DIALOG) {
            // remove oldest (insertion order)
            java.util.Iterator<Integer> it = set.iterator();
            if (it.hasNext()) {
                it.next();
                it.remove();
            } else {
                break;
            }
        }
    }

    private static void flushDialog(long dialogId) {
        SharedPreferences.Editor ed = getPrefs().edit();
        if (dialogId == 0) {
            ed.putString("global", toCsv(sGlobalCache));
        } else {
            LinkedHashSet<Integer> set = sCache.get(dialogId);
            ed.putString("d:" + dialogId, set == null ? "" : toCsv(set));
        }
        ed.apply();
    }

    public static boolean isDeleted(long dialogId, int msgId) {
        if (msgId == 0) return false;
        ensureLoaded();
        LinkedHashSet<Integer> set = sCache.get(dialogId);
        if (set != null && set.contains(msgId)) return true;
        return sGlobalCache.contains(msgId);
    }

    /** Append a subtle deletion suffix to an existing message text. */
    public static CharSequence markDeleted(CharSequence original) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int bodyStart = sb.length();
        if (original != null) {
            sb.append(original);
            sb.setSpan(new ForegroundColorSpan(BODY_COLOR), bodyStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.setSpan(new StyleSpan(android.graphics.Typeface.ITALIC), bodyStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        int markerStart = sb.length();
        sb.append(SUFFIX);
        sb.setSpan(new ForegroundColorSpan(MARKER_COLOR), markerStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), markerStart, sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return sb;
    }

    /** Get list of deleted message ids in a dialog (insertion order, oldest first).
     *  Also includes IDs from the global cache (updates without a dialogId). */
    public static synchronized ArrayList<Integer> getDeletedList(long dialogId) {
        ensureLoaded();
        ArrayList<Integer> out = new ArrayList<>();
        LinkedHashSet<Integer> set = sCache.get(dialogId);
        if (set != null) out.addAll(set);
        // Include global cache entries (TL_updateDeleteMessages has no dialogId)
        if (!sGlobalCache.isEmpty()) out.addAll(sGlobalCache);
        return out;
    }

    // --- Content storage for deleted messages ---

    private static final char FIELD_SEP = '\u001F';
    private static final char RECORD_SEP = '\u001E';

    private static SharedPreferences getContentPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(CONTENT_PREFS, 0);
    }

    /** Save the actual content of a deleted message. */
    public static void saveContent(long dialogId, int msgId, String text, int date, boolean outgoing) {
        saveContent(dialogId, msgId, text, date, outgoing, null, 0, null);
    }

    /** Save the actual content of a deleted message with sender info and media type. */
    public static void saveContent(long dialogId, int msgId, String text, int date, boolean outgoing,
                                   String senderName, long senderId, String mediaType) {
        try {
            String sanitized = (text != null ? text : "").replace(FIELD_SEP, ' ').replace(RECORD_SEP, ' ');
            String sName = (senderName != null ? senderName : "").replace(FIELD_SEP, ' ').replace(RECORD_SEP, ' ');
            String mType = mediaType != null ? mediaType : "";
            // Format: date|outgoing|senderId|senderName|mediaType|text
            String value = date + String.valueOf(FIELD_SEP)
                + (outgoing ? "1" : "0") + FIELD_SEP
                + senderId + FIELD_SEP
                + sName + FIELD_SEP
                + mType + FIELD_SEP
                + sanitized;
            getContentPrefs().edit().putString(dialogId + ":" + msgId, value).apply();
        } catch (Throwable ignore) {}
    }

    /** Get saved content of a specific deleted message. */
    public static DeletedMessage getContent(long dialogId, int msgId) {
        try {
            String val = getContentPrefs().getString(dialogId + ":" + msgId, null);
            if (val == null) {
                // Try global (dialogId=0)
                val = getContentPrefs().getString("0:" + msgId, null);
                if (val == null) return null;
                dialogId = 0;
            }
            return parseContentValue(dialogId, msgId, val);
        } catch (Throwable ignore) {
            return null;
        }
    }

    private static DeletedMessage parseContentValue(long dialogId, int msgId, String val) {
        String[] parts = val.split(String.valueOf(FIELD_SEP), 6);
        if (parts.length < 3) return null;
        int date = 0;
        try { date = Integer.parseInt(parts[0]); } catch (Throwable ignore) {}
        boolean out = "1".equals(parts[1]);
        // v2 format: date|out|senderId|senderName|mediaType|text
        if (parts.length >= 6) {
            long senderId = 0;
            try { senderId = Long.parseLong(parts[2]); } catch (Throwable ignore) {}
            String senderName = parts[3].isEmpty() ? null : parts[3];
            String mediaType = parts[4].isEmpty() ? null : parts[4];
            return new DeletedMessage(dialogId, msgId, parts[5], date, out, senderName, senderId, mediaType);
        }
        // v1 fallback: date|out|text
        return new DeletedMessage(dialogId, msgId, parts[2], date, out, null, 0, null);
    }

    /** Get all deleted messages with saved content for a dialog. Returns newest first. */
    public static ArrayList<DeletedMessage> getDeletedMessages(long dialogId) {
        ArrayList<DeletedMessage> out = new ArrayList<>();
        ArrayList<Integer> ids = getDeletedList(dialogId);
        for (Integer id : ids) {
            DeletedMessage dm = getContent(dialogId, id);
            if (dm != null) {
                out.add(dm);
            }
        }
        java.util.Collections.reverse(out);
        return out;
    }

    /** Get ALL deleted messages with saved content across ALL dialogs. Returns newest first. */
    public static ArrayList<DeletedMessage> getAllDeletedMessages() {
        ArrayList<DeletedMessage> out = new ArrayList<>();
        try {
            java.util.Map<String, ?> all = getContentPrefs().getAll();
            for (java.util.Map.Entry<String, ?> e : all.entrySet()) {
                String key = e.getKey();
                Object val = e.getValue();
                if (!(val instanceof String)) continue;
                try {
                    int colonIdx = key.indexOf(':');
                    if (colonIdx < 0) continue;
                    long did = Long.parseLong(key.substring(0, colonIdx));
                    int mid = Integer.parseInt(key.substring(colonIdx + 1));
                    DeletedMessage dm = parseContentValue(did, mid, (String) val);
                    if (dm != null) out.add(dm);
                } catch (Throwable ignore) {}
            }
        } catch (Throwable ignore) {}
        // Sort by date descending
        java.util.Collections.sort(out, (a, b) -> Integer.compare(b.date, a.date));
        return out;
    }
}
