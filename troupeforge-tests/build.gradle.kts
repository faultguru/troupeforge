plugins {
    `java-library`
    alias(libs.plugins.spring.boot) apply false
    alias(libs.plugins.spring.dependency.management)
}

dependencyManagement {
    imports {
        mavenBom(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES)
    }
}

dependencies {
    // Depend on all modules for full integration testing
    testImplementation(project(":troupeforge-core"))
    testImplementation(project(":troupeforge-engine"))
    testImplementation(project(":troupeforge-infra"))
    testImplementation(project(":troupeforge-tools"))
    testImplementation(project(":troupeforge-app"))
    testImplementation(project(":troupeforge-client"))
    testImplementation(project(":troupeforge-testconfig"))

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(libs.junit.jupiter)
    testImplementation(libs.junit.jupiter.params)
    testImplementation(libs.jackson.databind)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}
