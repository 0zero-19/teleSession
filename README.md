# Telegram Session Manager (Android)

Минималистичный клиент исключительно для авторизации в Telegram и управления активными сессиями аккаунта.

## 🎯 Реализованный функционал

1. **Авторизация в Telegram (MTProto / TDLib)**
   - Вход по номеру телефона (с автоформатированием и поддержкой международного кода).
   - Подтверждение 5-значным кодом авторизации из Telegram / SMS.
   - Поддержка двухфакторной аутентификации (облачный пароль 2FA) с отображением подсказки пароля.
   - Вход через сканирование QR-кода (`RequestQrCodeAuthentication`).
   - Автоматическая обработка ошибок Telegram (`PHONE_NUMBER_INVALID`, `PHONE_CODE_INVALID`, `PASSWORD_HASH_INVALID`, `FLOOD_WAIT_X`).
   - Безопасное хранение ключей базы данных и параметров в Android `EncryptedSharedPreferences` / Hardware Keystore.

2. **Управление сессиями**
   - Получение списка всех активных сессий через официальный TDLib метод `GetActiveSessions`.
   - Отображение для каждой сессии:
     - Модель устройства, ОС и платформа.
     - Название и версия приложения, бейдж официального клиента.
     - IP-адрес и геолокация (город / страна).
     - Время последней активности (относительное: «Только что», «10 мин назад», «Вчера») и точная дата входа.
     - Индикаторы статуса (2FA Pending, Unconfirmed).
   - Отдельная выделенная карточка текущего устройства («This Device»).
   - Завершение выбранной сессии с подтверждением (`TerminateSession`).
   - Завершение всех остальных сессий в один клик с защитой от случайного нажатия (`TerminateAllOtherSessions`).
   - Pull-to-refresh (SwipeRefreshLayout) и кнопка мгновенного обновления.
   - Выход из аккаунта (Log Out) с очисткой локальных данных.

3. **Создание дополнительных сессий**
   - Авторизация собственного аккаунта на другом устройстве через официальный механизм Telegram QR-логина (`ConfirmQrCodeAuthentication`).
   - Встроенный сканер QR-кода на базе камеры (ZXing Embedded).
   - Диалог ручного ввода ссылки (`tg://login?token=...`) для удобства и тестирования.

4. **Ограничения интерфейса и дизайн**
   - **Строгое отсутствие** чатов, контактов, каналов, звонков, сообщений.
   - Только экран авторизации (`AuthActivity`) и экран управления сессиями (`SessionsActivity`).
   - Современная премиальная тёмная тема (глубокие оттенки `#0F172A`, `#1E293B`, Telegram Cyan `#24A1DE`, изумрудный `#10B981`, предупреждающий `#EF4444`).
   - Удобно адаптировано для управления одной рукой на смартфонах.

---

## 🛠 Стек технологий

- **Язык**: Java 17
- **Среда**: Android Studio, Gradle 9.1.0, Android Gradle Plugin 8.13.2
- **Telegram MTProto Library**: TDLib (`io.github.tdlib-android:core:0.1.1`)
  - Включает нативные C++ библиотеки JNI (`libtdjni.so`) для всех 4-х архитектур: `arm64-v8a`, `armeabi-v7a`, `x86_64`, `x86`.
- **Шифрование**: `androidx.security:security-crypto` (AES-256 GCM + SIV)
- **UI**: Material Components 3 (MaterialCardView, TextInputLayout, MaterialToolbar, SwipeRefreshLayout)
- **QR**: ZXing Android Embedded 4.3.0

---

## 📁 Структура проекта

```
z:/telegramSessionManager/
├── app/
│   ├── src/main/
│   │   ├── AndroidManifest.xml
│   │   ├── java/com/telegram/sessionmanager/
│   │   │   ├── MainActivity.java               // Маршрутизатор (Splash / проверка сессии)
│   │   │   ├── telegram/
│   │   │   │   └── TelegramManager.java        // Управление TDLib клиентом, колбэки, сессии
│   │   │   ├── ui/
│   │   │   │   ├── AuthActivity.java           // Экран авторизации (Телефон / Код / 2FA / QR)
│   │   │   │   ├── SessionsActivity.java       // Экран сессий и авторизации устройств
│   │   │   │   └── SessionAdapter.java         // RecyclerView адаптер карточек сессий
│   │   │   └── util/
│   │   │       ├── QrHelper.java               // Генерация и парсинг QR-кодов
│   │   │       ├── SecurityHelper.java         // Безопасное хранилище и ключ БД TDLib
│   │   │       └── TimeHelper.java             // Форматирование дат и времени активности
│   │   └── res/
│   │       ├── layout/                         // XML разметка экранов и диалогов
│   │       ├── drawable/                       // Векторные иконки устройств, QR, логотипа
│   │       └── values/                         // Цвета, темы и строки
│   └── build.gradle
├── gradle/
├── gradlew.bat
├── settings.gradle
└── local.properties
```

---

## 🚀 Сборка и установка APK

### Готовый APK:
`app/build/outputs/apk/debug/app-debug.apk`

### Установка на устройство или эмулятор через ADB:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Сборка через Gradle:
```bash
.\gradlew.bat assembleDebug
```
