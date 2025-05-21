import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "1.8.22"
    kotlin("plugin.serialization") version "1.8.22"
    id("com.github.johnrengelman.shadow") version "7.1.2"
    application
}

group = "org.example"
version = "1.0-SNAPSHOT"

/* ─────────── репозитории ─────────── */
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

/* ─────────── зависимости ─────────── */
dependencies {
    /* FSM */
    implementation("io.github.nsk90:kstatemachine:0.33.0")

    /* Telegram-Bot */
    implementation("com.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.1.0")
    /* Exposed + Postgres + Hikari */
    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.50.1")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")

    /* Корутины */
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")

    testImplementation(kotlin("test"))
}

/* ─────────── Java 17 tool-chain ─────────── */
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

/* ─────────── точка входа ─────────── */
application {
    mainClass.set("MainKt")
}

/* ─────────── Shadow Jar ─────────── */
tasks.shadowJar {
    archiveBaseName.set("BookingBot")
    archiveClassifier.set("")  // будет BookingBot.jar
    mergeServiceFiles()
}

/* ─────────── Kotlin: compilerOptions ─────────── */
tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}