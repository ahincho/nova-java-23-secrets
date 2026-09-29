description = "Adaptador de la capacidad de secretos para HashiCorp Vault, motor KV versión 2."

dependencies {
    api(project(":nova-secrets"))
    // El parser de streaming, como en el contrato: lee la respuesta de Vault sin atarse a Jackson 3.
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.3")

    testImplementation("org.testcontainers:testcontainers-vault:2.0.5")
}
