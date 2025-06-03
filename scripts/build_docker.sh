#!/usr/bin/env bash
#
# scripts/build_docker.sh
#
# Собирает fat-JAR проекта (shadowJar) и упаковывает его в Docker-образ.
# Использование:
#   ./scripts/build_docker.sh               # версия по умолчанию 1.0.0
#   ./scripts/build_docker.sh 1.2.3         # задать номер версии
#   ./scripts/build_docker.sh 1.2.3 my-bot  # версия + своё имя образа
#
# Требования:
#   • Docker установлен и запущен
#   • В проекте настроена задача `shadowJar` (Gradle Shadow Plugin)
#   • В корне проекта есть Dockerfile, ожидающий JAR на пути /app/app.jar

set -euo pipefail

# ──────────────────────────────── 0. Проверки ────────────────────────────────
if ! command -v docker >/dev/null 2>&1; then
  echo "❌ Docker не установлен или не в PATH. Поставьте Docker Desktop / Docker Engine."
  exit 1
fi

# ──────────────────────────────── 1. Параметры ───────────────────────────────
APP_VERSION="${1:-1.0.0}"              # первый аргумент или 1.0.0
IMAGE_NAME="${2:-booking-bot}"         # второй аргумент или booking-bot

echo "→ Version:        ${APP_VERSION}"
echo "→ Image name:     ${IMAGE_NAME}"

# ──────────────────────────────── 2. Каталоги ────────────────────────────────
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
ROOT_DIR="$( dirname "$SCRIPT_DIR" )"
cd "$ROOT_DIR"

echo "→ Project root:   ${ROOT_DIR}"

# ──────────────────────────────── 3. Gradle build ────────────────────────────
echo "▶️  Building fat-JAR via Gradle (shadowJar)…"
./gradlew shadowJar --no-daemon

JAR_PATH=$(find build/libs -name "*-all.jar" | head -n 1)
if [[ -z "${JAR_PATH}" ]]; then
  echo "❌ Shadow JAR не найден! Проверьте, что Gradle task 'shadowJar' создаёт *-all.jar."
  exit 1
fi
echo "→ Found JAR:      ${JAR_PATH}"

# ──────────────────────────────── 4. Docker build ────────────────────────────
echo "🐳 Building Docker image ${IMAGE_NAME}:${APP_VERSION}…"
docker build \
  --build-arg APP_VERSION="${APP_VERSION}" \
  -t "${IMAGE_NAME}:${APP_VERSION}" \
  -t "${IMAGE_NAME}:latest" \
  -f Dockerfile .

echo "✅ Docker image '${IMAGE_NAME}:${APP_VERSION}' built successfully."

# ──────────────────────────────── 5. Подсказка ───────────────────────────────
cat