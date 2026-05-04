package org.telegram.messenger;

import android.app.Activity;
import android.os.Build;
import android.widget.Toast;

/**
 * Anti-screen-record detection for ValgallovGram.
 * On API 34+ uses Activity.ScreenCaptureCallback.
 * Shows a toast warning when screen capture is detected.
 */
public final class ValgallovScreenGuard {

    private static Object sCallback; // holds reference to the callback to prevent GC

    private ValgallovScreenGuard() {}

    public static void register(Activity activity) {
        if (!SharedConfig.valgallovAntiScreenRecord) return;
        if (Build.VERSION.SDK_INT >= 34) {
            try {
                // API 34+: Activity.ScreenCaptureCallback
                Activity.ScreenCaptureCallback callback = () -> {
                    if (!SharedConfig.valgallovAntiScreenRecord) return;
                    AndroidUtilities.runOnUIThread(() -> {
                        try {
                            Toast.makeText(activity, "⚠ Обнаружена запись экрана", Toast.LENGTH_LONG).show();
                        } catch (Throwable ignore) {}
                    });
                };
                activity.registerScreenCaptureCallback(activity.getMainExecutor(), callback);
                sCallback = callback;
            } catch (Throwable ignore) {}
        }
    }

    public static void unregister(Activity activity) {
        if (Build.VERSION.SDK_INT >= 34 && sCallback != null) {
            try {
                activity.unregisterScreenCaptureCallback((Activity.ScreenCaptureCallback) sCallback);
                sCallback = null;
            } catch (Throwable ignore) {}
        }
    }
}
