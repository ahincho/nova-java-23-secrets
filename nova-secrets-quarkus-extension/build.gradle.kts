plugins {
    id("pe.edu.nova.java.library")
    id("io.quarkus.extension")
}

description = "Conector de la capacidad de secretos con Quarkus: carga los secretos antes de que exista la aplicación."

val quarkusVersion = "3.33.3.3"

quarkusExtension {
    deploymentModule.set("nova-secrets-quarkus-extension-deployment")
}

dependencies {
    api(project(":nova-secrets"))
    implementation("io.quarkus:quarkus-core:$quarkusVersion")
}

// Las tareas del plugin de Quarkus guardan el proyecto entero, y el configuration cache que enciende el
// toolchain (ADR-044) no lo admite. Se declaran fuera del cache en lugar de apagarlo para todo el build.
tasks.matching { it.name in setOf("extensionDescriptor", "validateExtension") }.configureEach {
    notCompatibleWithConfigurationCache("the Quarkus extension plugin stores the project in its tasks")
}

tasks.withType<JavaCompile>().configureEach {
    // El plugin suma el procesador de extensiones, que en Gradle avisa que no puede generar la documentación
    // de configuración; con -Werror ese aviso corta la compilación, y el runtime no tiene configuración propia.
    options.compilerArgs.add("-AgenerateDoc=false")
}
