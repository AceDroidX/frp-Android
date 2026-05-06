plugins {
    id("com.android.application") version "9.2.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.21" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.3.21" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}