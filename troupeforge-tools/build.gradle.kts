dependencies {
    api(project(":troupeforge-core"))
    implementation(project(":troupeforge-engine"))

    testImplementation(libs.jackson.databind)
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}
