1. Документация (README.md для шаблона плагина)
# RuVideoHub Plugin Template

Шаблон для создания сторонних видео- и каталоговых плагинов для **RuVideoHub**.

Плагины компилируются в нативные `.dex` файлы и упаковываются в контейнеры `.rph`, которые хост подгружает на лету.

---

## 🚀 Быстрый старт

1. **Форкните** этот репозиторий.
2. Откройте проект в **Android Studio**.
3. Реализуйте интерфейс `VideoPlugin` (или `CatalogPlugin`, если у вашего источника есть каталог и сериалы) в своём классе:
   - Пример смотрите в `src/main/kotlin/com/example/plugins/myplugin/MyPlugin.kt`.
4. Настройте метаданные в `src/main/resources/plugin.json`.
5. Соберите релиз и упакуйте в `.rph` с помощью скрипта:
   ```bash
   chmod +x scripts/build-rph.sh
   ./scripts/build-rph.sh

📦 Структура плагина
 * src/main/kotlin/... — ваш код на Kotlin. Используйте зависимость :plugin-api строго как compileOnly, чтобы не тащить лишний вес и избежать конфликтов классов с хостом.
 * src/main/resources/plugin.json — манифест с метаданными плагина.
 * scripts/build-rph.sh — автоматический скрипт сборки .aar \rightarrow .dex \rightarrow .rph.
🛠️ Контракт (plugin.json)
Пример манифеста:
{
  "id": "com.example.plugins.myplugin",
  "name": "MySource",
  "version": "1.0.0",
  "apiVersion": 1,
  "minHostVersion": "3.7.0",
  "author": "YourName",
  "description": "Описание вашего источника",
  "entryClass": "com.example.plugins.myplugin.MyPlugin",
  "domains": ["mysource.com"]
}

💡 Важные правила
 * Никакого прямого доступа к хосту — вся работа с сетью идет через переданный в attach(context) объект PluginContext.http.
 * Изоляция зависимостей — используйте стандартные библиотеки Kotlin, корутины и org.json (доступны в рантайме Android). Не включайте тяжелые сторонние либы в тело плагина.

---

### 2. Скрипт сборки (`scripts/build-rph.sh`)

Положи этот скрипт в папку `scripts/` шаблона — он сам соберёт проект, вытащит классы, скомпилирует в `.dex` через `d8` и упакует в готовый `.rph`:

```bash
#!/usr/bin/env bash
set -e

echo ">>> Building plugin AAR..."
./gradlew :assembleRelease

AAR=$(find build/outputs/aar -name "*.aar" | head -1)
if [ -z "$AAR" ]; then
    echo "❌ Error: AAR file not found!"
    exit 1
fi

TMP=$(mktemp -d)
echo ">>> Unpacking AAR in $TMP..."
unzip -q "$AAR" -d "$TMP"

if [ -z "$ANDROID_HOME" ]; then
    echo "❌ Error: ANDROID_HOME is not set!"
    exit 1
fi

# Ищем актуальный build-tools
BUILD_TOOLS_DIR=$(find "$ANDROID_HOME/build-tools" -maxdepth 1 -mindepth 1 | sort -V | tail -n 1)
D8_EXEC="$BUILD_TOOLS_DIR/d8"

if [ ! -f "$D8_EXEC" ]; then
    echo "❌ Error: d8 compiler not found in $BUILD_TOOLS_DIR"
    exit 1
fi

echo ">>> Converting classes.jar to classes.dex using D8..."
"$D8_EXEC" --release --min-api 24 --output "$TMP" "$TMP/classes.jar"

# Собираем .rph контейнер (ZIP-архив)
RPH_DIR="$TMP/rph_build/META-INF"
mkdir -p "$RPH_DIR"

cp "$TMP/classes.dex" "$TMP/rph_build/"
cp src/main/resources/plugin.json "$TMP/rph_build/"
[ -f src/main/resources/icon.png ] && cp src/main/resources/icon.png "$TMP/rph_build/"

VERSION=$(grep -o '"version"[[:space:]]*:[[:space:]]*"[^"]*"' src/main/resources/plugin.json | head -1 | cut -d'"' -f4)
PLUGIN_ID=$(basename "$PWD")
OUTPUT_NAME="${PLUGIN_ID}-${VERSION}.rph"

echo ">>> Packaging into $OUTPUT_NAME..."
cd "$TMP/rph_build"
zip -qr "../$OUTPUT_NAME" .
cd - > /dev/null

mv "$TMP/rph_build/$OUTPUT_NAME" .
rm -rf "$TMP"

echo "✅ SUCCESS: Plugin container built -> $OUTPUT_NAME"

