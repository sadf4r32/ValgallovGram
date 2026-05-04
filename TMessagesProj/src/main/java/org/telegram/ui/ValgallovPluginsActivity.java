/*
 * ValgallovGram Plugin Manager UI.
 *
 * Lists every .js file in `/sdcard/Download/valgallov_plugins/` with:
 *   - enable/disable switch (per plugin)
 *   - delete button
 *   - metadata (name, author, version, description) parsed from header comments
 *
 * Pull-to-refresh reloads the plugin set from disk.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.ValgallovPluginManager;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.List;

public class ValgallovPluginsActivity extends BaseFragment {

    private RecyclerView listView;
    private Adapter adapter;
    private TextView emptyView;
    private final List<ValgallovPluginManager.PluginInfo> installed = new ArrayList<>();
    private final List<ValgallovPluginManager.PluginInfo> builtins = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Плагины");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        // Reload menu item
        org.telegram.ui.ActionBar.ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(1, R.drawable.msg_reset).setContentDescription("Перезагрузить");

        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == 1) {
                    refresh(true);
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout root = (FrameLayout) fragmentView;
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        adapter = new Adapter();
        listView.setAdapter(adapter);
        root.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        emptyView = new TextView(context);
        emptyView.setGravity(Gravity.CENTER);
        emptyView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        emptyView.setTextSize(15);
        emptyView.setPadding(AndroidUtilities.dp(32), 0, AndroidUtilities.dp(32), 0);
        emptyView.setText("Плагинов нет.\n\nСкопируй .js файлы в\nDownloads/valgallov_plugins/\nи нажми «Перезагрузить».");
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        refresh(false);
        return fragmentView;
    }

    private void refresh(boolean reloadEngine) {
        if (reloadEngine) {
            ValgallovPluginManager.reload();
            Toast.makeText(getParentActivity(),
                "Перезагружено: " + ValgallovPluginManager.loadedCount() + " активных",
                Toast.LENGTH_SHORT).show();
        }
        installed.clear();
        installed.addAll(ValgallovPluginManager.listAll());
        builtins.clear();
        // only show builtins that are not already installed
        for (ValgallovPluginManager.PluginInfo b : ValgallovPluginManager.listBuiltins()) {
            if (!ValgallovPluginManager.isInstalled(b.fileName)) builtins.add(b);
        }
        if (adapter != null) adapter.notifyDataSetChanged();
        if (emptyView != null) {
            emptyView.setVisibility(installed.isEmpty() && builtins.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh(false);
    }

    // ------------ Adapter ------------

    private static final int TYPE_HEADER = 0;
    private static final int TYPE_INSTALLED = 1;
    private static final int TYPE_BUILTIN = 2;

    private class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        // Layout:  [header "Установлено"]  [installed...]  [header "Встроенные"]  [builtins...]
        //  — if installed empty: first header becomes the builtins one (no double "empty" sections)

        private int headerInstalledPos() { return installed.isEmpty() ? -1 : 0; }
        private int headerBuiltinsPos()  {
            if (builtins.isEmpty()) return -1;
            return installed.isEmpty() ? 0 : 1 + installed.size();
        }

        @Override
        public int getItemViewType(int position) {
            int hi = headerInstalledPos();
            int hb = headerBuiltinsPos();
            if (position == hi || position == hb) return TYPE_HEADER;
            if (hi == 0 && position > 0 && position <= installed.size()) return TYPE_INSTALLED;
            return TYPE_BUILTIN;
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            Context ctx = parent.getContext();
            if (viewType == TYPE_HEADER) return new HeaderHolder(ctx);
            return new Holder(ctx);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            int hi = headerInstalledPos();
            int hb = headerBuiltinsPos();
            if (position == hi) { ((HeaderHolder) holder).label.setText("УСТАНОВЛЕНО"); return; }
            if (position == hb) { ((HeaderHolder) holder).label.setText("ВСТРОЕННЫЕ"); return; }
            if (hi == 0 && position > 0 && position <= installed.size()) {
                ((Holder) holder).bindInstalled(installed.get(position - 1));
            } else {
                int idxBase = (hb >= 0) ? hb + 1 : 0;
                ((Holder) holder).bindBuiltin(builtins.get(position - idxBase));
            }
        }

        @Override
        public int getItemCount() {
            int n = 0;
            if (!installed.isEmpty()) n += 1 + installed.size();
            if (!builtins.isEmpty())  n += 1 + builtins.size();
            return n;
        }

        class HeaderHolder extends RecyclerView.ViewHolder {
            final TextView label;
            HeaderHolder(Context ctx) {
                super(new TextView(ctx));
                label = (TextView) itemView;
                label.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                label.setTextSize(12);
                label.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                int h = AndroidUtilities.dp(16);
                label.setPadding(h, AndroidUtilities.dp(18), h, AndroidUtilities.dp(8));
                label.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
                label.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            }
        }

        class Holder extends RecyclerView.ViewHolder {
            final TextView titleView;
            final TextView subtitleView;
            final TextView metaView;
            final TextView toggleButton;
            final TextView deleteButton;

            Holder(Context ctx) {
                super(buildHolderRoot(ctx));
                LinearLayout root = (LinearLayout) itemView;
                LinearLayout topRow = (LinearLayout) root.getChildAt(0);
                LinearLayout textCol = (LinearLayout) topRow.getChildAt(0);
                LinearLayout btnRow = (LinearLayout) root.getChildAt(1);
                titleView = (TextView) textCol.getChildAt(0);
                subtitleView = (TextView) textCol.getChildAt(1);
                metaView = (TextView) textCol.getChildAt(2);
                toggleButton = (TextView) btnRow.getChildAt(0);
                deleteButton = (TextView) btnRow.getChildAt(1);
            }

            private void fillMeta(ValgallovPluginManager.PluginInfo p, String statusOverride) {
                titleView.setText(p.name);
                String subtitle = p.fileName;
                if (p.description != null && !p.description.isEmpty()) {
                    subtitle = p.description + "  ·  " + p.fileName;
                }
                subtitleView.setText(subtitle);
                StringBuilder meta = new StringBuilder();
                if (p.author != null && !p.author.isEmpty()) meta.append(p.author);
                if (p.version != null && !p.version.isEmpty()) {
                    if (meta.length() > 0) meta.append("  ·  ");
                    meta.append("v").append(p.version);
                }
                if (p.sizeBytes > 0) {
                    if (meta.length() > 0) meta.append("  ·  ");
                    meta.append(Math.max(1, p.sizeBytes / 1024)).append(" КБ");
                }
                if (statusOverride != null) {
                    if (meta.length() > 0) meta.append("  ·  ");
                    meta.append(statusOverride);
                }
                metaView.setText(meta.toString());
            }

            void bindInstalled(final ValgallovPluginManager.PluginInfo p) {
                String status = p.loaded ? "загружен" : (p.enabled ? "выключен глобально" : "отключён");
                fillMeta(p, status);

                boolean on = p.enabled;
                toggleButton.setText(on ? "Выключить" : "Включить");
                toggleButton.setTextColor(on ? 0xFFC04A3E : 0xFF3F9D5E);
                toggleButton.setOnClickListener(v -> {
                    ValgallovPluginManager.setEnabled(p.fileName, !on);
                    if (SharedConfig.valgallovPluginsEnabled) ValgallovPluginManager.reload();
                    refresh(false);
                });

                deleteButton.setVisibility(View.VISIBLE);
                deleteButton.setText("Удалить");
                deleteButton.setTextColor(0xFFC04A3E);
                deleteButton.setOnClickListener(v -> {
                    AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity());
                    b.setTitle("Удалить " + p.fileName + "?");
                    b.setMessage("Файл будет удалён с устройства. Это необратимо.");
                    b.setPositiveButton("Удалить", (d, w) -> {
                        boolean ok = ValgallovPluginManager.delete(p.fileName);
                        Toast.makeText(getParentActivity(),
                            ok ? "Удалено: " + p.fileName : "Не удалось удалить",
                            Toast.LENGTH_SHORT).show();
                        if (SharedConfig.valgallovPluginsEnabled) ValgallovPluginManager.reload();
                        refresh(false);
                    });
                    b.setNegativeButton("Отмена", null);
                    b.show();
                });
            }

            void bindBuiltin(final ValgallovPluginManager.PluginInfo p) {
                fillMeta(p, "встроенный — не установлен");

                toggleButton.setText("Установить");
                toggleButton.setTextColor(0xFF3F9D5E);
                toggleButton.setOnClickListener(v -> {
                    boolean ok = ValgallovPluginManager.installBuiltin(p.fileName);
                    Toast.makeText(getParentActivity(),
                        ok ? "Установлено: " + p.fileName : "Не удалось установить",
                        Toast.LENGTH_SHORT).show();
                    if (ok && SharedConfig.valgallovPluginsEnabled) ValgallovPluginManager.reload();
                    refresh(false);
                });

                deleteButton.setVisibility(View.GONE);
            }
        }
    }

    private static View buildHolderRoot(Context ctx) {
                LinearLayout root = new LinearLayout(ctx);
                root.setOrientation(LinearLayout.VERTICAL);
                root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                int pad = AndroidUtilities.dp(16);
                root.setPadding(pad, AndroidUtilities.dp(12), pad, AndroidUtilities.dp(12));
                LinearLayout.LayoutParams rootLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rootLp.bottomMargin = AndroidUtilities.dp(1);
                root.setLayoutParams(rootLp);

                // Top: text column
                LinearLayout topRow = new LinearLayout(ctx);
                topRow.setOrientation(LinearLayout.HORIZONTAL);
                root.addView(topRow, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                LinearLayout textCol = new LinearLayout(ctx);
                textCol.setOrientation(LinearLayout.VERTICAL);
                LinearLayout.LayoutParams colLp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
                topRow.addView(textCol, colLp);

                TextView title = new TextView(ctx);
                title.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
                title.setTextSize(16);
                textCol.addView(title);

                TextView subtitle = new TextView(ctx);
                subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                subtitle.setTextSize(13);
                subtitle.setPadding(0, AndroidUtilities.dp(2), 0, 0);
                textCol.addView(subtitle);

                TextView meta = new TextView(ctx);
                meta.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
                meta.setTextSize(12);
                meta.setPadding(0, AndroidUtilities.dp(2), 0, 0);
                textCol.addView(meta);

                // Bottom: buttons row
                LinearLayout btnRow = new LinearLayout(ctx);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams btnRowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                btnRowLp.topMargin = AndroidUtilities.dp(10);
                root.addView(btnRow, btnRowLp);

                TextView toggle = new TextView(ctx);
                toggle.setTextSize(13);
                toggle.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6),
                                  AndroidUtilities.dp(10), AndroidUtilities.dp(6));
                toggle.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                btnRow.addView(toggle);

                TextView del = new TextView(ctx);
                del.setTextSize(13);
                del.setText("Удалить");
                del.setTextColor(0xFFC04A3E);
                del.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(6),
                               AndroidUtilities.dp(10), AndroidUtilities.dp(6));
                del.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));
                LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                delLp.leftMargin = AndroidUtilities.dp(8);
                btnRow.addView(del, delLp);

                return root;
    }
}
