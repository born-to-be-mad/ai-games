plugins {
    `java-library`
    id("info.solidsoft.pitest")
}

dependencies {
    // Common (BOM-managed)
    implementation(libs.spring.boot.starter)
    implementation(libs.slf4j.api)
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    compileOnly(libs.lombok)
    annotationProcessor(libs.lombok)
    testCompileOnly(libs.lombok)
    testAnnotationProcessor(libs.lombok)

    // Pure domain — zero Spring/framework dependencies
    // Only java.* and third-party value-object helpers allowed
    implementation(libs.jackson.annotations)
}

pitest {
    targetClasses.set(listOf("com.aiarchitect.terraquery.*"))
    targetTests.set(listOf("com.aiarchitect.terraquery.*Test"))
    mutators.set(listOf("DEFAULTS"))
    timestampedReports.set(false)
    outputFormats.set(listOf("HTML", "XML"))
    threads.set(4)
    mutationThreshold.set(55)
    // Auto-detection of JUnit 5/6 plugin fails when Jupiter version is 6.x;
    // explicitly set the pitest-junit5-plugin version (works for both JUnit 5 and 6).
    junit5PluginVersion.set(libs.versions.pitest.junit5.plugin.get())
}
