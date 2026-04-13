plugins {
    `java-library`
    id("info.solidsoft.pitest")
}

dependencies {
    // Pure domain — zero Spring/framework dependencies
    // Only java.* and third-party value-object helpers allowed
    implementation("com.fasterxml.jackson.core:jackson-annotations")
}

pitest {
    targetClasses.set(listOf("com.aiarchitect.terraquery.*"))
    targetTests.set(listOf("com.aiarchitect.terraquery.*Test"))
    mutators.set(listOf("DEFAULTS"))
    timestampedReports.set(false)
    outputFormats.set(listOf("HTML", "XML"))
    threads.set(4)
    mutationThreshold.set(70)
}
