// Rate-limit: не даёт отправить одно и то же сообщение два раза подряд в один чат.
// Использует persistent storage, так что блок сохраняется между перезапусками приложения.

function onSend(dialogId, text) {
    var key = "last_" + dialogId;
    var last = Valgallov.storage.get(key);
    if (last === text) {
        Valgallov.toast("Ты уже это отправлял");
        return Valgallov.CANCEL;
    }
    Valgallov.storage.set(key, text);
    return null;
}
