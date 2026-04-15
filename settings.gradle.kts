plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "troupeforge"

include(
    "troupeforge-core",
    "troupeforge-engine",
    "troupeforge-infra",
    "troupeforge-tools",
    "troupeforge-app",
    "troupeforge-client",
    "troupeforge-testconfig",
    "troupeforge-tests",
    "troupeforge-integtests",
)
