dependencies {
    // Also in :app
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("com.google.guava:guava:28.2-jre")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")

    // lib-only
    implementation("io.netty:netty-codec:4.1.118.Final")
    implementation("io.netty:netty-codec-http:4.1.118.Final")
    implementation("io.netty:netty-codec-http2:4.1.118.Final")
    implementation("io.netty:netty-codec-smtp:4.1.118.Final")
    implementation("io.netty:netty-handler:4.1.118.Final")
    implementation("net.jpountz.lz4:lz4:1.3.0")
    implementation("org.apache.avro:avro:1.9.2")
}