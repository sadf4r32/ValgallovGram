package org.telegram.messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.PowerManager;
import android.provider.Settings;

/**
 * Asks the user (once) to whitelist ValgallovGram from battery optimisations
 * so the foreground service is not killed in deep doze.
 */
public final class ValgallovBatteryHelper {

    private ValgallovBatteryHelper() {}

    private static final String PREFS = "valgallov_battery";
    private static final String KEY_ASKED = "asked";

    public static void maybeAskOnce(Activity activity) {
        if (activity == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return;
        try {
            SharedPreferences sp = activity.getSharedPreferences(PREFS, 0);
            if (sp.getBoolean(KEY_ASKED, false)) return;

            PowerManager pm = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            if (pm == null) return;
            String pkg = activity.getPackageName();
            if (pm.isIgnoringBatteryOptimizations(pkg)) {
                sp.edit().putBoolean(KEY_ASKED, true).apply();
                return;
            }

            sp.edit().putBoolean(KEY_ASKED, true).apply();

            new AlertDialog.Builder(activity)
                    .setTitle("Работа в фоне")
                    .setMessage("Чтобы ValgallovGram держал связь без лагов и не глушился в фоне, разреши ему работать без ограничений батареи.")
                    .setPositiveButton("Разрешить", (d, w) -> {
                        try {
                            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                            intent.setData(Uri.parse("package:" + pkg));
                            activity.startActivity(intent);
                        } catch (Throwable ignore) {
                            try {
                                activity.startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                            } catch (Throwable ignore2) {
                            }
                        }
                    })
                    .setNegativeButton("Не сейчас", null)
                    .show();
        } catch (Throwable ignore) {
        }
    }
}
