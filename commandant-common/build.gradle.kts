plugins {
    id("commandant.java-common")
}

dependencies {
    api(project(":commandant-api"))

    compileOnly(libs.cloud.cooldowns)
    compileOnly(libs.cloud.annotations)

    testImplementation(libs.junit.jupiter)
    testImplementation(libs.cloud.core)
    testRuntimeOnly(libs.junit.platform)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "commandant-common"
            pom {
                name.set("commandant-common")
                description.set("Common implementation for Commandant")
            }
        }
    }
}
