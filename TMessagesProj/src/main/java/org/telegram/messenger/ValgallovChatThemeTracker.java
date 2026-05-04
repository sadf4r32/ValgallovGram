package org.telegram.messenger;

import android.content.SharedPreferences;

/**
 * Per-chat theme tracker. Stores dialogId -> preset index:
 *   0 — default (use global theme)
 *   1 — Чертог Стали
 *   2 — Чертог Огня
 *   3 — Чертог Льда
 *   4 — Чертог Тьмы
 */
public final class ValgallovChatThemeTracker {

    public static final int PRESET_DEFAULT = 0;
    public static final int PRESET_STEEL   = 1;
    public static final int PRESET_FIRE    = 2;
    public static final int PRESET_ICE     = 3;
    public static final int PRESET_DARK    = 4;

    public static final String[] NAMES_RU = {
        "По умолчанию",
        "Чертог Стали",
        "Чертог Огня",
        "Чертог Льда",
        "Чертог Тьмы"
    };

    // Tint color for each preset (applied as 45% overlay on chat background)
    private static final int[] TINT_COLORS = {
        0,
        0xFF15191F, // Steel
        0xFF2A140E, // Fire (deep burgundy)
        0xFF0D1A2A, // Ice (deep blue)
        0xFF060709  // Dark (near black)
    };

    // Accent color shown in previews
    private static final int[] ACCENT_COLORS = {
        0xFF7B8DA6, // default — pewter
        0xFF7B8DA6, // Steel — pewter blue
        0xFFC94A3A, // Fire — warm red
        0xFF5FA3D4, // Ice — bright blue
        0xFF3A3E4A  // Dark — cool grey
    };

    private static final String PREFS = "valgallov_chat_themes";

    private ValgallovChatThemeTracker() {}

    public static void setPreset(long dialogId, int preset) {
        if (preset < 0 || preset >= NAMES_RU.length) preset = PRESET_DEFAULT;
        SharedPreferences p = prefs();
        SharedPreferences.Editor e = p.edit();
        if (preset == PRESET_DEFAULT) {
            e.remove(String.valueOf(dialogId));
        } else {
            e.putInt(String.valueOf(dialogId), preset);
        }
        e.apply();
    }

    public static int getPreset(long dialogId) {
        return prefs().getInt(String.valueOf(dialogId), PRESET_DEFAULT);
    }

    public static int getTintColor(int preset) {
        if (preset < 0 || preset >= TINT_COLORS.length) return 0;
        return TINT_COLORS[preset];
    }

    public static int getAccentColor(int preset) {
        if (preset < 0 || preset >= ACCENT_COLORS.length) return ACCENT_COLORS[0];
        return ACCENT_COLORS[preset];
    }

    public static String getName(int preset) {
        if (preset < 0 || preset >= NAMES_RU.length) return NAMES_RU[0];
        return NAMES_RU[preset];
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
    }
}
