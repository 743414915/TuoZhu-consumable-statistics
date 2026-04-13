import org.gradle.api.tasks.compile.JavaCompile

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

tasks.jar {
    manifest {
        attributes["Main-Class"] = "com.tuozhu.desktop.DesktopSyncApp"
    }
}
