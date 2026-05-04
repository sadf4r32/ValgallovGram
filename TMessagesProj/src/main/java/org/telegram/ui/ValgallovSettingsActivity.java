/*
 * Valgallov Settings Activity
 * Provides UI toggles for Ghost Mode and other Valgallov features.
 */

package org.telegram.ui;

import android.content.Context;
import android.view.View;
import android.widget.FrameLayout;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.HeaderCell;
import org.telegram.ui.Cells.ShadowSectionCell;
import org.telegram.ui.Cells.TextCheckCell;
import org.telegram.ui.Cells.TextInfoPrivacyCell;
import org.telegram.ui.Cells.TextSettingsCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

public class ValgallovSettingsActivity extends BaseFragment {

    private RecyclerListView listView;
    private ListAdapter listAdapter;

    private int rowCount;

    private int ghostHeaderRow;
    private int ghostMasterRow;
    private int ghostNoReadRow;
    private int ghostNoTypingRow;
    private int ghostAlwaysOfflineRow;
    private int ghostStoriesNoTraceRow;
    private int ghostNoVoiceListenedRow;
    private int ghostInfoRow;

    private int extraHeaderRow;
    private int showDeletedRow;
    private int saveRestrictedRow;
    private int blueThemeRow;
    private int noContactsSyncRow;
    private int valgallovLanguageRow;
    private int hiddenChatsRow;
    private int manageHiddenChatsRow;
    private int editHistoryRow;
    private int themePresetRow;
    private int showSecondsRow;
    private int antiScreenRecordRow;
    private int quickMuteRow;
    private int deletedHistoryRow;
    private int extraInfoRow;

    @Override
    public boolean onFragmentCreate() {
        super.onFragmentCreate();
        updateRows();
        return true;
    }

    private void updateRows() {
        rowCount = 0;
        ghostHeaderRow = rowCount++;
        ghostMasterRow = rowCount++;
        ghostNoReadRow = rowCount++;
        ghostNoTypingRow = rowCount++;
        ghostAlwaysOfflineRow = rowCount++;
        ghostStoriesNoTraceRow = rowCount++;
        ghostNoVoiceListenedRow = rowCount++;
        ghostInfoRow = rowCount++;

        extraHeaderRow = rowCount++;
        showDeletedRow = rowCount++;
        saveRestrictedRow = rowCount++;
        blueThemeRow = rowCount++;
        noContactsSyncRow = rowCount++;
        valgallovLanguageRow = rowCount++;
        hiddenChatsRow = rowCount++;
        manageHiddenChatsRow = SharedConfig.valgallovHiddenChatsEnabled ? rowCount++ : -1;
        editHistoryRow = rowCount++;
        themePresetRow = rowCount++;
        showSecondsRow = rowCount++;
        antiScreenRecordRow = rowCount++;
        quickMuteRow = rowCount++;
        deletedHistoryRow = rowCount++;
        extraInfoRow = rowCount++;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("ValgallovGram");
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                }
            }
        });

        fragmentView = new FrameLayout(context);
        FrameLayout frameLayout = (FrameLayout) fragmentView;
        frameLayout.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));

        listView = new RecyclerListView(context);
        listView.setLayoutManager(new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false));
        listView.setVerticalScrollBarEnabled(false);
        listView.setAdapter(listAdapter = new ListAdapter(context));
        frameLayout.addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        listView.setOnItemClickListener((view, position) -> {
            boolean changed = false;
            boolean newValue = false;

            if (position == ghostMasterRow) {
                SharedConfig.toggleGhostMode();
                newValue = SharedConfig.ghostMode;
                changed = true;
            } else if (position == ghostNoReadRow) {
                SharedConfig.toggleGhostNoRead();
                newValue = SharedConfig.ghostNoRead;
                changed = true;
            } else if (position == ghostNoTypingRow) {
                SharedConfig.toggleGhostNoTyping();
                newValue = SharedConfig.ghostNoTyping;
                changed = true;
            } else if (position == ghostAlwaysOfflineRow) {
                SharedConfig.toggleGhostAlwaysOffline();
                newValue = SharedConfig.ghostAlwaysOffline;
                changed = true;
            } else if (position == ghostStoriesNoTraceRow) {
                SharedConfig.toggleGhostStoriesNoTrace();
                newValue = SharedConfig.ghostStoriesNoTrace;
                changed = true;
            } else if (position == ghostNoVoiceListenedRow) {
                SharedConfig.toggleGhostNoVoiceListened();
                newValue = SharedConfig.ghostNoVoiceListened;
                changed = true;
            } else if (position == showDeletedRow) {
                SharedConfig.toggleValgallovShowDeleted();
                newValue = SharedConfig.valgallovShowDeleted;
                changed = true;
            } else if (position == saveRestrictedRow) {
                SharedConfig.toggleValgallovSaveRestricted();
                newValue = SharedConfig.valgallovSaveRestricted;
                changed = true;
            } else if (position == blueThemeRow) {
                SharedConfig.toggleValgallovBlueThemeDefault();
                newValue = SharedConfig.valgallovBlueThemeDefault;
                changed = true;
                try {
                    if (newValue) {
                        org.telegram.messenger.ValgallovThemeBootstrap.applyValgallovTheme();
                    } else {
                        Theme.ThemeInfo themeInfo = Theme.getTheme("Blue");
                        if (themeInfo != null) {
                            Theme.applyTheme(themeInfo);
                        }
                    }
                    org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.didSetNewTheme, false, false);
                } catch (Throwable ignore) {
                }
            } else if (position == noContactsSyncRow) {
                SharedConfig.toggleValgallovNoContactsSync();
                newValue = SharedConfig.valgallovNoContactsSync;
                changed = true;
                // Apply immediately to the active account so future contact uploads stop
                try {
                    org.telegram.messenger.UserConfig uc = org.telegram.messenger.UserConfig.getInstance(getCurrentAccount());
                    uc.syncContacts = !newValue;
                    uc.saveConfig(false);
                } catch (Throwable ignore) {
                }
            } else if (position == valgallovLanguageRow) {
                SharedConfig.toggleValgallovLanguageEnabled();
                newValue = SharedConfig.valgallovLanguageEnabled;
                changed = true;
                try {
                    org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(org.telegram.messenger.NotificationCenter.reloadInterface);
                } catch (Throwable ignore) {
                }
            } else if (position == hiddenChatsRow) {
                SharedConfig.toggleValgallovHiddenChatsEnabled();
                newValue = SharedConfig.valgallovHiddenChatsEnabled;
                changed = true;
                updateRows();
                if (listAdapter != null) listAdapter.notifyDataSetChanged();
            } else if (manageHiddenChatsRow != -1 && position == manageHiddenChatsRow) {
                try {
                    if (getParentActivity() instanceof LaunchActivity) {
                        ValgallovHiddenChatsActivity.promptAndOpen((LaunchActivity) getParentActivity(), this);
                    } else {
                        org.telegram.messenger.ValgallovHiddenChatsTracker.markUnlocked();
                        presentFragment(new ValgallovHiddenChatsActivity());
                    }
                } catch (Throwable ignore) {
                }
                return;
            } else if (position == editHistoryRow) {
                SharedConfig.toggleValgallovEditHistoryEnabled();
                newValue = SharedConfig.valgallovEditHistoryEnabled;
                changed = true;
            } else if (position == themePresetRow) {
                int next = (SharedConfig.valgallovThemePreset + 1) % 4;
                org.telegram.messenger.ValgallovThemePresets.apply(next);
                String themeName = org.telegram.messenger.ValgallovThemePresets.presetName(next);
                try {
                    android.widget.Toast.makeText(getParentActivity(), "Тема: " + themeName, android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable ignore) {}
                try {
                    org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
                        org.telegram.messenger.NotificationCenter.didSetNewTheme, false, false);
                    org.telegram.messenger.NotificationCenter.getGlobalInstance().postNotificationName(
                        org.telegram.messenger.NotificationCenter.reloadInterface);
                } catch (Throwable ignore) {}
                if (getParentActivity() != null) {
                    getParentActivity().recreate();
                }
                return;
            } else if (position == showSecondsRow) {
                SharedConfig.toggleValgallovShowSeconds();
                newValue = SharedConfig.valgallovShowSeconds;
                changed = true;
            } else if (position == antiScreenRecordRow) {
                SharedConfig.toggleValgallovAntiScreenRecord();
                newValue = SharedConfig.valgallovAntiScreenRecord;
                changed = true;
            } else if (position == quickMuteRow) {
                // Quick mute all dialogs for 2 hours
                try {
                    org.telegram.messenger.MessagesController mc = org.telegram.messenger.MessagesController.getInstance(getCurrentAccount());
                    java.util.ArrayList<org.telegram.tgnet.TLRPC.Dialog> allDialogs = mc.getAllDialogs();
                    int count = 0;
                    for (org.telegram.tgnet.TLRPC.Dialog d : allDialogs) {
                        if (d == null) continue;
                        if (mc.isDialogMuted(d.id, 0)) continue;
                        org.telegram.messenger.NotificationsController.getInstance(getCurrentAccount()).muteDialog(d.id, 0, true);
                        count++;
                    }
                    android.widget.Toast.makeText(getParentActivity(), "Замучено: " + count + " чатов на 2 часа", android.widget.Toast.LENGTH_SHORT).show();
                } catch (Throwable e) {
                    android.widget.Toast.makeText(getParentActivity(), "Ошибка: " + e.getMessage(), android.widget.Toast.LENGTH_SHORT).show();
                }
                return;
            } else if (position == deletedHistoryRow) {
                presentFragment(new ValgallovDeletedHistoryActivity(0));
                return;
            }

            if (changed && view instanceof TextCheckCell) {
                ((TextCheckCell) view).setChecked(newValue);
                if (position == ghostMasterRow) {
                    listAdapter.notifyDataSetChanged();
                }
            }
        });

        return fragmentView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (listAdapter != null) {
            listAdapter.notifyDataSetChanged();
        }
    }

    private class ListAdapter extends RecyclerListView.SelectionAdapter {

        private static final int TYPE_HEADER = 0;
        private static final int TYPE_CHECK = 1;
        private static final int TYPE_INFO = 2;
        private static final int TYPE_SHADOW = 3;
        private static final int TYPE_SETTINGS = 4;

        private final Context mContext;

        public ListAdapter(Context context) {
            mContext = context;
        }

        @Override
        public int getItemCount() {
            return rowCount;
        }

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            int type = holder.getItemViewType();
            if (type == TYPE_CHECK) {
                int position = holder.getAdapterPosition();
                if (position == ghostMasterRow) {
                    return true;
                }
                // sub-toggles disabled when master is off
                if (position == ghostNoReadRow || position == ghostNoTypingRow ||
                    position == ghostAlwaysOfflineRow || position == ghostStoriesNoTraceRow ||
                    position == ghostNoVoiceListenedRow) {
                    return SharedConfig.ghostMode;
                }
                return true;
            }
            if (type == TYPE_SETTINGS) return true;
            return false;
        }

        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(android.view.ViewGroup parent, int viewType) {
            View view;
            switch (viewType) {
                case TYPE_HEADER:
                    view = new HeaderCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_CHECK:
                    view = new TextCheckCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_INFO:
                    view = new TextInfoPrivacyCell(mContext);
                    break;
                case TYPE_SETTINGS:
                    view = new TextSettingsCell(mContext);
                    view.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundWhite));
                    break;
                case TYPE_SHADOW:
                default:
                    view = new ShadowSectionCell(mContext);
                    break;
            }
            view.setLayoutParams(new RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, RecyclerView.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(view);
        }

        @Override
        public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
            switch (holder.getItemViewType()) {
                case TYPE_HEADER: {
                    HeaderCell cell = (HeaderCell) holder.itemView;
                    if (position == ghostHeaderRow) {
                        cell.setText("Режим Призрака");
                    } else if (position == extraHeaderRow) {
                        cell.setText("Дополнительные функции");
                    }
                    break;
                }
                case TYPE_CHECK: {
                    TextCheckCell cell = (TextCheckCell) holder.itemView;
                    boolean enabled = SharedConfig.ghostMode;
                    cell.setEnabled(true, null);

                    if (position == ghostMasterRow) {
                        cell.setTextAndCheck("Включить режим Призрака", SharedConfig.ghostMode, true);
                        cell.setEnabled(true, null);
                    } else if (position == ghostNoReadRow) {
                        cell.setTextAndValueAndCheck("Не помечать прочитанным",
                            "Сообщения остаются непрочитанными у отправителя",
                            SharedConfig.ghostNoRead, true, true);
                        cell.setEnabled(enabled, null);
                    } else if (position == ghostNoTypingRow) {
                        cell.setTextAndValueAndCheck("Не показывать «печатает»",
                            "Не отправлять индикатор набора текста",
                            SharedConfig.ghostNoTyping, true, true);
                        cell.setEnabled(enabled, null);
                    } else if (position == ghostAlwaysOfflineRow) {
                        cell.setTextAndValueAndCheck("Всегда оффлайн",
                            "Last seen всегда показывает «давно»",
                            SharedConfig.ghostAlwaysOffline, true, true);
                        cell.setEnabled(enabled, null);
                    } else if (position == ghostStoriesNoTraceRow) {
                        cell.setTextAndValueAndCheck("Stories без следа",
                            "Не появляться в списке зрителей",
                            SharedConfig.ghostStoriesNoTrace, true, true);
                        cell.setEnabled(enabled, null);
                    } else if (position == ghostNoVoiceListenedRow) {
                        cell.setTextAndValueAndCheck("Голосовые без «прослушано»",
                            "Не отмечать voice/video как прослушанные",
                            SharedConfig.ghostNoVoiceListened, false, true);
                        cell.setEnabled(enabled, null);
                    } else if (position == showDeletedRow) {
                        cell.setTextAndValueAndCheck("Показывать удалённые",
                            "Не удалять сообщения из локальной базы при удалении на сервере",
                            SharedConfig.valgallovShowDeleted, true, true);
                    } else if (position == saveRestrictedRow) {
                        cell.setTextAndValueAndCheck("Сохранять защищённые медиа",
                            "Скачивать медиа из каналов с запретом на сохранение",
                            SharedConfig.valgallovSaveRestricted, true, true);
                    } else if (position == blueThemeRow) {
                        cell.setTextAndValueAndCheck("Тёмная тема",
                            "Применить ночную тёмно-синюю тему сразу",
                            SharedConfig.valgallovBlueThemeDefault, true, true);
                    } else if (position == noContactsSyncRow) {
                        cell.setTextAndValueAndCheck("Не выгружать контакты",
                            "Не отправлять записную книжку телефона на серверы",
                            SharedConfig.valgallovNoContactsSync, true, true);
                    } else if (position == valgallovLanguageRow) {
                        cell.setTextAndValueAndCheck("Язык Вальгаллы",
                            "Online → На пиру, Saved Messages → Кубок мёда и т.д.",
                            SharedConfig.valgallovLanguageEnabled, true, true);
                    } else if (position == hiddenChatsRow) {
                        cell.setTextAndValueAndCheck("Тайные чертоги",
                            "Скрытые чаты с защитой по пин-коду / биометрии",
                            SharedConfig.valgallovHiddenChatsEnabled, true, true);
                    } else if (position == editHistoryRow) {
                        cell.setTextAndValueAndCheck("История переписанного",
                            "Сохранять предыдущие версии редактированных сообщений",
                            SharedConfig.valgallovEditHistoryEnabled, true, true);
                    } else if (position == showSecondsRow) {
                        cell.setTextAndValueAndCheck("Секунды в часах",
                            "Показывать HH:MM:SS вместо HH:MM",
                            SharedConfig.valgallovShowSeconds, true, true);
                    } else if (position == antiScreenRecordRow) {
                        cell.setTextAndValueAndCheck("Детектор записи экрана",
                            "Уведомлять при записи экрана (API 34+)",
                            SharedConfig.valgallovAntiScreenRecord, false, true);
                    }
                    break;
                }
                case TYPE_SETTINGS: {
                    TextSettingsCell cell = (TextSettingsCell) holder.itemView;
                    if (manageHiddenChatsRow != -1 && position == manageHiddenChatsRow) {
                        int n = org.telegram.messenger.ValgallovHiddenChatsTracker.count();
                        cell.setTextAndValue("Управлять Тайными Чертогами",
                            n == 0 ? "ничего не скрыто" : (n + " чат(ов) скрыто"), true);
                    } else if (position == themePresetRow) {
                        cell.setTextAndValue("Тема Чертога",
                            org.telegram.messenger.ValgallovThemePresets.presetName(SharedConfig.valgallovThemePreset), true);
                    } else if (position == quickMuteRow) {
                        cell.setTextAndValue("Замутить все чаты",
                            "Отключить уведомления для всех диалогов", true);
                    } else if (position == deletedHistoryRow) {
                        cell.setTextAndValue("Вся история стёртого",
                            "Удалённые сообщения из всех чатов", false);
                    }
                    break;
                }
                case TYPE_INFO: {
                    TextInfoPrivacyCell cell = (TextInfoPrivacyCell) holder.itemView;
                    if (position == ghostInfoRow) {
                        cell.setText("Эти настройки не отправляют определённые метаданные на серверы Telegram. Все изменения работают только на стороне клиента.");
                    } else if (position == extraInfoRow) {
                        cell.setText("Удалённые сообщения помечаются «удалено» и остаются в чате. Защищённые медиа можно сохранять и пересылать. Контакты не выгружаются на серверы Telegram.");
                    }
                    break;
                }
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (position == ghostHeaderRow || position == extraHeaderRow) {
                return TYPE_HEADER;
            } else if (position == ghostInfoRow || position == extraInfoRow) {
                return TYPE_INFO;
            } else if ((manageHiddenChatsRow != -1 && position == manageHiddenChatsRow) || position == themePresetRow || position == quickMuteRow || position == deletedHistoryRow) {
                return TYPE_SETTINGS;
            } else {
                return TYPE_CHECK;
            }
        }
    }
}
