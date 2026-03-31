plugins {
    java
    id("io.root.patcher") version "0.1.0"
}

group = "com.example"
version = "1.0.0"

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("ch.qos.logback:logback-core:1.2.9")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")
    implementation("com.google.guava:guava:28.2-jre")
    implementation("commons-beanutils:commons-beanutils:1.9.4")
    implementation("com.nimbusds:nimbus-jose-jwt:9.31")
    implementation("io.grpc:grpc-netty-shaded:1.67.1")
    implementation("io.netty:netty-codec:4.1.118.Final")
    implementation("io.netty:netty-codec-http:4.1.118.Final")
    implementation("io.netty:netty-codec-http2:4.1.118.Final")
    implementation("io.netty:netty-codec-smtp:4.1.118.Final")
    implementation("io.netty:netty-handler:4.1.118.Final")
    implementation("io.projectreactor.netty:reactor-netty-core:1.1.10")
    implementation("io.projectreactor.netty:reactor-netty-http:1.1.10")
    implementation("net.jpountz.lz4:lz4:1.3.0")
    implementation("org.apache.avro:avro:1.9.2")
}

rootio {
    apiKey.set(providers.environmentVariable("ROOTIO_API_KEY").orElse("test-api-key"))
}
