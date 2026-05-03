/*
 * Valgallov Theme Bootstrap — copies our bundled custom .attheme from assets
 * into the app's files dir on first launch (or whenever the file is missing)
 * and applies it. Tracks bootstrap state so we don't override user choices on
 * every start.
 */

package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;

import org.telegram.ui.ActionBar.Theme;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class ValgallovThemeBootstrap {

    private static final String PREFS = "valgallov_theme_v2";
    private static final String KEY_BOOTSTRAPPED = "applied_valgallov_theme";
    private static final String ASSET_NAME = "valgallov.attheme";
    private static final String THEME_NAME = "Valgallov";

    public static void applyIfNeeded() {
        if (!SharedConfig.valgallovBlueThemeDefault) return;
        SharedPreferences prefs = ApplicationLoader.applicationContext.getSharedPreferences(PREFS, 0);
        if (prefs.getBoolean(KEY_BOOTSTRAPPED, false)) return;
        try {
            applyValgallovTheme();
            prefs.edit().putBoolean(KEY_BOOTSTRAPPED, true).apply();
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("Valgallov: applied custom Valgallov theme on first launch");
            }
        } catch (Throwable t) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.e(t);
            }
        }
    }

    /** Force-apply the Valgallov theme right now (used by the Settings toggle). */
    public static void applyValgallovTheme() throws Exception {
        Context ctx = ApplicationLoader.applicationContext;
        File outFile = new File(ctx.getFilesDir(), ASSET_NAME);
        // Always re-copy so updates ship cleanly with new versions of the app.
        try (InputStream in = ctx.getAssets().open(ASSET_NAME);
             OutputStream out = new FileOutputStream(outFile)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        Theme.applyThemeFile(outFile, THEME_NAME, null, false);
    }
}
