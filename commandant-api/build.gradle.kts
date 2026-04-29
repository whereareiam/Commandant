plugins {
    id("commandant.java-common")
}

dependencies {
    api(libs.keystone)
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "commandant-api"
            pom {
                name.set("commandant-api")
                description.set("Public API for Commandant")
            }
        }
    }
}
