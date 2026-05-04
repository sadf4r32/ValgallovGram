package org.telegram.ui;

import android.app.Activity;
import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.ValgallovEditTracker;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

/**
 * Dialog that shows the previous text versions of an edited message,
 * captured by ValgallovEditTracker. Triggered from the long-press
 * "Переписано" menu entry in ChatActivity.
 */
public final class ValgallovEditHistoryDialog {

    private ValgallovEditHistoryDialog() {}

    public static void show(Activity activity, Theme.ResourcesProvider provider, MessageObject msg) {
        if (activity == null || msg == null) return;
        final long dialogId = msg.getDialogId();
        final int msgId = msg.getId();
        ArrayList<ValgallovEditTracker.Version> versions =
                ValgallovEditTracker.getVersionsForMessage(dialogId, msgId);

        AlertDialog.Builder b = new AlertDialog.Builder(activity, provider);
        b.setTitle("Переписано");

        Context ctx = activity;
        ScrollView scroll = new ScrollView(ctx);
        LinearLayout list = new LinearLayout(ctx);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(8),
                AndroidUtilities.dp(16), AndroidUtilities.dp(8));
        scroll.addView(list);

        if (versions == null || versions.isEmpty()) {
            TextView empty = new TextView(ctx);
            empty.setText("Сага молчит — этих рун ещё не было.");
            empty.setTextColor(Theme.getColor(Theme.key_dialogTextHint, provider));
            empty.setTextSize(14);
            empty.setGravity(Gravity.CENTER);
            list.addView(empty);
        } else {
            // current text first
            String currentText = msg.messageText != null ? msg.messageText.toString() : "";
            if (TextUtils.isEmpty(currentText) && msg.messageOwner != null && msg.messageOwner.message != null) {
                currentText = msg.messageOwner.message;
            }
            addEntry(ctx, list, provider, "Сейчас", currentText, true);

            // previous versions, newest first
            for (int i = versions.size() - 1; i >= 0; i--) {
                ValgallovEditTracker.Version v = versions.get(i);
                String when = formatTime(v.timestamp);
                addEntry(ctx, list, provider, when, v.text, false);
            }
        }

        b.setView(scroll);
        b.setPositiveButton(LocaleController.getString(org.telegram.messenger.R.string.Close), null);
        AlertDialog dlg = b.create();
        dlg.show();
    }

    private static void addEntry(Context ctx, LinearLayout parent,
                                  Theme.ResourcesProvider provider,
                                  String header, String text, boolean isCurrent) {
        TextView label = new TextView(ctx);
        label.setText(header + (isCurrent ? "" : "  ·  переписано"));
        label.setTextColor(Theme.getColor(isCurrent ? Theme.key_dialogTextBlue
                : Theme.key_dialogTextGray3, provider));
        label.setTextSize(12);
        label.setTypeface(AndroidUtilities.bold());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = AndroidUtilities.dp(parent.getChildCount() == 0 ? 0 : 12);
        parent.addView(label, lp);

        TextView body = new TextView(ctx);
        body.setText(text == null || text.isEmpty() ? "(пусто)" : text);
        body.setTextColor(Theme.getColor(Theme.key_dialogTextBlack, provider));
        body.setTextSize(15);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.topMargin = AndroidUtilities.dp(2);
        parent.addView(body, bp);
    }

    private static String formatTime(long ts) {
        try {
            return new SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(new Date(ts));
        } catch (Throwable ignore) {
            return String.valueOf(ts);
        }
    }
}
