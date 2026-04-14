import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

plugins {
    application
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

application {
    mainClass.set("com.tuozhu.desktop.DesktopSyncApp")
}

dependencies {
    implementation("com.google.zxing:core:3.5.4")
    implementation("com.google.zxing:javase:3.5.4")
    testImplementation("junit:junit:4.13.2")
}

tasks.jar {
    archiveClassifier.set("plain")
    manifest {
        attributes["Main-Class"] = "com.tuozhu.desktop.DesktopSyncApp"
    }
}

val fatJar by tasks.registering(Jar::class) {
    group = "build"
    description = "Builds a self-contained desktop jar for jpackage."
    archiveFileName.set("desktop-app.jar")
    destinationDirectory.set(layout.buildDirectory.dir("package-input"))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "com.tuozhu.desktop.DesktopSyncApp"
    }

    from(sourceSets.main.get().output)
    dependsOn(configurations.runtimeClasspath)
    from({
        configurations.runtimeClasspath.get()
            .filter { it.name.endsWith(".jar") }
            .map { zipTree(it) }
    }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}
