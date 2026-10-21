# Telegram Session Manager (Android)

Минималистичный клиент исключительно для авторизации в Telegram и управления активными сессиями аккаунта.

## 🎯 Реализованный функционал

### 1. Поддержка нескольких аккаунтов (Multi-Account)
- Поддержка произвольного числа Telegram-аккаунтов внутри одного приложения.
- Переключение между аккаунтами через нижнюю шторку (Account Switcher BottomSheet).
- Кнопка «+ Добавить аккаунт» для входа в новый аккаунт по номеру телефона или QR-коду.
- Изоляция данных: каждый аккаунт имеет собственную изолированную директорию базы данных (`accounts/{accountId}/tdlib`) и постоянный ключ шифрования (`key.bin`).
- Возможность удалить отдельный аккаунт из приложения с очисткой его локальной базы данных.
- Сохранение авторизации между перезапусками приложения: `TelegramApp` (Application класс) обеспечивает долгоживущую инициализацию, а `useChatInfoDatabase = true` гарантирует сохранность данных пользователей и сессий.

### 2. Монохромный дизайн (Glassmorphism)
- Полностью монохромная эстетика: только глубокий чёрный (`#000000`), чистый белый (`#FFFFFF`) и оттенки серого.
- Полупрозрачные панели (Glassmorphism): поверхности с полупрозрачной заливкой (`#12FFFFFF`, `#1AFFFFFF`) и тонкими световыми границами (`#1FFFFFFF`, `#38FFFFFF`).
- Отсутствие ярких цветных градиентов и лишнего декора — строгий, контрастный и технологичный стиль.
- Адаптивная типографика с отличной читаемостью на экранах любых размеров.

### 3. Исправление завершения сессий и 24-часовой карантин Telegram
- **Первопричина ошибок завершения:** Telegram API блокирует вызов `TerminateSession` / `TerminateAllOtherSessions` ошибкой `406 FRESH_RESET_AUTHORISATION_FORBIDDEN`, если текущая сессия была создана менее 24 часов назад. Это встроенный механизм безопасности Telegram против захвата аккаунтов.
- **Исправление:**
  - Автоматический расчёт возраста текущей сессии (`logInDate`).
  - Отображение информационного бейджа с таймером карантина (например: «Telegram 24h protection active: Session termination available in 14h 20m»).
  - При ошибке `406` выводится подробное диалоговое окно с понятным разъяснением правил безопасности Telegram и оставшегося времени.
  - Обработка `SESSION_REVOKED` и сетевых ошибок.
  - Список сессий обновляется с сервера только после подтверждения Telegram (никакого преждевременного локального удаления).

### 4. Авторизация сторонних устройств
- Вход в аккаунт на компьютере или планшете через встроенный сканер QR-кода на базе камеры (`ConfirmQrCodeAuthentication`).
- Диалог ручного ввода ссылки (`tg://login?token=...`) для тестирования или ручной авторизации.

---

## 🛠 Стек технологий

- **Язык**: Java 17
- **Среда**: Android Studio, Gradle 9.1.0, Android Gradle Plugin 8.13.2
- **Telegram MTProto Library**: TDLib (`io.github.tdlib-android:core:0.1.1`)
  - Включает нативные C++ библиотеки JNI (`libtdjni.so`) для `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.
- **UI**: Material Components 3 (Monochrome Glassmorphism, MaterialCardView, BottomSheetDialogFragment, SwipeRefreshLayout)
- **QR**: ZXing Android Embedded 4.3.0

---

## 📁 Структура проекта

```
z:/telegramSessionManager/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/telegram/sessionmanager/
│   │   │   ├── TelegramApp.java                // Application класс для глобального управления
│   │   │   ├── MainActivity.java               // Маршрутизатор первого запуска
│   │   │   ├── account/
│   │   │   │   ├── AccountModel.java           // Модель Telegram-аккаунта
│   │   │   │   └── AccountStorage.java         // Изолированное хранилище директорий и ключей
│   │   │   ├── telegram/
│   │   │   │   ├── TelegramManager.java        // Диспетчер клиентов и аккаунтов
│   │   │   │   └── TelegramAccountClient.java  // Индивидуальный клиент TDLib для аккаунта
│   │   │   ├── ui/
│   │   │   │   ├── AuthActivity.java           // Экран авторизации (Телефон / Код / 2FA / QR)
│   │   │   │   ├── SessionsActivity.java       // Экран сессий и авторизации устройств
│   │   │   │   ├── SessionAdapter.java         // Адаптер списка сессий
│   │   │   │   ├── AccountAdapter.java         // Адаптер списка аккаунтов
│   │   │   │   └── AccountSwitchBottomSheet.java // Шторка переключения аккаунтов
│   │   │   └── util/
│   │   │       ├── QrHelper.java               // Генерация и валидация QR
│   │   │       ├── SecurityHelper.java         // Хранение параметров API ID/Hash
│   │   │       └── TimeHelper.java             // Форматирование времени и дат
│   │   └── res/
│   │       ├── layout/                         // Разметки экранов, диалогов и шторки
│   │       ├── drawable/                       // Монохромные векторные иконки и glass-фона
│   │       └── values/                         // Цветовая палитра и темы
│   └── build.gradle
├── gradle/
├── gradlew.bat
├── settings.gradle
└── local.properties
```

---

## 🚀 Сборка и запуск

### Готовый APK:
`app/build/outputs/apk/debug/app-debug.apk`

### Установка через ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
