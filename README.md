# 🎵 Yandex Music Lite

<div align="center">

[![Android](https://img.shields.io/badge/Platform-Android%207.0%2B-3DDC84.svg?style=for-the-badge&logo=android&logoColor=white)](https://android.com)
[![Version](https://img.shields.io/badge/Version-1.2.0-FF3366.svg?style=for-the-badge)](https://github.com/KorenskoyR/YandexMusicLite/releases)
[![Size](https://img.shields.io/badge/APK%20Size-5.8%20MB-00E5FF.svg?style=for-the-badge)](https://github.com/KorenskoyR/YandexMusicLite/releases)
[![Language](https://img.shields.io/badge/Kotlin-100%25-7F52FF.svg?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4.svg?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)

**Быстрый, легковесный клиент Яндекс Музыки.**  
Создан для смартфонов, планшетов и автомагнитол на базе Android.

[📥 Скачать актуальный APK](https://github.com/KorenskoyR/YandexMusicLite/releases/latest) • [✨ Возможности](#-основные-возможности) • [🚗 Авторежим](#-режим-в-автомобиле-car-mode) • [🛠 Сборка](#-сборка-из-исходников)

</div>

---

## 🌟 Основные возможности

- 🚀 **Ультра-легковесный размер (менее 6 МБ)**: Благодаря глубокой R8 / ProGuard оптимизации размер APK уменьшен на 68% по сравнению со стандартными приложениями.
- 🎧 **Надежное фоновое воспроизведение**: Интеллектуальный `PlaybackWakeLockHelper` гарантирует беспрерывное переключение треков при выключенном экране и в режиме глубокого сна (Android Doze Mode).
- 🌊 **«Моя волна» без повторов**: Алгоритм циклической ротации исключает повторение треков при перезапусках и упреждающе кэширует следующие композиции.
- 📻 **Встроенное FM Радио**: 37+ популярных радиостанций в прямом эфире с официальными логотипами высокого разрешения и фильтрацией по жанрам.
- 🔄 **Встроенное обновление (In-App Updater)**: Приложение самостоятельно проверяет наличие новых версий на GitHub, скачивает APK с прогресс-баром и запускает обновление в один клик.
- 🎨 **Дизайн Liquid Glass (Glassmorphism)**: Глубокие атмосферные градиенты, парящие полупрозрачные панели, неоновая подсветка и мягкие микро-анимации.
- 📱 **Планшеты и ландшафтная ориентация**: Адаптивная верстка с боковым меню `NavigationRail`, двухколоночными списками и центрированным мини-плеером.
- 💾 **Мгновенный холодный старт**: Оффлайн-кэш медиатеки позволяет запускать воспроизведение за доли секунды даже при медленном соединении.

---

## 🚗 Режим «В автомобиле» (Car Mode)

Специальный интерфейс, спроектированный для Android-магнитол и комфортного использования за рулем:
- **Крупные сенсорные элементы**: Большие кнопки управления воспроизведением, удобные для нажатия в движении.
- **Плитки быстрого доступа**: Моментальный запуск «Моей волны», раздела «Любимое», «Радио», «Хитов» и «Скачанных треков».
- **Информационный виджет**: Встроенные крупные цифровые часы и текущая дата в стиле панели приборов.
- **Аппаратная совместимость**: Принудительная маршрутизация на программные декодеры Google для исключения эффекта ускорения звука («бурундука») на чипсетах Rockchip, Allwinner, MediaTek и Unisoc.

---

## 📲 Установка

1. Перейдите в раздел [**Releases**](https://github.com/KorenskoyR/YandexMusicLite/releases/latest) и скачайте файл **`YandexMusicLite.apk`**.
2. Откройте скачанный APK на вашем Android-устройстве.
3. При запросе системы разрешите установку из неизвестных источников.
4. Запустите **Yandex Music Lite** и авторизуйтесь через свой Яндекс ID.

> **💡 Подсказка:** Последующие обновления будут происходить автоматически через встроенный загрузчик в меню настроек!

---

## 🛠 Сборка из исходников

Для самостоятельной компиляции проекта потребуется:
- **JDK**: 17 или 21
- **Android SDK**: API 35 (Android 15)
- **Gradle**: 8.x (включен gradlew wrapper)

```bash
# Клонирование репозитория
git clone https://github.com/KorenskoyR/YandexMusicLite.git
cd YandexMusicLite

# Сборка релизного APK
./gradlew assembleRelease

# Готовый подписанный файл будет расположен в:
# app/build/outputs/apk/release/app-release.apk
```

---

## 🏗 Архитектура и стек технологий

- **Архитектурный паттерн**: Clean Architecture + MVVM + MVI Events
- **UI Framework**: Jetpack Compose (Material 3, Edge-to-Edge, WindowInsets)
- **Внедрение зависимостей**: Dagger Hilt
- **Аудио-движок**: AndroidX Media3 (ExoPlayer, MediaLibraryService, MediaSession)
- **Сеть**: Retrofit 2, OkHttp 4, KotlinX Serialization
- **Асинхронность**: Kotlin Coroutines & StateFlow / SharedFlow
- **Кэширование**: DataStore Preferences, OkHttp Cache, Coil Image Loader
- **Оптимизация**: R8 Compiler, ProGuard, Resource Shrinking

---

## 📄 Лицензия

Распространяется бесплатно для личного использования. Все права на товарные знаки и аудиоматериалы принадлежат их законным правообладателям.
