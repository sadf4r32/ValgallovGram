/*
 * Valgallov: per-chat deleted message history viewer.
 */

package org.telegram.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.ValgallovDeletedTracker;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;

public class ValgallovDeletedHistoryActivity extends BaseFragment {

    private final long dialogId;
    private RecyclerListView listView;
    private ListAdapter adapter;
    private ArrayList<Integer> deletedIds = new ArrayList<>();

    public ValgallovDeletedHistoryActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        deletedIds = ValgallovDeletedTracker.getDeletedList(dialogId);
        java.util.Collections.reverse(deletedIds);
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("История стёртого");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;

        if (deletedIds.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Пока ничего не стёрто.\nКогда кто-то удалит сообщение —\nоно появится здесь.");
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setGravity(Gravity.CENTER);
            empty.setLineSpacing(AndroidUtilities.dp(4), 1f);
            frame.addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
            return fragmentView;
        }

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(true);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            int msgId = deletedIds.get(position);
            Bundle args = new Bundle();
            if (dialogId > 0) {
                args.putLong("user_id", dialogId);
            } else {
                args.putLong("chat_id", -dialogId);
            }
            args.putInt("message_id", msgId);
            ChatActivity fragment = new ChatActivity(args);
            presentFragment(fragment);
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private static class RowView extends FrameLayout {
        final TextView title;
        final TextView subtitle;

        RowView(Context ctx) {
            super(ctx);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, AndroidUtilities.dp(64)));

            TextView rune = new TextView(ctx);
            rune.setText("ᚺ");
            rune.setTextColor(0xFF7A8AA0);
            rune.setTextSize(22);
            rune.setTypeface(AndroidUtilities.bold());
            rune.setGravity(Gravity.CENTER);
            addView(rune, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));

            title = new TextView(ctx);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(15);
            title.setSingleLine(true);
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 64, 14, 16, 0));

            subtitle = new TextView(ctx);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitle.setTextSize(13);
            subtitle.setSingleLine(true);
            subtitle.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 64, 36, 16, 0));
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;

        ListAdapter(Context ctx) {
            this.mContext = ctx;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return true;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new RowView(mContext));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            RowView row = (RowView) holder.itemView;
            int msgId = deletedIds.get(position);
            row.title.setText("Сообщение #" + msgId);
            row.subtitle.setText("Тапни, чтобы открыть в чате");
        }

        @Override
        public int getItemCount() {
            return deletedIds.size();
        }
    }
}
