
# 🎬 RuVideoHub

**Неофициальный open-source клиент для просмотра видео**

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-blue)
![Compose](https://img.shields.io/badge/Jetpack%20Compose-1.7+-green)

RuVideoHub — это альтернативный клиент Rutube, созданный с акцентом на удобство, оффлайн-функции и чистый код.

## Основные возможности

- Умный адаптивный парсер (SmartParser)
- Скачивание видео для оффлайн-просмотра
- Поддержка Android TV (D-pad навигация)
- Тёмная и светлая темы
- История просмотров и Избранное
- Локальная медиатека

## Технические особенности

**SmartParser** — собственный парсер, который:
- Автоматически определяет тип контента (видео, сериал, канал, плейлист)
- Адаптируется к изменениям в API
- Фильтрует платный контент 
- Рекурсивно ищет карточки в сложных ответах

**Архитектура:**
- Jetpack Compose + Material 3
- MVVM + Clean Architecture
- Room Database
- Media3 (ExoPlayer)
- Hilt (Dependency Injection)
- Kotlin Coroutines + Flow

## Установка

1. Скачайте последнюю сборку из [Releases](https://github.com/yourusername/ruvideohub/releases)
2. Установите APK на устройство

Или соберите проект самостоятельно:

```bash
git clone https://github.com/ByteBudda/RuVideoHub.git
cd RuVideoHub
./gradlew assembleDebug
```

## Важное

- Это **неофициальное** приложение. Мы не связаны с компанией RUTUBE и Газпром-Медиа.
- Приложение использует публичные API Rutube. Использование может противоречить их Пользовательскому соглашению.
- Распространяется бесплатно под лицензией **GNU GPL v3.0**

## Лицензия

Проект распространяется под лицензией [GNU General Public License v3.0](LICENSE).  
Вы можете свободно использовать, модифицировать и распространять код при соблюдении условий лицензии.

---
