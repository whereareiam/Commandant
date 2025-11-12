dependencies {
    "api"(project(":commandant-api"))

    "compileOnly"(libs.cloud.core)
    "compileOnly"(libs.cloud.cooldowns)
    "compileOnly"(libs.cloud.minecraft.extras)
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

