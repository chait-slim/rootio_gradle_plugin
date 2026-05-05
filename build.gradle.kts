plugins {
    `java-gradle-plugin`
    `maven-publish`
    id("com.gradle.plugin-publish") version "2.1.1"
}

group = "io.root"
version = project.findProperty("pluginVersion")?.toString() ?: "0.2.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

gradlePlugin {
    website = "https://docs.root.io/rlc/java#gradle"
    vcsUrl = "https://github.com/rootio-avr/rootio_gradle_plugin"
    plugins {
        create("rootIoPatcher") {
            id = "io.root.patcher"
            implementationClass = "io.root.patcher.RootIoPatcherPlugin"
            displayName = "Root.io Patcher"
            description = "Automatically patches vulnerable Java dependencies with secure versions from the Root.io registry — no changes to your dependency declarations required."
            tags = listOf("security", "dependencies", "vulnerability", "patching")
        }
    }
}

repositories {
    mavenCentral()
}

publishing {
    repositories {
        val artifactoryUrl = System.getenv("ARTIFACTORY_URL")
        if (!artifactoryUrl.isNullOrEmpty()) {
            maven {
                url = uri(artifactoryUrl)
                credentials {
                    username = System.getenv("ARTIFACTORY_USER")
                    password = System.getenv("ARTIFACTORY_PASSWORD")
                }
            }
        }
    }
}

dependencies {
    implementation(localGroovy())
    testImplementation(gradleTestKit())
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Forward test.gradleVersions and test.javaHome so functional tests can read them.
    // test.gradleVersions: comma-separated Gradle versions to test (default: all three).
    // test.javaHome: path to a JDK to use as JAVA_HOME inside TestKit runners (default: current JVM).
    listOf("test.gradleVersions", "test.javaHome").forEach { key ->
        System.getProperty(key)?.let { systemProperty(key, it) }
    }
}
