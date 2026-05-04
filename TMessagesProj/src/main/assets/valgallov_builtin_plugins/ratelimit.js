// @name        Rate-limit
// @author      sadf4r32
// @version     1.0
// @description Не даёт отправить одно и то же сообщение два раза подряд в тот же чат

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
