dependencies {
    api(project(":commandant-api"))
    implementation(project(":commandant-common"))
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            artifactId = "commandant"
            pom {
                name.set("commandant")
                description.set("Aggregator module for Commandant")
            }
        }
    }
}

