plugins {
    id("io.root.patcher") version "0.1.0" apply false
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "io.root.patcher")

    group = "com.example"
    version = "1.0.0"

    extensions.configure<JavaPluginExtension> {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    repositories {
        mavenLocal()
        mavenCentral()
    }
}