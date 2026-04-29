import org.gradle.api.initialization.resolve.RepositoriesMode

rootProject.name = "Commandant"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven("https://maven.whereareiam.me/release")
        maven("https://maven.whereareiam.me/development")
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_SETTINGS)

    repositories {
        mavenCentral()
        maven("https://maven.whereareiam.me/release")
        maven("https://maven.whereareiam.me/development")
        maven("https://oss.sonatype.org/content/repositories/snapshots/")
    }
}

include(":commandant-api")
project(":commandant-api").projectDir = file("commandant-api")

include(":commandant-common")
project(":commandant-common").projectDir = file("commandant-common")

include(":commandant")
project(":commandant").projectDir = file("commandant")
