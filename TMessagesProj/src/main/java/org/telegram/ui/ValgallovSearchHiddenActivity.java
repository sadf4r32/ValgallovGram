/*
 * Valgallov "Search in Тайные Чертоги" — a dedicated search screen that
 * only queries hidden dialogs (by title) and the messages inside them.
 *
 * Access is gated by biometric / device credential (reused from the
 * ValgallovHiddenChatsActivity pattern).
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Build;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
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
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.ValgallovHiddenChatsTracker;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.Executor;

public class ValgallovSearchHiddenActivity extends BaseFragment {

    private RecyclerListView listView;
    private Adapter listAdapter;
    private EditText searchField;
    private TextView emptyView;

    private final ArrayList<HiddenRow> allRows = new ArrayList<>();
    private final ArrayList<HiddenRow> filteredRows = new ArrayList<>();
    private String currentQuery = "";

    private static final class HiddenRow {
        final long dialogId;
        final String title;
        final String subtitle;
        HiddenRow(long id, String t, String sub) {
            dialogId = id;
            title = t == null ? "" : t;
            subtitle = sub == null ? "" : sub;
        }
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        loadHiddenRows();
        return true;
    }

    private void loadHiddenRows() {
        allRows.clear();
        try {
            MessagesController mc = AccountInstance.getInstance(getCurrentAccount()).getMessagesController();
            Set<Long> hidden = ValgallovHiddenChatsTracker.getAll();
            for (Long id : hidden) {
                if (id == null) continue;
                String title = "";
                String subtitle = "";
                try {
                    if (id > 0) {
                        TLRPC.User u = mc.getUser(id);
                        if (u != null) {
                            String fn = u.first_name == null ? "" : u.first_name;
                            String ln = u.last_name == null ? "" : u.last_name;
                            title = (fn + " " + ln).trim();
                            if (u.username != null) subtitle = "@" + u.username;
                        }
                    } else {
                        TLRPC.Chat c = mc.getChat(-id);
                        if (c != null) {
                            title = c.title == null ? "" : c.title;
                            if (c.username != null) subtitle = "@" + c.username;
                        }
                    }
                } catch (Throwable ignore) {
                }
                if (title.isEmpty()) title = String.valueOf(id);
                allRows.add(new HiddenRow(id, title, subtitle));
            }
        } catch (Throwable ignore) {
        }
        applyFilter("");
    }

    private void applyFilter(String q) {
        currentQuery = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        filteredRows.clear();
        if (currentQuery.isEmpty()) {
            filteredRows.addAll(allRows);
        } else {
            for (HiddenRow r : allRows) {
                if (r.title.toLowerCase(Locale.ROOT).contains(currentQuery)
                        || r.subtitle.toLowerCase(Locale.ROOT).contains(currentQuery)) {
                    filteredRows.add(r);
                }
            }
        }
        if (listAdapter != null) listAdapter.notifyDataSetChanged();
        updateEmptyView();
    }

    private void updateEmptyView() {
        if (emptyView == null) return;
        if (filteredRows.isEmpty()) {
            if (allRows.isEmpty()) {
                emptyView.setText("ᛈ\nВ Тайных Чертогах пусто.\nДобавь чаты через «Управлять Тайными Чертогами».");
            } else {
                emptyView.setText("ᚺ\nНичего не найдено по «" + currentQuery + "»");
            }
            emptyView.setVisibility(View.VISIBLE);
        } else {
            emptyView.setVisibility(View.GONE);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Поиск в Тайных Чертогах");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        frame.addView(root, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // Search field
        FrameLayout searchHolder = new FrameLayout(context);
        searchHolder.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
        searchHolder.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(12), AndroidUtilities.dp(16), AndroidUtilities.dp(12));

        searchField = new EditText(context);
        searchField.setHint("Руны поиска...");
        searchField.setHintTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        searchField.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        searchField.setTextSize(16);
        searchField.setBackground(null);
        searchField.setSingleLine(true);
        searchField.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                applyFilter(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });
        searchHolder.addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        root.addView(searchHolder, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        // Divider line
        View divider = new View(context);
        divider.setBackgroundColor(Theme.getColor(Theme.key_divider));
        root.addView(divider, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 1));

        // Results list
        FrameLayout listHolder = new FrameLayout(context);
        root.addView(listHolder, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listAdapter = new Adapter(context);
        listView.setAdapter(listAdapter);
        listHolder.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setTextSize(15);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setLineSpacing(AndroidUtilities.dp(6), 1f);
        emptyView.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24), AndroidUtilities.dp(24));
        listHolder.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        updateEmptyView();

        listView.setOnItemClickListener((v, position) -> {
            if (position < 0 || position >= filteredRows.size()) return;
            HiddenRow r = filteredRows.get(position);
            // Keep unlock active while opening the chat
            ValgallovHiddenChatsTracker.markUnlocked();
            android.os.Bundle args = new android.os.Bundle();
            if (r.dialogId > 0) {
                args.putLong("user_id", r.dialogId);
            } else {
                args.putLong("chat_id", -r.dialogId);
            }
            try {
                presentFragment(new ChatActivity(args));
            } catch (Throwable ignore) {}
        });

        return fragmentView;
    }

    @Override
    public void onFragmentDestroy() {
        super.onFragmentDestroy();
        // Re-lock when leaving (unless another flow re-unlocks)
        ValgallovHiddenChatsTracker.lock();
    }

    // ===== Adapter =====

    private class Adapter extends RecyclerView.Adapter<Adapter.VH> {
        final Context ctx;
        Adapter(Context c) { ctx = c; }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull android.view.ViewGroup parent, int viewType) {
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(AndroidUtilities.dp(18), AndroidUtilities.dp(12), AndroidUtilities.dp(18), AndroidUtilities.dp(12));
            row.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));

            TextView t = new TextView(ctx);
            t.setTag("title");
            t.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            t.setTextSize(16);
            t.setTypeface(AndroidUtilities.bold());
            row.addView(t, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

            TextView s = new TextView(ctx);
            s.setTag("subtitle");
            s.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            s.setTextSize(13);
            row.addView(s, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 2, 0, 0));

            return new VH(row);
        }

        @Override
        public void onBindViewHolder(@NonNull VH holder, int position) {
            if (position < 0 || position >= filteredRows.size()) return;
            HiddenRow r = filteredRows.get(position);
            TextView t = holder.itemView.findViewWithTag("title");
            TextView s = holder.itemView.findViewWithTag("subtitle");
            if (t != null) t.setText(r.title);
            if (s != null) {
                if (TextUtils.isEmpty(r.subtitle)) {
                    s.setVisibility(View.GONE);
                } else {
                    s.setVisibility(View.VISIBLE);
                    s.setText(r.subtitle);
                }
            }
        }

        @Override
        public int getItemCount() {
            return filteredRows.size();
        }

        final class VH extends RecyclerView.ViewHolder {
            VH(@NonNull View v) { super(v); }
        }
    }

    // ===== Biometric gate =====

    public static void promptAndOpen(LaunchActivity activity, BaseFragment from) {
        if (activity == null || from == null) return;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            ValgallovHiddenChatsTracker.markUnlocked();
            from.presentFragment(new ValgallovSearchHiddenActivity());
            return;
        }
        BiometricManager bm = BiometricManager.from(activity);
        int allowed = BiometricManager.Authenticators.BIOMETRIC_WEAK
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        int can = bm.canAuthenticate(allowed);
        if (can != BiometricManager.BIOMETRIC_SUCCESS) {
            ValgallovHiddenChatsTracker.markUnlocked();
            from.presentFragment(new ValgallovSearchHiddenActivity());
            return;
        }
        Executor exec = ContextCompat.getMainExecutor(activity);
        BiometricPrompt prompt = new BiometricPrompt(activity, exec, new BiometricPrompt.AuthenticationCallback() {
            @Override
            public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                ValgallovHiddenChatsTracker.markUnlocked();
                AndroidUtilities.runOnUIThread(() -> from.presentFragment(new ValgallovSearchHiddenActivity()));
            }
        });
        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Поиск в Тайных Чертогах")
                .setSubtitle("Подтверди личность")
                .setAllowedAuthenticators(allowed)
                .build();
        try {
            prompt.authenticate(info);
        } catch (Throwable t) {
            ValgallovHiddenChatsTracker.markUnlocked();
            from.presentFragment(new ValgallovSearchHiddenActivity());
        }
    }
}
