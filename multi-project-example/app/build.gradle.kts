dependencies {
    // Also in :lib
    implementation("ch.qos.logback:logback-classic:1.4.11")
    implementation("com.google.guava:guava:28.2-jre")
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.2")

    // app-only
    implementation("ch.qos.logback:logback-core:1.2.9")
    implementation("commons-beanutils:commons-beanutils:1.9.4")
    implementation("com.nimbusds:nimbus-jose-jwt:9.31")
    implementation("io.grpc:grpc-netty-shaded:1.67.1")
    implementation("io.projectreactor.netty:reactor-netty-core:1.1.10")
    implementation("io.projectreactor.netty:reactor-netty-http:1.1.10")
}