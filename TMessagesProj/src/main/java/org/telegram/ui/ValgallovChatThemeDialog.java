package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.ValgallovChatThemeTracker;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

/**
 * Dialog that lets the user pick a per-chat theme preset.
 */
public final class ValgallovChatThemeDialog {

    private ValgallovChatThemeDialog() {}

    public static void show(final Activity activity, final long dialogId, final Runnable onApplied) {
        if (activity == null) return;

        final int currentPreset = ValgallovChatThemeTracker.getPreset(dialogId);

        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(4), AndroidUtilities.dp(16), AndroidUtilities.dp(4));

        final AlertDialog.Builder builder = new AlertDialog.Builder(activity);
        builder.setTitle("Чертог темы");

        final AlertDialog[] dlgHolder = new AlertDialog[1];

        for (int i = 0; i < ValgallovChatThemeTracker.NAMES_RU.length; i++) {
            final int preset = i;

            LinearLayout row = new LinearLayout(activity);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
            row.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector), 2));
            row.setClickable(true);
            row.setFocusable(true);

            // Color swatch
            View swatch = new View(activity);
            int tint = ValgallovChatThemeTracker.getTintColor(preset);
            int swatchColor = preset == 0 ? Theme.getColor(Theme.key_windowBackgroundWhite) : tint;
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(swatchColor);
            circle.setStroke(AndroidUtilities.dp(2), ValgallovChatThemeTracker.getAccentColor(preset));
            swatch.setBackground(circle);
            row.addView(swatch, LayoutHelper.createLinear(32, 32, Gravity.CENTER_VERTICAL, 0, 0, 16, 0));

            // Name
            TextView name = new TextView(activity);
            name.setText(ValgallovChatThemeTracker.getName(preset));
            name.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            row.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

            // Check mark for current selection
            if (preset == currentPreset) {
                ImageView check = new ImageView(activity);
                check.setImageResource(R.drawable.msg_check_s);
                check.setColorFilter(ValgallovChatThemeTracker.getAccentColor(preset));
                row.addView(check, LayoutHelper.createLinear(24, 24, Gravity.CENTER_VERTICAL));
            }

            row.setOnClickListener(v -> {
                ValgallovChatThemeTracker.setPreset(dialogId, preset);
                if (dlgHolder[0] != null) dlgHolder[0].dismiss();
                if (onApplied != null) onApplied.run();
            });

            root.addView(row, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        }

        builder.setView(root);
        builder.setNegativeButton("Отмена", null);
        dlgHolder[0] = builder.show();
    }
}
