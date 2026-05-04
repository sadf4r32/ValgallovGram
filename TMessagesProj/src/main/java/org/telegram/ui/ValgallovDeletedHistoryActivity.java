/*
 * Valgallov: per-chat and global deleted message history viewer.
 * Shows actual deleted message content with avatar, sender info, and media type.
 */

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
import org.telegram.messenger.ValgallovDeletedTracker;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class ValgallovDeletedHistoryActivity extends BaseFragment {

    private static final int RUNE_COLOR = 0xFFD44040; // Red deletion rune

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
        final BackupImageView avatarView;
        final AvatarDrawable avatarDrawable;
        final TextView senderNameView;
        final TextView title;
        final TextView subtitle;
        final TextView timeView;
        final TextView runeView;
        final TextView mediaBadge;
        private final Paint dividerPaint;

        RowView(Context ctx) {
            super(ctx);
            setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
            setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            setPadding(0, AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10));
            setMinimumHeight(AndroidUtilities.dp(72));
            setWillNotDraw(false);

            dividerPaint = new Paint();
            dividerPaint.setColor(Theme.getColor(Theme.key_divider));
            dividerPaint.setStrokeWidth(1);

            // Avatar (40dp circle, left side)
            avatarDrawable = new AvatarDrawable();
            avatarView = new BackupImageView(ctx);
            avatarView.setRoundRadius(AndroidUtilities.dp(20));
            addView(avatarView, LayoutHelper.createFrame(40, 40, Gravity.LEFT | Gravity.TOP, 14, 8, 0, 0));

            // Sender name (top, after avatar)
            senderNameView = new TextView(ctx);
            senderNameView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            senderNameView.setTextSize(14);
            senderNameView.setTypeface(AndroidUtilities.bold());
            senderNameView.setSingleLine(true);
            senderNameView.setEllipsize(TextUtils.TruncateAt.END);
            addView(senderNameView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 66, 6, 50, 0));

            // Message title (content, below sender name)
            title = new TextView(ctx);
            title.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            title.setTextSize(14);
            title.setMaxLines(4);
            title.setEllipsize(TextUtils.TruncateAt.END);
            addView(title, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 66, 26, 50, 0));

            // Subtitle (who deleted: "Ты удалил" / "Собеседник удалил")
            subtitle = new TextView(ctx);
            subtitle.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            subtitle.setTextSize(12);
            subtitle.setSingleLine(true);
            subtitle.setTypeface(Typeface.DEFAULT, Typeface.ITALIC);
            addView(subtitle, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.BOTTOM, 66, 0, 80, 8));

            // Time view (bottom right)
            timeView = new TextView(ctx);
            timeView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            timeView.setTextSize(11);
            timeView.setSingleLine(true);
            timeView.setGravity(Gravity.RIGHT);
            addView(timeView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.BOTTOM, 0, 0, 14, 8));

            // Red rune (top right)
            runeView = new TextView(ctx);
            runeView.setText("ᚺ");
            runeView.setTextColor(RUNE_COLOR);
            runeView.setTextSize(20);
            runeView.setTypeface(AndroidUtilities.bold());
            runeView.setGravity(Gravity.CENTER);
            addView(runeView, LayoutHelper.createFrame(32, 32, Gravity.RIGHT | Gravity.TOP, 0, 6, 12, 0));

            // Media badge (small label below rune if media)
            mediaBadge = new TextView(ctx);
            mediaBadge.setTextColor(0xFFFF6B35);
            mediaBadge.setTextSize(9);
            mediaBadge.setSingleLine(true);
            mediaBadge.setTypeface(AndroidUtilities.bold());
            mediaBadge.setGravity(Gravity.CENTER);
            addView(mediaBadge, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.RIGHT | Gravity.TOP, 0, 36, 8, 0));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            // Draw bottom divider line
            canvas.drawLine(AndroidUtilities.dp(66), getHeight() - 1, getWidth(), getHeight() - 1, dividerPaint);
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

            // Set sender name
            String displayName = null;
            if (dm.senderName != null && !dm.senderName.isEmpty()) {
                displayName = dm.senderName;
            } else if (dm.outgoing) {
                displayName = "Ты";
            } else {
                displayName = "Собеседник";
            }
            row.senderNameView.setText(displayName);

            // Set avatar
            try {
                long sid = dm.senderId;
                if (sid > 0) {
                    TLRPC.User user = getMessagesController().getUser(sid);
                    if (user != null) {
                        row.avatarDrawable.setInfo(currentAccount, user);
                        row.avatarView.setForUserOrChat(user, row.avatarDrawable);
                    } else {
                        row.avatarDrawable.setInfo(sid, displayName, "");
                        row.avatarView.setImage(null, null, row.avatarDrawable, null);
                    }
                } else if (sid < 0) {
                    TLRPC.Chat chat = getMessagesController().getChat(-sid);
                    if (chat != null) {
                        row.avatarDrawable.setInfo(currentAccount, chat);
                        row.avatarView.setForUserOrChat(chat, row.avatarDrawable);
                    } else {
                        row.avatarDrawable.setInfo(sid, displayName, "");
                        row.avatarView.setImage(null, null, row.avatarDrawable, null);
                    }
                } else {
                    // No sender ID — use dialog ID as fallback
                    long fallback = dm.dialogId;
                    if (fallback > 0) {
                        TLRPC.User user = getMessagesController().getUser(fallback);
                        if (user != null) {
                            row.avatarDrawable.setInfo(currentAccount, user);
                            row.avatarView.setForUserOrChat(user, row.avatarDrawable);
                        } else {
                            row.avatarDrawable.setInfo(fallback, displayName, "");
                            row.avatarView.setImage(null, null, row.avatarDrawable, null);
                        }
                    } else if (fallback < 0) {
                        TLRPC.Chat chat = getMessagesController().getChat(-fallback);
                        if (chat != null) {
                            row.avatarDrawable.setInfo(currentAccount, chat);
                            row.avatarView.setForUserOrChat(chat, row.avatarDrawable);
                        } else {
                            row.avatarDrawable.setInfo(fallback, displayName, "");
                            row.avatarView.setImage(null, null, row.avatarDrawable, null);
                        }
                    } else {
                        row.avatarDrawable.setInfo(0L, displayName, "");
                        row.avatarView.setImage(null, null, row.avatarDrawable, null);
                    }
                }
            } catch (Throwable e) {
                row.avatarDrawable.setInfo(0L, displayName, "");
                row.avatarView.setImage(null, null, row.avatarDrawable, null);
            }

            // Set message text
            row.title.setText(dm.text != null ? dm.text : "");

            // Set subtitle (who deleted)
            row.subtitle.setText(dm.outgoing ? "Ты удалил" : "Собеседник удалил");

            // Set time
            if (dm.date > 0) {
                row.timeView.setText(sdf.format(new Date((long) dm.date * 1000)));
            } else {
                row.timeView.setText("");
            }

            // Set media badge
            if (dm.mediaType != null && !dm.mediaType.isEmpty()) {
                row.mediaBadge.setVisibility(View.VISIBLE);
                switch (dm.mediaType) {
                    case "sticker": row.mediaBadge.setText("СТИКЕР"); break;
                    case "photo": row.mediaBadge.setText("ФОТО"); break;
                    case "video": row.mediaBadge.setText("ВИДЕО"); break;
                    case "voice": row.mediaBadge.setText("ГОЛОС"); break;
                    case "video_note": row.mediaBadge.setText("КРУЖОК"); break;
                    case "gif": row.mediaBadge.setText("GIF"); break;
                    case "document": row.mediaBadge.setText("ФАЙЛ"); break;
                    default: row.mediaBadge.setVisibility(View.GONE); break;
                }
            } else {
                row.mediaBadge.setVisibility(View.GONE);
            }
        }

        @Override
        public int getItemCount() {
            return deletedMessages.size();
        }
    }
}
