plugins {
    application
}

application {
    mainClass.set("com.troupeforge.client.TroupeForgeClient")
}

tasks.named<JavaExec>("run") {
    standardInput = System.`in`
    jvmArgs("-Dfile.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
            "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8")
    // Ensure Gradle doesn't re-encode the forked process output
    environment("JAVA_TOOL_OPTIONS", "-Dfile.encoding=UTF-8")
    // Use the process's raw stdout/stderr directly
    isIgnoreExitValue = true
}

dependencies {
    implementation(libs.jackson.databind)
}
