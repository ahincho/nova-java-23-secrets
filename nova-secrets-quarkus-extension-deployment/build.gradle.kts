plugins {
    id("pe.edu.nova.java.library")
}

description = "Pasos de build de la extensión de secretos para Quarkus: registran las fuentes para la imagen nativa."

val quarkusVersion = "3.33.3.3"

dependencies {
    implementation(project(":nova-secrets-quarkus-extension"))
    implementation("io.quarkus:quarkus-core-deployment:$quarkusVersion")
    // Genera la lista de pasos de build que Quarkus lee al construir la aplicación.
    annotationProcessor("io.quarkus:quarkus-extension-processor:$quarkusVersion")

    // Cada prueba arma una aplicación Quarkus mínima con la extensión, como lo hace un servicio.
    testImplementation("io.quarkus:quarkus-junit-internal:$quarkusVersion")
    // QuarkusUnitTest inyecta la instancia de prueba con ArC, el CDI de Quarkus.
    testImplementation("io.quarkus:quarkus-arc-deployment:$quarkusVersion")
    testImplementation(project(":nova-secrets-vault"))
    testImplementation("org.testcontainers:testcontainers-vault:2.0.5")
}

tasks.withType<JavaCompile>().configureEach {
    // La documentación de configuración la genera el procesador solo para builds de Maven; en Gradle avisa
    // que no puede, y con -Werror ese aviso corta la compilación. Esta extensión no tiene configuración
    // propia que documentar: lee nova.secrets.* con la misma API que los adaptadores.
    options.compilerArgs.add("-AgenerateDoc=false")
}

tasks.named<Test>("test") {
    // El plugin de Quarkus le pasa a esta tarea el modelo de la aplicación desde un doFirst que lee el
    // proyecto, y con el configuration cache ese modelo no llega: QuarkusUnitTest no arranca.
    notCompatibleWithConfigurationCache("the Quarkus extension plugin builds the test application model from the project")
    // Quarkus valida al armar cada aplicación la imagen del builder nativo, que en un servicio llega con
    // las propiedades de la plataforma. El modelo de pruebas de Gradle no las trae, y la prueba no la usa.
    systemProperty("platform.quarkus.native.builder-image", "mandrel")
    // El JSON que inyectaría ECS, y una variable suelta con el mismo nombre que una clave del secreto:
    // las pruebas comprueban que el secreto gana, como dice ADR-042.
    environment("CREDENTIALS_DB", """{"DB_USERNAME":"course","DB_PASSWORD":"s3cr3t"}""")
    environment("DB_PASSWORD", "from-the-environment")
}
