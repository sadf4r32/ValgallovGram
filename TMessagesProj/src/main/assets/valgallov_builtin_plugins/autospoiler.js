// @name        Auto-spoiler
// @author      sadf4r32
// @version     1.0
// @description Всё что ты пишешь превращается в ||спойлер|| (кроме команд бота)

function onLoad() {
    Valgallov.toast("autospoiler активирован");
}

function onSend(dialogId, text) {
    if (!text || text.charAt(0) === '/') return null;
    Valgallov.sendSpoiler(dialogId, text);
    return Valgallov.CANCEL;
}
