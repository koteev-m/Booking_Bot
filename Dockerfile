###############################################################################
# Stage 1 ─── Сборка fat-JAR через Gradle (ShadowPlugin)                      #
###############################################################################
FROM gradle:8.7-jdk21-alpine AS build

# Оптимизация кэша: сначала копируем gradle-конфигурацию, затем исходники
WORKDIR /home/gradle/project

# 1. Копируем только файлы конфигурации, чтобы слой зависимостей кэшировался
COPY settings.gradle* build.gradle* build.gradle.kts gradle* gradlew* ./

# 2. Загружаем зависимости (рутинная «пустая» сборка, кэшируется)
RUN ./gradlew --no-daemon clean assemble || true

# 3. Теперь копируем всё остальное (src/ и ресурсы) и делаем финальную сборку
COPY . .
RUN ./gradlew --no-daemon shadowJar

###############################################################################
# Stage 2 ─── Runtime-слой: лишь JRE + JAR                                    #
###############################################################################
FROM eclipse-temurin:21-jre-alpine

# 1. Достаём версию приложения из аргумента сборки (можно передать из скрипта)
ARG APP_VERSION="dev"
ENV APP_VERSION=${APP_VERSION}

# 2. Создаём рабочую директорию
WORKDIR /opt/app

# 3. Копируем только собранный JAR (он содержит все зависимости)
COPY --from=build /home/gradle/project/build/libs/*-all.jar app.jar

# 4. Указываем, что контейнер слушает 8080 (если у вас есть REST/Webhook)
EXPOSE 8080

# 5. Команда запуска
ENTRYPOINT ["java", "-jar", "/opt/app/app.jar"]