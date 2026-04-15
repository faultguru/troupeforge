dependencies {
    api(project(":troupeforge-core"))
    implementation(libs.jackson.databind)
    implementation(libs.guava)
    implementation(libs.reactor.core)
    implementation(libs.slf4j.api)

    testImplementation(project(":troupeforge-infra"))
    testImplementation(libs.reactor.test)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
