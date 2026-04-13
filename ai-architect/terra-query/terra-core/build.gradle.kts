plugins {
    `java-library`
    // PIT mutation testing disabled: gradle-pitest-plugin 1.15.0 incompatible with Gradle 9+.
    // Re-enable once a Gradle 9-compatible release is available.
    // id("info.solidsoft.pitest")
}

dependencies {
    // Pure domain — zero Spring/framework dependencies
    // Only java.* and third-party value-object helpers allowed
    implementation("com.fasterxml.jackson.core:jackson-annotations")
}

// pitest {
//     targetClasses.set(listOf("com.aiarchitect.terraquery.*"))
//     targetTests.set(listOf("com.aiarchitect.terraquery.*Test"))
//     mutators.set(listOf("DEFAULTS"))
//     timestampedReports.set(false)
//     outputFormats.set(listOf("HTML", "XML"))
//     threads.set(4)
//     mutationThreshold.set(70)
// }
