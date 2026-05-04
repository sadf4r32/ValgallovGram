# ValgallovGram plugin examples

Скопируй `.js` файл отсюда в папку `Downloads/valgallov_plugins/` на телефоне,
включи «Плагины (JavaScript)» в Settings → ValgallovGram и нажми «Перезагрузить плагины».

## Доступный API

| Метод | Описание |
|---|---|
| `Valgallov.log(msg)` | Запись в logcat |
| `Valgallov.toast(msg)` | Всплывашка в UI |
| `Valgallov.sendMessage(dialogId, text)` | Отправить обычный текст |
| `Valgallov.sendSpoiler(dialogId, text)` | Отправить как `||spoiler||` |
| `Valgallov.sendBold(dialogId, text)` | Жирный |
| `Valgallov.sendItalic(dialogId, text)` | Курсив |
| `Valgallov.sendCode(dialogId, text)` | `code` (инлайн) |
| `Valgallov.sendMono(dialogId, text)` | моно-блок |
| `Valgallov.sendStrike(dialogId, text)` | ~~перечёркнутый~~ |
| `Valgallov.sendUnderline(dialogId, text)` | подчёркнутый |
| `Valgallov.deleteMessage(dialogId, msgId)` | Удалить сообщение |
| `Valgallov.getSelfId()` | Свой user ID |
| `Valgallov.getMyUsername()` | Свой @username |
| `Valgallov.getDialogTitle(dialogId)` | Название чата |
| `Valgallov.storage.get(key)` | Прочитать из стораджа |
| `Valgallov.storage.set(key, value)` | Записать в сторадж |
| `Valgallov.storage.remove(key)` | Удалить ключ |
| `Valgallov.CANCEL` | Константа для отмены отправки |

## Хуки

```js
function onLoad()                         { /* при запуске */ }
function onSend(dialogId, text)           { /* при отправке; вернуть строку = заменить, вернуть Valgallov.CANCEL = отменить, null = не трогать */ }
```
