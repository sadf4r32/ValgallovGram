/*
 * Valgallov: per-chat and global deleted message history viewer.
 * Shows actual deleted message content (not just IDs).
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
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.ValgallovDeletedTracker;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ValgallovDeletedHistoryActivity extends BaseFragment {

    private final long dialogId;
    private RecyclerListView listView;
    private ListAdapter adapter;
    private ArrayList<ValgallovDeletedTracker.DeletedMessage> deletedMessages = new ArrayList<>();

    public ValgallovDeletedHistoryActivity(long dialogId) {
        this.dialogId = dialogId;
    }

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        if (dialogId == 0) {
            deletedMessages = ValgallovDeletedTracker.getAllDeletedMessages();
        } else {
            deletedMessages = ValgallovDeletedTracker.getDeletedMessages(dialogId);
        }
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(dialogId == 0 ? "Вся история стёртого" : "История стёртого");
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

        if (deletedMessages.isEmpty()) {
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
            if (position < 0 || position >= deletedMessages.size()) return;
            ValgallovDeletedTracker.DeletedMessage dm = deletedMessages.get(position);
            long did = dm.dialogId;
            if (did == 0) return;
            Bundle args = new Bundle();
            if (did > 0) {
                args.putLong("user_id", did);
            } else {
                args.putLong("chat_id", -did);
            }
            args.putInt("message_id", dm.msgId);
            ChatActivity fragment = new ChatActivity(args);
            presentFragment(fragment);
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private static class RowView extends FrameLayout {
        final TextView rune;
        final TextView title;
        final TextView subtitle;
        final TextView timeView;

        RowView(Context ctx) {
            super(ctx);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(8));
            setMinimumHeight(AndroidUtilities.dp(64));

            rune = new TextView(ctx);
            rune.setText("ᚺ");
            rune.setTextColor(0xFF7A8AA0);
            rune.setTextSize(22);
            rune.setTypeface(AndroidUtilities.bold());
            rune.setGravity(Gravity.CENTER);
            addView(rune, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.TOP, 16, 8, 0, 0));

            title = new TextView(ctx);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(15);
            title.setMaxLines(5);
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 64, 8, 16, 0));

            subtitle = new TextView(ctx);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitle.setTextSize(12);
            subtitle.setSingleLine(true);
            subtitle.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 64, 0, 80, 8));

            timeView = new TextView(ctx);
            timeView.setTextColor(0xFF7A8AA0);
            timeView.setTextSize(12);
            timeView.setSingleLine(true);
            timeView.setGravity(Gravity.RIGHT);
            addView(timeView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 16, 8));
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm:ss", Locale.getDefault());

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
            ValgallovDeletedTracker.DeletedMessage dm = deletedMessages.get(position);
            row.title.setText(dm.text);
            row.subtitle.setText(dm.outgoing ? "Ты удалил" : "Собеседник удалил");
            if (dm.date > 0) {
                row.timeView.setText(sdf.format(new Date((long) dm.date * 1000)));
            } else {
                row.timeView.setText("");
            }
        }

        @Override
        public int getItemCount() {
            return deletedMessages.size();
        }
    }
}
