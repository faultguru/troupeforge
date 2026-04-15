plugins {
    alias(libs.plugins.spring.boot)
    alias(libs.plugins.spring.dependency.management)
}

springBoot {
    mainClass.set("com.troupeforge.app.TroupeForgeApplication")
}

tasks.named<Jar>("jar") {
    isEnabled = true
}

tasks.named<org.springframework.boot.gradle.tasks.run.BootRun>("bootRun") {
    if (project.hasProperty("mainClass")) {
        mainClass.set(project.property("mainClass") as String)
    }
    workingDir = rootProject.projectDir
}

dependencies {
    implementation(project(":troupeforge-engine"))
    implementation(project(":troupeforge-infra"))
    implementation(project(":troupeforge-tools"))

    implementation(libs.spring.boot.starter)
    implementation(libs.spring.boot.starter.web)
    implementation(libs.spring.boot.starter.webflux)

    testImplementation(libs.spring.boot.starter.test)
}
