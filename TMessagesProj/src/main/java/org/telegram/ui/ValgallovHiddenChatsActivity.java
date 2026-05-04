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
import android.os.Build;
import android.text.TextUtils;
import android.view.View;
import android.widget.FrameLayout;

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
import org.telegram.messenger.R;
import org.telegram.messenger.ValgallovHiddenChatsTracker;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.concurrent.Executor;

public class ValgallovHiddenChatsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;
    private final ArrayList<DialogRow> rows = new ArrayList<>();

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
            for (TLRPC.Dialog d : all) {
                if (d == null) continue;
                String title = resolveTitle(mc, d);
                if (TextUtils.isEmpty(title)) title = String.valueOf(d.id);
                rows.add(new DialogRow(d.id, title));
            }
        } catch (Throwable ignore) {
        }
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

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setAdapter(listAdapter = new ListAdapter(context));
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= rows.size()) return;
            DialogRow r = rows.get(position);
            boolean nowHidden = !ValgallovHiddenChatsTracker.isHidden(r.dialogId);
            if (nowHidden) {
                ValgallovHiddenChatsTracker.hide(r.dialogId);
            } else {
                ValgallovHiddenChatsTracker.unhide(r.dialogId);
            }
            if (view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(nowHidden);
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        // Re-lock secret folder on leave
        ValgallovHiddenChatsTracker.lock();
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private final Context mContext;
        ListAdapter(Context c) { mContext = c; }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @Override
        public int getItemCount() { return rows.size(); }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            TextCheckCell cell = new TextCheckCell(mContext);
            cell.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            cell.setLayoutParams(new RecyclerView.LayoutParams(
                RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(cell);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            DialogRow r = rows.get(position);
            TextCheckCell cell = (TextCheckCell) holder.itemView;
            cell.setTextAndCheck(r.title, ValgallovHiddenChatsTracker.isHidden(r.dialogId), position < rows.size() - 1);
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
            // No biometric API — open directly
            ValgallovHiddenChatsTracker.markUnlocked();
            from.presentFragment(new ValgallovHiddenChatsActivity());
            return;
        }
        BiometricManager bm = BiometricManager.from(activity);
        int allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        int can = bm.canAuthenticate(allowed);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            // Fallback: open without auth (no biometric available)
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
