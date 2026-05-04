package org.telegram.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.messenger.ValgallovReadLaterTracker;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ValgallovReadLaterActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter adapter;
    private ArrayList<ValgallovReadLaterTracker.Bookmark> bookmarks = new ArrayList<>();

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        bookmarks = ValgallovReadLaterTracker.getAll();
        return true;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("Прочитать позже");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        FrameLayout frame = new FrameLayout(context);
        frame.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        fragmentView = frame;

        if (bookmarks.isEmpty()) {
            TextView empty = new TextView(context);
            empty.setText("Пусто.\nДолгое нажатие на сообщение →\n«Прочитать позже»");
            empty.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            empty.setGravity(Gravity.CENTER);
            empty.setTextSize(15);
            empty.setLineSpacing(AndroidUtilities.dp(4), 1f);
            frame.addView(empty, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
            return fragmentView;
        }

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context));
        listView.setVerticalScrollBarEnabled(true);
        listView.setAdapter(adapter = new ListAdapter(context));
        listView.setOnItemClickListener((view, position) -> {
            if (position < 0 || position >= bookmarks.size()) return;
            ValgallovReadLaterTracker.Bookmark bm = bookmarks.get(position);
            if (bm.dialogId == 0) return;
            Bundle args = new Bundle();
            if (bm.dialogId > 0) {
                args.putLong("user_id", bm.dialogId);
            } else {
                args.putLong("chat_id", -bm.dialogId);
            }
            args.putInt("message_id", bm.msgId);
            presentFragment(new ChatActivity(args));
        });
        listView.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= bookmarks.size()) return false;
            ValgallovReadLaterTracker.Bookmark bm = bookmarks.get(position);
            ValgallovReadLaterTracker.remove(bm.dialogId, bm.msgId);
            bookmarks.remove(position);
            adapter.notifyItemRemoved(position);
            android.widget.Toast.makeText(getParentActivity(), "Убрано", android.widget.Toast.LENGTH_SHORT).show();
            return true;
        });
        frame.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        return fragmentView;
    }

    private static class RowView extends FrameLayout {
        final TextView senderView;
        final TextView textView;
        final TextView chatView;
        final TextView timeView;
        private final Paint dividerPaint;

        RowView(Context ctx) {
            super(ctx);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
            setMinimumHeight(AndroidUtilities.dp(64));
            setWillNotDraw(false);

            dividerPaint = new Paint();
            dividerPaint.setColor(Theme.getColor(Theme.key_divider));

            senderView = new TextView(ctx);
            senderView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            senderView.setTextSize(14);
            senderView.setTypeface(AndroidUtilities.bold());
            senderView.setSingleLine(true);
            senderView.setEllipsize(TextUtils.TruncateAt.END);
            addView(senderView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 16, 6, 80, 0));

            textView = new TextView(ctx);
            textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            textView.setTextSize(14);
            textView.setMaxLines(4);
            textView.setEllipsize(TextUtils.TruncateAt.END);
            addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 16, 26, 16, 0));

            chatView = new TextView(ctx);
            chatView.setTextColor(0xFFC9A86C);
            chatView.setTextSize(11);
            chatView.setSingleLine(true);
            addView(chatView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 16, 0, 80, 8));

            timeView = new TextView(ctx);
            timeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            timeView.setTextSize(11);
            timeView.setSingleLine(true);
            timeView.setGravity(Gravity.RIGHT);
            addView(timeView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 6, 14, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            canvas.drawLine(AndroidUtilities.dp(16), getHeight() - 1, getWidth(), getHeight() - 1, dividerPaint);
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {
        private final Context mContext;
        private final SimpleDateFormat sdf = new SimpleDateFormat("dd.MM HH:mm", Locale.getDefault());

        ListAdapter(Context ctx) { this.mContext = ctx; }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) { return true; }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            return new RecyclerListView.Holder(new RowView(mContext));
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            RowView row = (RowView) holder.itemView;
            ValgallovReadLaterTracker.Bookmark bm = bookmarks.get(position);

            row.senderView.setText(bm.senderName != null ? bm.senderName : "");
            row.textView.setText(bm.text != null ? bm.text : "");
            row.chatView.setText(bm.chatName != null ? bm.chatName : "");
            if (bm.date > 0) {
                row.timeView.setText(sdf.format(new Date((long) bm.date * 1000)));
            } else {
                row.timeView.setText("");
            }
        }

        @Override
        public int getItemCount() { return bookmarks.size(); }
    }
}
