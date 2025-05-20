import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    // Kotlin JVM + плагин сериализации, обе версии 2.1.20
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.serialization") version "2.1.20"

    // Shadow Jar (с 7.1.2 всё ок на Gradle 8.x)
    id("com.github.johnrengelman.shadow") version "7.1.2"

    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://jitpack.io")       // для kotlin-telegram-bot
}

dependencies {

    /* ──────────── Telegram-бот ──────────── */
    implementation("io.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.1.0")

    /* ─────────────  OkHttp  ───────────── */
    implementation(platform("com.squareup.okhttp3:okhttp-bom:4.12.0"))
    implementation("com.squareup.okhttp3:okhttp")
    implementation("com.squareup.okhttp3:logging-interceptor")

    /* ─────────────  Прочее  ───────────── */
    implementation("com.google.code.gson:gson:2.10")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")

    /* ─────────────  Ktor + Coroutines  ───────────── */
    implementation("io.ktor:ktor-client-core:3.1.2")
    implementation("io.ktor:ktor-client-cio:3.1.2")
    implementation("io.ktor:ktor-client-content-negotiation:3.1.2")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.1.2")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    testImplementation(kotlin("test"))
}

/* ───────────  Java 17 под Kotlin  ─────────── */
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

/* ─────────────  Точка входа  ───────────── */
application {
    // если main.kt без package:
    mainClass.set("MainKt")
}

/* ───────────  Shadow Jar  ─────────── */
tasks {
    shadowJar {
        archiveBaseName.set("BookingBot")
        archiveClassifier.set("")
        mergeServiceFiles()
    }
}

/* ─── Доп. флаг компилятора (опционально) ─── */
tasks.withType<KotlinCompile>().configureEach {
    kotlinOptions.jvmTarget = "17"
}