import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

plugins {
    kotlin("jvm") version "1.9.23" // Рекомендуется использовать последнюю стабильную версию Kotlin
    kotlin("plugin.serialization") version "1.9.23" // Согласовать с версией Kotlin
    id("com.github.johnrengelman.shadow") version "8.1.1" // Последняя версия Shadow JAR plugin
    application
}

group = "com.example.bot" // Изменил group id для соответствия пакетам
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.flywaydb:flyway-core:10.13.0")
    // KStateMachine
    implementation("ru.nsk.kstatemachine:kstatemachine:0.42.0")
    implementation("ru.nsk.kstatemachine:kstatemachine-coroutines:0.42.0")

    // Telegram-bot
    implementation("com.github.kotlin-telegram-bot.kotlin-telegram-bot:telegram:6.1.0")

    // Exposed + Postgres
    implementation("org.jetbrains.exposed:exposed-core:0.50.1") // Используйте последнюю совместимую версию
    implementation("org.jetbrains.exposed:exposed-dao:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.50.1")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0") // Согласовать с версией Kotlin

    // Logging - SLF4J API and a Logback binding (recommended)
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.13")


    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.10") // Для мокирования в тестах
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0") // Для тестирования корутин
    testImplementation("com.h2database:h2:2.2.224") // In-memory DB for testing
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("com.example.bot.MainKt") // Убедитесь, что путь к MainKt правильный
}

tasks.shadowJar {
    archiveBaseName.set("BookingBot")
    archiveClassifier.set("") // будет BookingBot.jar
    mergeServiceFiles()
    manifest {
        attributes(mapOf("Main-Class" to application.mainClass.get()))
    }
}

tasks.withType<KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
        // freeCompilerArgs.add("-Xopt-in=kotlin.RequiresOptIn") // Если используете OptIn annotations
    }
}

// Для корректной работы с ресурсами (например, файлы локализации, если они будут)
sourceSets {
    main {
        resources {
            srcDirs("src/main/resources")
        }
    }
}


