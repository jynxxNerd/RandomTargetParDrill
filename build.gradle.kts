plugins {
    java
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    // ShootOFF's plugin API: run `./gradlew publishToMavenLocal` in the ShootOFF project first
    mavenLocal()
    mavenCentral()
}

val shootoffVersion = "5.0.0-SNAPSHOT"

dependencies {
    // Provided by ShootOFF at runtime, so none of it goes into the plugin jar. Not transitive: core's
    // own libraries (OpenCV, MaryTTS, ...) aren't needed to compile an exercise, and some aren't on
    // Maven Central
    compileOnly("com.shootoff:plugin-api:$shootoffVersion") { isTransitive = false }
    compileOnly("com.shootoff:core:$shootoffVersion") { isTransitive = false }
    compileOnly("org.slf4j:slf4j-api:2.0.20")

    testImplementation("com.shootoff:plugin-api:$shootoffVersion") { isTransitive = false }
    testImplementation("com.shootoff:core:$shootoffVersion") { isTransitive = false }
    // FakeExerciseHost, from plugin-api's test fixtures
    testImplementation("com.shootoff:plugin-api:$shootoffVersion") {
        isTransitive = false
        capabilities { requireCapability("com.shootoff:plugin-api-test-fixtures") }
    }
    testImplementation("org.slf4j:slf4j-api:2.0.20")
    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

tasks.jar {
    // Installed next to the v1 drill's RandomTargetParDrill.jar rather than over it
    archiveFileName = "RandomTargetParDrill-v2.jar"
}

// Directory of the ShootOFF install to copy the plugin into; override with -PshootoffHome=...
val shootoffHome = providers.gradleProperty("shootoffHome").orElse("../ShootOFF")

tasks.register<Copy>("installPlugin") {
    description = "Copies the plugin jar into ShootOFF's exercises folder"
    group = "distribution"
    from(tasks.jar)
    into(shootoffHome.map { "$it/exercises" })
}
