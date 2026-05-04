package org.telegram.messenger;

import android.content.SharedPreferences;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Stores the set of dialog IDs marked as "Тайные Чертоги" — hidden chats
 * that should be filtered out of the main dialogs list.
 *
 * Access to view those chats is gated by a biometric / PIN prompt
 * shown by ValgallovHiddenChatsActivity.
 *
 * Format: SharedPreferences key "hidden_ids" stores a comma-separated
 * string of dialog IDs.
 */
public final class ValgallovHiddenChatsTracker {

    private static final String PREFS = "valgallov_hidden_v1";
    private static final String KEY = "hidden_ids";

    private static volatile HashSet<Long> sCache = null;

    private ValgallovHiddenChatsTracker() {}

    private static SharedPreferences getPrefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }

    private static synchronized HashSet<Long> ensureLoaded() {
        if (sCache != null) return sCache;
        HashSet<Long> set = new HashSet<>();
        try {
            String raw = getPrefs().getString(KEY, "");
            if (raw != null && !raw.isEmpty()) {
                for (String tok : raw.split(",")) {
                    if (tok.isEmpty()) continue;
                    try {
                        set.add(Long.parseLong(tok));
                    } catch (NumberFormatException ignore) {
                    }
                }
            }
        } catch (Throwable ignore) {
        }
        sCache = set;
        return set;
    }

    private static synchronized void persist() {
        if (sCache == null) return;
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Long id : sCache) {
            if (!first) sb.append(',');
            sb.append(id);
            first = false;
        }
        getPrefs().edit().putString(KEY, sb.toString()).apply();
    }

    public static synchronized boolean isHidden(long dialogId) {
        if (!SharedConfig.valgallovHiddenChatsEnabled) return false;
        return ensureLoaded().contains(dialogId);
    }

    public static synchronized void hide(long dialogId) {
        ensureLoaded().add(dialogId);
        persist();
    }

    public static synchronized void unhide(long dialogId) {
        ensureLoaded().remove(dialogId);
        persist();
    }

    public static synchronized Set<Long> getAll() {
        return Collections.unmodifiableSet(new HashSet<>(ensureLoaded()));
    }

    public static synchronized int count() {
        return ensureLoaded().size();
    }

    /**
     * When the user has unlocked the secret folder via biometric,
     * we set this volatile flag so the dialogs list temporarily
     * shows hidden chats. Auto-cleared after timeout.
     */
    public static volatile boolean unlocked = false;
    private static volatile long unlockedSince = 0;
    private static final long UNLOCK_TIMEOUT_MS = 60_000L;

    public static void markUnlocked() {
        unlocked = true;
        unlockedSince = System.currentTimeMillis();
    }

    public static void lock() {
        unlocked = false;
        unlockedSince = 0;
    }

    public static boolean isUnlockedNow() {
        if (!unlocked) return false;
        if (System.currentTimeMillis() - unlockedSince > UNLOCK_TIMEOUT_MS) {
            unlocked = false;
            return false;
        }
        return true;
    }
}
