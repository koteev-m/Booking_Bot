plugins {
    kotlin("jvm") version "1.9.23"
    kotlin("plugin.serialization") version "1.9.23"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

group = "com.example.bot"
version = "1.0.0"

repositories {
    // JitPack нужен для kotlin-telegram-bot  [oai_citation:0‡mvnrepository.com](https://mvnrepository.com/artifact/com.github.kotlin-telegram-bot/kotlin-telegram-bot?utm_source=chatgpt.com)
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    // Flyway, Exposed, HikariCP и т.д.
    implementation("org.flywaydb:flyway-core:10.13.0")
    implementation("org.jetbrains.exposed:exposed-core:0.50.1")
    implementation("org.jetbrains.exposed:exposed-dao:0.50.1")
    implementation("org.jetbrains.exposed:exposed-jdbc:0.50.1")
    implementation("org.jetbrains.exposed:exposed-java-time:0.50.1")
    implementation("org.postgresql:postgresql:42.7.3")
    implementation("com.zaxxer:HikariCP:5.1.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
    implementation("org.slf4j:slf4j-api:2.0.12")
    implementation("ch.qos.logback:logback-classic:1.5.13")

    // ——————————————————————————————————————————————
    // KStateMachine core и корутины, версия 0.33.0  [oai_citation:1‡mvnrepository.com](https://mvnrepository.com/artifact/io.github.nsk90/kstatemachine?utm_source=chatgpt.com) [oai_citation:2‡mvnrepository.com](https://mvnrepository.com/artifact/io.github.nsk90/kstatemachine-coroutines?utm_source=chatgpt.com)
//    implementation("io.github.nsk90:kstatemachine:0.33.0")
//    implementation("io.github.nsk90:kstatemachine-coroutines:0.33.0")

    // Kotlin Telegram Bot из JitPack, версия 6.3.0  [oai_citation:3‡mvnrepository.com](https://mvnrepository.com/artifact/com.github.kotlin-telegram-bot/kotlin-telegram-bot?utm_source=chatgpt.com)
    implementation("com.github.kotlin-telegram-bot:kotlin-telegram-bot:6.3.0")

    // Тестовые зависимости
    testImplementation(kotlin("test"))
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.0")
    testImplementation("com.h2database:h2:2.2.224")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testImplementation("org.testcontainers:junit-jupiter:1.21.1")
    // Принудительно используем новую commons-compress, чтобы перекрыть транзитивную
    implementation("org.apache.commons:commons-compress:1.26.0")
    testImplementation("org.testcontainers:postgresql:1.19.7")
    testImplementation("io.mockk:mockk:1.13.10")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")
    implementation("io.github.cdimascio:dotenv-kotlin:6.4.1")
    implementation(kotlin("test"))
}

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

application {
    mainClass.set("bot.MainKt")
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.shadowJar {
    archiveBaseName.set("BookingBot")
    archiveClassifier.set("")
    mergeServiceFiles()
    manifest {
        attributes("Main-Class" to application.mainClass.get())
    }
}