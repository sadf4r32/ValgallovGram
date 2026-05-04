# ValgallovGram plugin examples

Скачай любой `.js` файл отсюда, положи в `Downloads/valgallov_plugins/` на телефоне,
включи **Плагины (JavaScript)** в Settings → ValgallovGram → тап в **Плагины** → включи конкретный плагин.

## Доступные плагины

| Файл | Что делает |
|---|---|
| [autospoiler.js](autospoiler.js) | Всё что ты пишешь → `\|\|spoiler\|\|` |
| [ratelimit.js](ratelimit.js) | Блокирует повторную отправку одного и того же текста |
| [typos.js](typos.js) | Заменяет «telegram» → «ValgallovGram» в твоих сообщениях |
| [translit.js](translit.js) | Порт AyuGram tr.plugin — команды `.t`/`.tr`/`.b`/`.i`/`.u`/`.s`/`.code`/`.caps`/`.alt`/`.mock`/`.rev`/`.space`/`.sp`/`.pref`/`.suf`/`.status`/`.off` |

## Метаданные плагина

Вверху файла укажи:
```js
// @name        Имя плагина
// @author      ник
// @version     1.0
// @description Короткое описание
```
Они показываются в Plugin Manager UI (Settings → ValgallovGram → Плагины).

## API

### Простая отправка
```js
Valgallov.sendMessage(dialogId, text)
```

### Форматирование (настоящие Telegram entities)
```js
Valgallov.sendSpoiler  (dialogId, text)  // ||spoiler||
Valgallov.sendBold     (dialogId, text)
Valgallov.sendItalic   (dialogId, text)
Valgallov.sendCode     (dialogId, text)  // `inline`
Valgallov.sendMono     (dialogId, text)  // ```block```
Valgallov.sendStrike   (dialogId, text)
Valgallov.sendUnderline(dialogId, text)
```

### Unicode-трансформы (возвращают новую строку, без entities)
```js
Valgallov.text.bold("hello")      // 𝐡𝐞𝐥𝐥𝐨
Valgallov.text.italic("hello")    // 𝑕𝑒𝑙𝑙𝑜
Valgallov.text.mono("hello")      // 𝚑𝚎𝚕𝚕𝚘
Valgallov.text.strike("hi")       // h̶i̶
Valgallov.text.underline("hi")    // h̲i̲
Valgallov.text.caps("hi")         // HI
Valgallov.text.alt("hello")       // hElLo
Valgallov.text.mock("hello")      // HeLlO (рандом)
Valgallov.text.reverse("hello")   // olleh
Valgallov.text.space("hi")        // h i
Valgallov.text.translit("привет") // privet
```

### Контекст
```js
Valgallov.getSelfId()              // long
Valgallov.getMyUsername()          // String
Valgallov.getDialogTitle(dialogId) // String
```

### Действия
```js
Valgallov.deleteMessage(dialogId, messageId)
Valgallov.log(msg)    // logcat
Valgallov.toast(msg)  // всплывашка
```

### Хранилище (переживает перезапуск)
```js
Valgallov.storage.set(key, value)
Valgallov.storage.get(key)
Valgallov.storage.getOrDefault(key, def)
Valgallov.storage.remove(key)
Valgallov.storage.clear()
```

### Константы
```js
Valgallov.CANCEL  // вернуть из onSend чтобы отменить отправку
```

## Хуки

```js
function onLoad() {
    // вызывается один раз при загрузке плагина
    Valgallov.toast("загружен");
}

function onSend(dialogId, text) {
    // вызывается перед КАЖДОЙ исходящей отправкой
    // return null                      → не трогать
    // return "новый текст"             → заменить
    // return Valgallov.CANCEL          → отменить (например после Valgallov.sendSpoiler)
}
```
