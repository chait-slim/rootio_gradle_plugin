plugins {
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.root"
version = project.findProperty("pluginVersion")?.toString() ?: "0.1.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

gradlePlugin {
    plugins {
        create("rootIoPatcher") {
            id = "io.root.patcher"
            implementationClass = "io.root.patcher.RootIoPatcherPlugin"
        }
    }
}

repositories {
    mavenCentral()
}

publishing {
    repositories {
        val codeartifactUrl = System.getenv("CODEARTIFACT_URL")
        if (!codeartifactUrl.isNullOrEmpty()) {
            maven {
                url = uri(codeartifactUrl)
                credentials {
                    username = "aws"
                    password = System.getenv("CODEARTIFACT_AUTH_TOKEN")
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
