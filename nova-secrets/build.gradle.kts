plugins {
    id("pe.edu.nova.java.library")
    id("info.solidsoft.pitest")
}

description = "Contrato de la capacidad de secretos de Nova y su implementación por defecto, el entorno del proceso."

dependencies {
    // Solo el parser de streaming de Jackson 2. Convive con el Jackson 3 de Spring Boot 4 y con el
    // Jackson 2 de Quarkus, porque Jackson 3 cambió de paquete (ADR-042, pregunta abierta 6).
    implementation("com.fasterxml.jackson.core:jackson-core:2.22.3")

    testImplementation("net.jqwik:jqwik:1.9.3")
    testImplementation("com.tngtech.archunit:archunit:1.5.1")
}

pitest {
    junit5PluginVersion.set("1.2.1")
    targetClasses.set(setOf("pe.edu.nova.java.libs.secrets.*"))
    targetTests.set(setOf("pe.edu.nova.java.libs.secrets.*"))
    mutators.set(setOf("DEFAULTS"))
    outputFormats.set(setOf("HTML", "XML"))
    pitestVersion.set("1.17.4")
}
