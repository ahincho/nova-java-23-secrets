description = "Adaptador de la capacidad de secretos para AWS Secrets Manager."

val awsSdkVersion = "2.55.7"

dependencies {
    api(project(":nova-secrets"))

    // El cliente HTTP liviano del SDK, basado en HttpURLConnection. Los dos que el módulo trae por
    // defecto (Apache y Netty) no hacen falta para leer unos secretos al arrancar.
    implementation("software.amazon.awssdk:secretsmanager:$awsSdkVersion") {
        exclude(group = "software.amazon.awssdk", module = "apache-client")
        exclude(group = "software.amazon.awssdk", module = "netty-nio-client")
    }
    implementation("software.amazon.awssdk:url-connection-client:$awsSdkVersion")

    testImplementation("org.testcontainers:testcontainers:2.0.5")
}
