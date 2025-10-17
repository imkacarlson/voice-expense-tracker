import org.gradle.jvm.tasks.Jar

plugins {
    kotlin("jvm")
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.voiceexpense.eval.CliMainKt")
}

dependencies {
    implementation(project(":parsing"))
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
}

tasks.named<Jar>("jar") {
    dependsOn(":parsing:jar")
    manifest {
        attributes["Main-Class"] = "com.voiceexpense.eval.CliMainKt"
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
}

tasks.named("build") {
    dependsOn(tasks.named("jar"))
}
