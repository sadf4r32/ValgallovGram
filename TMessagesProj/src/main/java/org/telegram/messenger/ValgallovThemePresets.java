package org.telegram.messenger;

import android.content.Context;

import org.telegram.ui.ActionBar.Theme;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.HashMap;

/**
 * Four Norse theme presets for ValgallovGram:
 *   0 — Чертог Стали  (default — the existing valgallov.attheme)
 *   1 — Чертог Огня   (warm red-orange)
 *   2 — Чертог Льда   (blue + white)
 *   3 — Чертог Тьмы   (almost-black)
 */
public final class ValgallovThemePresets {

    public static final int PRESET_STEEL = 0;
    public static final int PRESET_FIRE  = 1;
    public static final int PRESET_ICE   = 2;
    public static final int PRESET_DARK  = 3;

    public static final String[] NAMES_RU = {
        "Чертог Стали",
        "Чертог Огня",
        "Чертог Льда",
        "Чертог Тьмы"
    };

    private static final String BASE_ASSET = "valgallov.attheme";

    private ValgallovThemePresets() {}

    public static String presetName(int preset) {
        if (preset >= 0 && preset < NAMES_RU.length) return NAMES_RU[preset];
        return NAMES_RU[0];
    }

    /**
     * Apply the given preset. Writes the generated .attheme to filesDir and
     * calls Theme.applyThemeFile().
     */
    public static void apply(int preset) {
        try {
            Context ctx = ApplicationLoader.applicationContext;
            String content;
            if (preset == PRESET_STEEL) {
                // Just copy the base asset as-is
                content = readAsset(ctx, BASE_ASSET);
            } else {
                String base = readAsset(ctx, BASE_ASSET);
                content = transformTheme(base, preset);
            }
            String fileName = "valgallov_preset_" + preset + ".attheme";
            File outFile = new File(ctx.getFilesDir(), fileName);
            try (OutputStream out = new FileOutputStream(outFile)) {
                out.write(content.getBytes("UTF-8"));
            }
            Theme.applyThemeFile(outFile, "Valgallov " + presetName(preset), null, false);
            SharedConfig.setValgallovThemePreset(preset);
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e(t);
            }
        }
    }

    private static String readAsset(Context ctx, String name) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (InputStream in = ctx.getAssets().open(name);
             BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    /**
     * Transform base Steel theme into Fire / Ice / Dark by remapping
     * key background and accent colors.
     */
    private static String transformTheme(String base, int preset) {
        // Parse all key=value pairs
        HashMap<String, String> map = new HashMap<>();
        String[] lines = base.split("\n");
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                map.put(line.substring(0, eq).trim(), line.substring(eq + 1).trim());
            }
        }

        // Color maps per preset (int color values)
        // Steel palette reference:
        //   bg #15191F = -15064033, windowBackgroundGray #0B0E14 = -15659500 approx
        //   titles #E5DBC4 = -1709116 approx, accent #C9A86A = -3561366 approx
        //   button gradient #5C6F8A→#7B8DA6

        switch (preset) {
            case PRESET_FIRE:
                applyFirePalette(map);
                break;
            case PRESET_ICE:
                applyIcePalette(map);
                break;
            case PRESET_DARK:
                applyDarkPalette(map);
                break;
        }

        // Rebuild theme string
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            int eq = line.indexOf('=');
            if (eq > 0) {
                String key = line.substring(0, eq).trim();
                String val = map.get(key);
                if (val != null) {
                    sb.append(key).append('=').append(val).append('\n');
                } else {
                    sb.append(line).append('\n');
                }
            } else {
                sb.append(line).append('\n');
            }
        }
        return sb.toString();
    }

    // ---- Чертог Огня (Fire) — warm red-orange tones ----
    private static void applyFirePalette(HashMap<String, String> m) {
        // Main backgrounds: deep burgundy-black
        putColor(m, "windowBackgroundGray", 0xFF1A0E0E);
        putColor(m, "windowBackgroundWhite", 0xFF1F1210);
        putColor(m, "windowBackgroundChecked", 0xFF2A1412);
        putColor(m, "actionBarDefault", 0xFF241410);
        putColor(m, "actionBarDefaultArchived", 0xFF1A0E0E);
        putColor(m, "chats_menuBackground", 0xFF1C0F0D);
        putColor(m, "dialogBackground", 0xFF241612);
        putColor(m, "inappPlayerBackground", 0xFF241612);
        putColor(m, "chat_emojiPanelBackground", 0xFF201210);

        // Accent: warm orange-gold
        int accent = 0xFFD4823A;
        putColor(m, "chats_unreadCounter", accent);
        putColor(m, "radioBackgroundChecked", accent);
        putColor(m, "windowBackgroundWhiteBlueIcon", accent);
        putColor(m, "windowBackgroundWhiteBlueText", accent);
        putColor(m, "windowBackgroundWhiteBlueHeader", accent);
        putColor(m, "switchTrackBlueChecked", accent);
        putColor(m, "dialogButton", accent);
        putColor(m, "dialogTextBlue", accent);
        putColor(m, "dialogTextBlue2", accent);
        putColor(m, "dialogTextBlue4", accent);
        putColor(m, "chat_messagePanelSend", accent);
        putColor(m, "progressCircle", accent);
        putColor(m, "chats_sentCheck", accent);
        putColor(m, "chats_verifiedBackground", accent);
        putColor(m, "profile_creatorIcon", accent);
        putColor(m, "chat_fieldOverlayText", accent);

        // Bubbles
        putColor(m, "chat_inBubble", 0xFF2D1A16);
        putColor(m, "chat_inBubbleSelected", 0xFF3A221C);
        putColor(m, "chat_inBubbleShadow", 0xFF1A0E0E);
        putColor(m, "chat_outBubbleShadow", 0xFF1A0E0E);

        // Title/text: warm parchment
        int title = 0xFFE8D0B0;
        putColor(m, "actionBarDefaultTitle", title);
        putColor(m, "actionBarDefaultIcon", title);
        putColor(m, "actionBarDefaultSearch", title);
        putColor(m, "windowBackgroundWhiteBlackText", title);

        // Red accents for fire
        putColor(m, "chat_goDownButtonCounterBackground", 0xFFC0392B);
        putColor(m, "location_sendLocationBackground", 0xFFB83A2A);
        putColor(m, "chat_attachAudioBackground", 0xFFD45B3A);

        // Action bar submenu
        putColor(m, "actionBarDefaultSubmenuBackground", 0xFF261614);
        putColor(m, "actionBarDefaultSubmenuSeparator", 0xFF2E1A18);
    }

    // ---- Чертог Льда (Ice) — cold blue-white tones ----
    private static void applyIcePalette(HashMap<String, String> m) {
        // Main backgrounds: deep cold blue
        putColor(m, "windowBackgroundGray", 0xFF0C1420);
        putColor(m, "windowBackgroundWhite", 0xFF111C2C);
        putColor(m, "windowBackgroundChecked", 0xFF162236);
        putColor(m, "actionBarDefault", 0xFF0F1926);
        putColor(m, "actionBarDefaultArchived", 0xFF0C1420);
        putColor(m, "chats_menuBackground", 0xFF0D1522);
        putColor(m, "dialogBackground", 0xFF111C2C);
        putColor(m, "inappPlayerBackground", 0xFF111C2C);
        putColor(m, "chat_emojiPanelBackground", 0xFF101A28);

        // Accent: icy cyan-blue
        int accent = 0xFF5EB3D4;
        putColor(m, "chats_unreadCounter", accent);
        putColor(m, "radioBackgroundChecked", accent);
        putColor(m, "windowBackgroundWhiteBlueIcon", accent);
        putColor(m, "windowBackgroundWhiteBlueText", accent);
        putColor(m, "windowBackgroundWhiteBlueHeader", accent);
        putColor(m, "switchTrackBlueChecked", accent);
        putColor(m, "dialogButton", accent);
        putColor(m, "dialogTextBlue", accent);
        putColor(m, "dialogTextBlue2", accent);
        putColor(m, "dialogTextBlue4", accent);
        putColor(m, "chat_messagePanelSend", accent);
        putColor(m, "progressCircle", accent);
        putColor(m, "chats_sentCheck", accent);
        putColor(m, "chats_verifiedBackground", accent);
        putColor(m, "profile_creatorIcon", accent);
        putColor(m, "chat_fieldOverlayText", accent);

        // Bubbles: dark blue
        putColor(m, "chat_inBubble", 0xFF172536);
        putColor(m, "chat_inBubbleSelected", 0xFF1E3048);
        putColor(m, "chat_inBubbleShadow", 0xFF0C1420);
        putColor(m, "chat_outBubbleShadow", 0xFF0C1420);

        // Title/text: frost white
        int title = 0xFFE0ECF4;
        putColor(m, "actionBarDefaultTitle", title);
        putColor(m, "actionBarDefaultIcon", title);
        putColor(m, "actionBarDefaultSearch", title);
        putColor(m, "windowBackgroundWhiteBlackText", title);

        // Icy highlights
        putColor(m, "chat_goDownButtonCounterBackground", 0xFF3A8FBA);
        putColor(m, "location_sendLocationBackground", 0xFF3580A8);
        putColor(m, "chat_attachAudioBackground", 0xFF4A9FCA);

        // Action bar submenu
        putColor(m, "actionBarDefaultSubmenuBackground", 0xFF131F30);
        putColor(m, "actionBarDefaultSubmenuSeparator", 0xFF1A2A3E);
    }

    // ---- Чертог Тьмы (Darkness) — almost pure black ----
    private static void applyDarkPalette(HashMap<String, String> m) {
        // Main backgrounds: near-black
        putColor(m, "windowBackgroundGray", 0xFF050505);
        putColor(m, "windowBackgroundWhite", 0xFF0A0A0A);
        putColor(m, "windowBackgroundChecked", 0xFF121212);
        putColor(m, "actionBarDefault", 0xFF080808);
        putColor(m, "actionBarDefaultArchived", 0xFF050505);
        putColor(m, "chats_menuBackground", 0xFF060606);
        putColor(m, "dialogBackground", 0xFF0A0A0A);
        putColor(m, "inappPlayerBackground", 0xFF0A0A0A);
        putColor(m, "chat_emojiPanelBackground", 0xFF080808);

        // Accent: muted bone/ash
        int accent = 0xFF8A8078;
        putColor(m, "chats_unreadCounter", accent);
        putColor(m, "radioBackgroundChecked", accent);
        putColor(m, "windowBackgroundWhiteBlueIcon", accent);
        putColor(m, "windowBackgroundWhiteBlueText", accent);
        putColor(m, "windowBackgroundWhiteBlueHeader", accent);
        putColor(m, "switchTrackBlueChecked", accent);
        putColor(m, "dialogButton", accent);
        putColor(m, "dialogTextBlue", accent);
        putColor(m, "dialogTextBlue2", accent);
        putColor(m, "dialogTextBlue4", accent);
        putColor(m, "chat_messagePanelSend", accent);
        putColor(m, "progressCircle", accent);
        putColor(m, "chats_sentCheck", accent);
        putColor(m, "chats_verifiedBackground", accent);
        putColor(m, "profile_creatorIcon", accent);
        putColor(m, "chat_fieldOverlayText", accent);

        // Bubbles: very dark grey
        putColor(m, "chat_inBubble", 0xFF141414);
        putColor(m, "chat_inBubbleSelected", 0xFF1C1C1C);
        putColor(m, "chat_inBubbleShadow", 0xFF050505);
        putColor(m, "chat_outBubbleShadow", 0xFF050505);

        // Title/text: pale grey
        int title = 0xFFCCC8C0;
        putColor(m, "actionBarDefaultTitle", title);
        putColor(m, "actionBarDefaultIcon", title);
        putColor(m, "actionBarDefaultSearch", title);
        putColor(m, "windowBackgroundWhiteBlackText", title);

        // Subtle dark highlights
        putColor(m, "chat_goDownButtonCounterBackground", 0xFF606060);
        putColor(m, "location_sendLocationBackground", 0xFF505050);
        putColor(m, "chat_attachAudioBackground", 0xFF686868);

        // Action bar submenu
        putColor(m, "actionBarDefaultSubmenuBackground", 0xFF0C0C0C);
        putColor(m, "actionBarDefaultSubmenuSeparator", 0xFF151515);
    }

    private static void putColor(HashMap<String, String> m, String key, int color) {
        m.put(key, String.valueOf(color));
    }
}
