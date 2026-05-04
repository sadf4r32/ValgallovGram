/*
 * Valgallov Hidden Chats management screen ("Тайные Чертоги").
 *
 * Lists every dialog with a checkbox; checked → hidden from main list.
 * Access is gated by the device biometric / pin (authenticate-only,
 * no crypto). After authentication we mark the tracker "unlocked"
 * so the user can see what they previously hid; auto-relock on exit.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AccountInstance;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.ValgallovHiddenChatsTracker;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.Executor;

public class ValgallovHiddenChatsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private final ArrayList<DialogRow> rows = new ArrayList<>();

    private int headerRow;
    private int infoRow;
    private int dialogsStartRow;
    private int dialogsEndRow;
    private int totalRows;

    private static final class DialogRow {
        final long dialogId;
        final String title;
        DialogRow(long id, String t) { dialogId = id; title = t; }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        loadRows();
        return true;
    }

    private void loadRows() {
        rows.clear();
        try {
            MessagesController mc = AccountInstance.getInstance(getCurrentAccount()).getMessagesController();
            ArrayList<TLRPC.Dialog> all = new ArrayList<>(mc.getAllDialogs());
            // Sort: hidden chats first, then alphabetical
            ArrayList<DialogRow> hidden = new ArrayList<>();
            ArrayList<DialogRow> visible = new ArrayList<>();
            for (TLRPC.Dialog d : all) {
                if (d == null) continue;
                String title = resolveTitle(mc, d);
                if (TextUtils.isEmpty(title)) title = String.valueOf(d.id);
                DialogRow row = new DialogRow(d.id, title);
                if (ValgallovHiddenChatsTracker.isHidden(d.id)) {
                    hidden.add(row);
                } else {
                    visible.add(row);
                }
            }
            rows.addAll(hidden);
            rows.addAll(visible);
        } catch (Throwable ignore) {
        }
        updateRowIndices();
    }

    private void updateRowIndices() {
        totalRows = 0;
        headerRow = totalRows++;
        infoRow = totalRows++;
        dialogsStartRow = totalRows;
        dialogsEndRow = dialogsStartRow + rows.size();
        totalRows = dialogsEndRow;
    }

    private String resolveTitle(MessagesController mc, TLRPC.Dialog d) {
        try {
            if (d.id > 0) {
                TLRPC.User u = mc.getUser(d.id);
                if (u != null) {
                    String fn = u.first_name == null ? "" : u.first_name;
                    String ln = u.last_name == null ? "" : u.last_name;
                    return (fn + " " + ln).trim();
                }
            } else {
                TLRPC.Chat c = mc.getChat(-d.id);
                if (c != null) return c.title;
            }
        } catch (Throwable ignore) {
        }
        return null;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Тайные Чертоги");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frame = (FrameLayout) fragmentView;
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        if (rows.isEmpty()) {
            LinearLayout emptyLayout = new LinearLayout(context);
            emptyLayout.setOrientation(LinearLayout.VERTICAL);
            emptyLayout.setGravity(Gravity.CENTER);

            TextView runeText = new TextView(context);
            runeText.setText("ᛈ");
            runeText.setTextSize(48);
            runeText.setTextColor(0xFF7A8AA0);
            runeText.setGravity(Gravity.CENTER);
            emptyLayout.addView(runeText, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 0, 0, 12));

            TextView empty = new TextView(context);
            empty.setText("Нет диалогов для скрытия.\nОткрой чаты, потом вернись сюда.");
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setTextSize(15);
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(AndroidUtilities.dp(4), 1f);
            emptyLayout.addView(empty, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

            frame.addView(emptyLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
            return fragmentView;
        }

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            int dialogIndex = position - dialogsStartRow;
            if (dialogIndex < 0 || dialogIndex >= rows.size()) return;
            DialogRow r = rows.get(dialogIndex);
            // If hidden — tap opens the chat directly
            if (ValgallovHiddenChatsTracker.isHidden(r.dialogId)) {
                openChat(r.dialogId);
                return;
            }
            // Otherwise toggle hide/unhide
            ValgallovHiddenChatsTracker.hide(r.dialogId);
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(true);
            }
            try {
                NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.dialogsNeedReload);
            } catch (Throwable ignore) {}
        });

        listView.setOnItemLongClickListener((view, position) -> {
            int dialogIndex = position - dialogsStartRow;
            if (dialogIndex < 0 || dialogIndex >= rows.size()) return false;
            DialogRow r = rows.get(dialogIndex);
            boolean isHidden = ValgallovHiddenChatsTracker.isHidden(r.dialogId);
            if (isHidden) {
                // Long-press on hidden chat → unhide
                ValgallovHiddenChatsTracker.unhide(r.dialogId);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(false);
                }
            } else {
                // Long-press on visible chat → hide
                ValgallovHiddenChatsTracker.hide(r.dialogId);
                if (view instanceof TextCheckCell) {
                    ((TextCheckCell) view).setChecked(true);
                }
            }
            try {
                NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.dialogsNeedReload);
            } catch (Throwable ignore) {}
            return true;
        });

        return fragmentView;
    }

    private void openChat(long dialogId) {
        try {
            android.os.Bundle args = new android.os.Bundle();
            if (dialogId > 0) {
                args.putLong("user_id", dialogId);
            } else {
                args.putLong("chat_id", -dialogId);
            }
            presentFragment(new ChatActivity(args));
        } catch (Throwable ignore) {}
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        ValgallovHiddenChatsTracker.lock();
        // Ensure dialogs list refreshes after leaving
        try {
            NotificationCenter.getInstance(getCurrentAccount()).postNotificationName(NotificationCenter.dialogsNeedReload);
        } catch (Throwable ignore) {}
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_INFO = 1;
        private static final int TYPE_CHECK = 2;

        private final Context mContext;
        ListAdapter(Context c) { mContext = c; }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == TYPE_CHECK;
        }

        @Override
        public int getItemCount() { return totalRows; }

        @Override
        public int getItemViewType(int position) {
            if (position == headerRow) return TYPE_HEADER;
            if (position == infoRow) return TYPE_INFO;
            return TYPE_CHECK;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case TYPE_CHECK:
                default:
                    TextCheckCell cell = new TextCheckCell(mContext);
                    cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    view = cell;
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    int hiddenCount = ValgallovHiddenChatsTracker.count();
                    cell.setText(hiddenCount > 0
                        ? "Скрыто чатов: " + hiddenCount
                        : "Выбери чаты для скрытия");
                    break;
                }
                case TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    cell.setText("Тап — войти в скрытый чат. Долгое нажатие — скрыть/раскрыть. Отмеченные чаты исчезнут из основного списка и поиска.");
                    break;
                }
                case TYPE_CHECK: {
                    int dialogIndex = position - dialogsStartRow;
                    if (dialogIndex >= 0 && dialogIndex < rows.size()) {
                        DialogRow r = rows.get(dialogIndex);
                        TextCheckCell cell = (TextCheckCell) holder.itemView;
                        boolean isHidden = ValgallovHiddenChatsTracker.isHidden(r.dialogId);
                        cell.setTextAndCheck(
                            (isHidden ? "ᛈ  " : "") + r.title,
                            isHidden,
                            dialogIndex < rows.size() - 1);
                    }
                    break;
                }
            }
        }
    }

    /**
     * Helper: ask for biometric / device credential, on success run callback on
     * UI thread. Sets ValgallovHiddenChatsTracker.unlocked = true as a side
     * effect on success.
     */
    public static void promptAndOpen(LaunchActivity activity, BaseFragment from) {
        if (activity == null || from == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            ValgallovHiddenChatsTracker.markUnlocked();
            from.presentFragment(new ValgallovHiddenChatsActivity());
            return;
        }
        BiometricManager bm = BiometricManager.from(activity);
        int allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        int can = bm.canAuthenticate(allowed);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            ValgallovHiddenChatsTracker.markUnlocked();
            from.presentFragment(new ValgallovHiddenChatsActivity());
            return;
        }
        Executor exec = ContextCompat.getMainExecutor(activity);
        BiometricPrompt prompt = new BiometricPrompt(activity, exec, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                ValgallovHiddenChatsTracker.markUnlocked();
                AndroidUtilities.runOnUIThread(() -> from.presentFragment(new ValgallovHiddenChatsActivity()));
            }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Тайные Чертоги")
                .setSubtitle("Подтверди личность чтобы открыть скрытые чаты")
                .setAllowedAuthenticators(allowed)
                .build();
        try {
            prompt.authenticate(info);
        } catch (Throwable t) {
            ValgallovHiddenChatsTracker.markUnlocked();
            from.presentFragment(new ValgallovHiddenChatsActivity());
        }
    }
}
