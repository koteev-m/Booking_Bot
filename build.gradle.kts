import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Kotlin JVM
    kotlin("jvm") version "1.8.20"

    // Плагин Shadow Jar — собирает один‑единственный jar с кодом + зависимостями
    id("com.github.johnrengelman.shadow") version "7.1.2"

    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")       // нужен для kotlin‑telegram‑bot
}

dependencies {
    /* ──────────── Telegram‑бот ──────────── */
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.1.0")

    /* ─────────────  OkHttp  ───────────── */
    // 1) Подключаем BOM — «таблица» с одной версией для всех артефактов OkHttp
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))

    // 2) Нужные нам модули уже без версии
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")   // удобно для логирования запросов

    /* ─────────────  Прочее  ───────────── */
    implementation("com.google.code.gson:gson:2.10")             // JSON
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")    // .env

    testImplementation(kotlin("test"))
}

/* ───────────  Java 17 под Kotlin  ─────────── */
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

/* ─────────────  Точка входа  ───────────── */
application {
    // Если ваш файл main.kt лежит в пакете (например, package org.example),
    // то здесь должно быть "org.example.MainKt"
    mainClass.set("MainKt")
}

/* ───────────  Shadow Jar настройки  ─────────── */
tasks {
    shadowJar {
        archiveBaseName.set("BookingBot") // итоговый файл BookingBot.jar
        archiveClassifier.set("")
        mergeServiceFiles()               // склейка META-INF/services если потребуется
    }

    // При желании можно автоматически запускать shadowJar после сборки:
    // build { dependsOn(shadowJar) }
}

/* ─────────  Доп. флаги для компилятора (опционально) ───────── */
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}