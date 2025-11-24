dependencies {
    "api"(project(":commandant-api"))

    "compileOnly"(libs.cloud.core)
    "compileOnly"(libs.cloud.cooldowns)
    "compileOnly"(libs.cloud.minecraft.extras)

    // Testing
    "testImplementation"(libs.junit.jupiter)
    "testRuntimeOnly"(libs.junit.platform)
    "testImplementation"(libs.mockito.core)
    "testImplementation"(libs.mockito.junit)
    "testImplementation"(libs.cloud.core)
    "testImplementation"(libs.cloud.cooldowns)
}

tasks.test {
    useJUnitPlatform()
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

