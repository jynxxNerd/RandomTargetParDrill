plugins {
    java
    id("org.openjfx.javafxplugin") version "0.1.0"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    // ShootOFF itself: run `./gradlew publishToMavenLocal` in the ShootOFF project first
    mavenLocal()
    mavenCentral()
}

javafx {
    version = "21.0.12"
    modules("javafx.controls")
    // ShootOFF provides JavaFX at runtime
    configuration = "compileOnly"
}

dependencies {
    // Provided by ShootOFF at runtime, so none of it goes into the plugin jar
    compileOnly("com.shootoff:shootoff:5.0.0-SNAPSHOT") { isTransitive = false }
    compileOnly("org.slf4j:slf4j-api:2.0.20")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Directory of the ShootOFF install to copy the plugin into; override with -PshootoffHome=...
val shootoffHome = providers.gradleProperty("shootoffHome").orElse("../ShootOFF")

tasks.register<Copy>("installPlugin") {
    description = "Copies the plugin jar into ShootOFF's exercises folder"
    group = "distribution"
    from(tasks.jar)
    into(shootoffHome.map { "$it/exercises" })
}
