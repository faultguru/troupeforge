plugins {
    `java-library`
}

dependencies {
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.jackson.databind)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    // Integration tests are expensive (real LLM calls), don't run by default
    onlyIf { project.hasProperty("integTest") }
    // Forward base URL to test JVM
    systemProperty("integtest.base-url",
        System.getProperty("integtest.base-url") ?: project.findProperty("integtest.base-url") ?: "http://localhost:8080")
}
