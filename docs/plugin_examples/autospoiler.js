// Auto-spoiler: всё что ты отправляешь автоматически становится спойлером.
// Отменяет оригинальную отправку, заменяет её на спойлер-версию с теми же буквами.

function onLoad() {
    Valgallov.toast("Плагин autospoiler активирован");
}

function onSend(dialogId, text) {
    // не трогаем команды бота
    if (text.charAt(0) === '/') return null;
    Valgallov.sendSpoiler(dialogId, text);
    return Valgallov.CANCEL;
}
