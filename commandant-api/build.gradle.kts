dependencies {
    compileOnly(libs.cloud.core)
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