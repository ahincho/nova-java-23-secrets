description = "Conector de la capacidad de secretos con Spring Boot: carga los secretos antes de que exista la aplicación."

val springBootVersion = "4.0.8"

dependencies {
    api(project(":nova-secrets"))

    // Spring Boot lo trae el servicio; el starter solo compila contra él.
    compileOnly("org.springframework.boot:spring-boot:$springBootVersion")
    compileOnly("org.springframework.boot:spring-boot-autoconfigure:$springBootVersion")
    compileOnly("org.springframework.boot:spring-boot-actuator:$springBootVersion")

    testImplementation("org.springframework.boot:spring-boot-starter-test:$springBootVersion")
    testImplementation("org.springframework.boot:spring-boot-actuator:$springBootVersion")
}
