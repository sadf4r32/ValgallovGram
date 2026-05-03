# ValgallovGram

Форк Telegram Android с фичами «Чертога Павших».

Базируется на [DrKLO/Telegram](https://github.com/DrKLO/Telegram) v12.6.4.

## Скачать APK

Свежие сборки → **[Releases](../../releases)**.

## Что внутри

### Режим Призрака (Ghost Mode)
- Не помечать сообщения прочитанными для отправителя
- Не показывать «печатает…»
- Всегда оффлайн (last seen — «давно»)
- Stories без следа (не появляться в списке зрителей)
- Голосовые без «прослушано»

### Что-то помимо
- **Показывать удалённые** — серверные удаления игнорируются, сообщение остаётся в чате с руной **ᚺ** справа в матово-стальном цвете
- **История стёртого** — отдельный экран в меню чата со списком всех удалённых в этом чате с прыжком к нужному сообщению
- **Сохранять защищённые медиа** — bypass `noforwards`, можно скачивать/пересылать из каналов с запретом
- **Тёмная тема Вальгалловграма** — кастомный `.attheme` с гунметал-палитрой и неон-голубым акцентом
- **Не выгружать контакты** — отключает автоматическую загрузку записной книжки телефона на серверы Telegram
- **Язык Вальгаллы** — оверлей русских строк в норско-готическом стиле (Online → На пиру, Saved Messages → Кубок мёда, Typing… → точит меч…)
- **Auto-subscribe** — автоматически подписывает на канал @fucktrollingh после первого логина

### Брендинг
- Кастомный валькнут-логотип на тёмно-стальном фоне
- Welcome-экран с мифологическими текстами («Чертог Павших», «Призрак», «Вечная Память», «Без Цепей», «Свой», «Братство»)
- На экране ввода номера — крупная надпись «ValgallovGram» шрифтом Pirata One
- Foreground-сервис «ValgallovGram активен в фоне» с persistent-уведомлением для надёжной работы в фоне

### Под капотом
- API id/hash: собственные
- Версия Telegram base: 12.6.4 (build 6666)
- Min SDK: 21 (Android 5.0+)
- Foreground service для keep-alive
- In-memory tracker удалённых с CSV-персистом per-dialog (cap 5000 ID на диалог)
- Tracker предыдущих версий редактированных сообщений

## Сборка из исходников

```bash
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk
export ANDROID_NDK_HOME=$HOME/android-sdk/ndk/21.4.7075529

git clone https://github.com/sadf4r32/ValgallovGram.git
cd ValgallovGram
./gradlew :TMessagesProj_App:assembleAfatDebug

# APK выйдет в:
# TMessagesProj_App/build/outputs/apk/afat/debug/app.apk
```

## Лицензия

Базовый код Telegram — GPL-2.0+ (см. [LICENSE](LICENSE)).
Изменения форка — также GPL-2.0+.
