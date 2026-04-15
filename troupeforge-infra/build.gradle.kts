dependencies {
    api(project(":troupeforge-core"))
    implementation(libs.jackson.databind)
    implementation(libs.slf4j.api)

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
