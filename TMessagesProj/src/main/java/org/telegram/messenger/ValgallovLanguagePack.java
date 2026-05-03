package org.telegram.messenger;

import java.util.HashMap;
import java.util.Map;

/**
 * "Язык Вальгаллы" — Norse-flavored Russian overlay applied on top of the
 * standard Russian locale. Activated via SharedConfig.valgallovLanguageEnabled.
 *
 * Only operates when the active system / app locale is Russian — for any other
 * locale we leave Telegram's strings untouched.
 */
public final class ValgallovLanguagePack {

    private ValgallovLanguagePack() {
    }

    private static final HashMap<String, String> OVERRIDES = new HashMap<>();

    static {
        // Presence / activity
        OVERRIDES.put("Online", "На пиру");
        OVERRIDES.put("ChatYourSelfName", "Кубок мёда");
        OVERRIDES.put("SavedMessages", "Кубок мёда");
        OVERRIDES.put("LastSeenFormatted", "В путешествии %1$s");
        OVERRIDES.put("Recently", "недавно из похода");
        OVERRIDES.put("LastSeenRecently", "недавно из похода");
        OVERRIDES.put("WithinAWeek", "был в походе на этой неделе");
        OVERRIDES.put("WithinAMonth", "был в походе в этом месяце");
        OVERRIDES.put("ALongTimeAgo", "давно ушёл в Вальгаллу");

        // Typing / actions
        OVERRIDES.put("Typing", "точит меч…");
        OVERRIDES.put("IsTyping", "точит меч…");
        OVERRIDES.put("RecordingAudio", "поёт песнь скальда…");
        OVERRIDES.put("RecordingVideoMessage", "записывает руны…");
        OVERRIDES.put("SendingPhoto", "отправляет ворона…");
        OVERRIDES.put("SendingFile", "снаряжает обоз…");
        OVERRIDES.put("SendingVideoStatus", "вестник в пути…");
        OVERRIDES.put("SelectingSticker", "выбирает оберег…");

        // Group events
        OVERRIDES.put("ActionAddUser", "ввёл %1$s в братство");
        OVERRIDES.put("ActionLeftUser", "%1$s покинул чертог");
        OVERRIDES.put("ActionInviteUser", "%1$s вошёл в братство");
        OVERRIDES.put("ActionUserJoined", "вошёл в братство");
        OVERRIDES.put("ActionGroupCallStarted", "начал тинг");
        OVERRIDES.put("ActionGroupCallEnded", "закрыл тинг");

        // Message states
        OVERRIDES.put("EditedMessage", "переписано");
        OVERRIDES.put("ForwardedMessage", "Эхо");
        OVERRIDES.put("ForwardedFrom", "Эхо от");
        OVERRIDES.put("DeletedMessage", "ᚺ");
        OVERRIDES.put("Edit", "Переписать");
        OVERRIDES.put("Delete", "Стереть");
        OVERRIDES.put("Reply", "Ответить");
        OVERRIDES.put("Forward", "Послать эхо");
        OVERRIDES.put("Pin", "Прибить к мачте");
        OVERRIDES.put("Unpin", "Снять с мачты");

        // Account states
        OVERRIDES.put("HiddenName", "Безымянный");
        OVERRIDES.put("HiddenLastName", "");
        OVERRIDES.put("FromYou", "От тебя");
        OVERRIDES.put("DELETED", "Павший воин");
        OVERRIDES.put("HiddenSender", "Безымянный воин");

        // Voice / music
        OVERRIDES.put("AttachAudio", "Песнь скальда");
        OVERRIDES.put("AttachVoice", "Песнь скальда");
        OVERRIDES.put("AttachVideo", "Видение");
        OVERRIDES.put("AttachPhoto", "Изображение");
        OVERRIDES.put("AttachRound", "Круглое видение");

        // Calls
        OVERRIDES.put("CallMessageIncoming", "Зов");
        OVERRIDES.put("CallMessageOutgoing", "Зов");
        OVERRIDES.put("CallMessageIncomingMissed", "Зов пропущен");
        OVERRIDES.put("VoipBusy", "Воин занят");
        OVERRIDES.put("VoipFailed", "Зов оборвался");

        // Other
        OVERRIDES.put("Members", "Воины");
        OVERRIDES.put("Subscribers", "Послушники");
        OVERRIDES.put("Channel", "Чертог");
        OVERRIDES.put("Channels", "Чертоги");
        OVERRIDES.put("Groups", "Братства");
        OVERRIDES.put("Bots", "Тролли");
        OVERRIDES.put("Search", "Найти руну");
        OVERRIDES.put("ChatHistory", "Свиток");
        OVERRIDES.put("Done", "Готово");
        OVERRIDES.put("Cancel", "Отменить");
        OVERRIDES.put("Yes", "Да");
        OVERRIDES.put("No", "Нет");
    }

    /** Returns the override for `key` or null if no override applies. */
    public static String tryOverride(String key) {
        if (key == null) return null;
        if (!SharedConfig.valgallovLanguageEnabled) return null;
        // Only apply when the user is in Russian locale.
        try {
            LocaleController.LocaleInfo current = LocaleController.getInstance().getCurrentLocaleInfo();
            if (current == null) return null;
            String langCode = current.shortName != null ? current.shortName.toLowerCase() : "";
            if (!"ru".equals(langCode) && !"ru_ru".equals(langCode)) {
                return null;
            }
        } catch (Throwable ignore) {
            return null;
        }
        return OVERRIDES.get(key);
    }
}
