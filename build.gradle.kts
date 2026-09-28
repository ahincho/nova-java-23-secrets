import org.gradle.api.plugins.quality.CheckstyleExtension
import org.owasp.dependencycheck.gradle.extension.DependencyCheckExtension

plugins {
    id("net.nemerosa.versioning") version "4.0.1"
    id("org.owasp.dependencycheck") version "12.2.2" apply false
    id("info.solidsoft.pitest") version "1.19.0-rc.1" apply false
    id("org.cyclonedx.bom") version "3.2.4"
}

versioning {
    releaseMode = "snapshot"
    displayMode = "snapshot"
    releaseBuild = false
}

val junitVersion = "6.0.3"
val jqwikVersion = "1.9.3"
val repositoryUrl = "https://github.com/ahincho/nova-java-23-secrets"

subprojects {
    // El contrato y los adaptadores son librerías puras (nivel 1 de ADR-001); los conectores de un
    // framework son nivel 2. Cada nivel tiene su groupId (ADR-004), aunque vivan en el mismo
    // repositorio y salgan con la misma versión (ADR-041).
    group = if (name.endsWith("-spring-boot-starter") || name.endsWith("-quarkus-extension")) {
        "pe.edu.nova.java.starters"
    } else {
        "pe.edu.nova.java.libs"
    }
    version = rootProject.findProperty("version") as String

    apply(plugin = "java-library")
    apply(plugin = "maven-publish")
    apply(plugin = "signing")
    apply(plugin = "checkstyle")
    apply(plugin = "jacoco")
    apply(plugin = "org.owasp.dependencycheck")

    repositories {
        mavenCentral()
    }

    configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
        withSourcesJar()
        withJavadocJar()
    }

    dependencies {
        "testImplementation"("org.junit.jupiter:junit-jupiter:$junitVersion")
        "testImplementation"("net.jqwik:jqwik:$jqwikVersion")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher:$junitVersion")
    }

    tasks.withType<JavaCompile>().configureEach {
        options.encoding = "UTF-8"
    }

    tasks.named<Test>("test") {
        useJUnitPlatform()
        finalizedBy(tasks.named("jacocoTestReport"))
    }

    tasks.named<JacocoReport>("jacocoTestReport") {
        reports {
            xml.required.set(true)
        }
    }

    tasks.named<Javadoc>("javadoc") {
        (options as StandardJavadocDocletOptions).apply {
            addStringOption("Xdoclint:all", "-quiet")
            encoding = "UTF-8"
            charSet = "UTF-8"
        }
    }

    configure<CheckstyleExtension> {
        // Solo el código de producción: las pruebas usan imports estáticos con comodín
        // (org.junit.jupiter.api.Assertions.*), que AvoidStarImport rechazaría.
        sourceSets = listOf(the<SourceSetContainer>().getByName("main"))
    }

    configure<DependencyCheckExtension> {
        // NVD_API_KEY y NOVA_OWASP_FAIL_ON_CVSS los pone reusable-owasp-check.yml. En local, sin
        // esas variables, el análisis nunca falla (11) y corre sin clave de NVD.
        failBuildOnCVSS = (System.getenv("NOVA_OWASP_FAIL_ON_CVSS") ?: "11").toFloat()
        nvd.apiKey = System.getenv("NVD_API_KEY") ?: ""
        // reusable-owasp-check.yml restaura un mirror de NVD de menos de 24 horas. Sin estas dos
        // líneas el plugin lo ignora, sincroniza NVD entero y puede quedarse sin memoria.
        autoUpdate = false
        data.directory = System.getenv("NOVA_OWASP_DATA_DIR")
            ?: "${System.getProperty("user.home")}/.dependency-check-data"
        // Solo lo que recibe el consumidor. Las herramientas del build (checkstyle, pitest) y las
        // dependencias de prueba no viajan en el POM publicado.
        scanConfigurations = listOf("compileClasspath", "runtimeClasspath")
        // Falsos positivos documentados en docs/owasp-suppressions.json de nova-shared-02-pipelines.
        // reusable-owasp-check.yml genera el XML con los CVE que lista ci.yml y deja su ruta en
        // NOVA_OWASP_SUPPRESSIONS_FILE; sin esa variable no se suprime nada. Cuando ci.yml no lista
        // ninguno, la variable llega vacía, y en Java 25 File("").exists() es true: por eso se pide
        // un archivo y no solo que la ruta exista.
        System.getenv("NOVA_OWASP_SUPPRESSIONS_FILE")
            ?.takeIf { it.isNotBlank() && File(it).isFile }
            ?.let { suppressionFiles.add(it) }
    }

    configure<PublishingExtension> {
        publications {
            create<MavenPublication>("mavenJava") {
                from(components["java"])
                pom {
                    name.set(project.name)
                    description.set(provider { project.description })
                    url.set(repositoryUrl)
                    licenses {
                        license {
                            name.set("Eclipse Public License 2.0")
                            url.set("https://www.eclipse.org/legal/epl-2.0/")
                            distribution.set("repo")
                        }
                    }
                    developers {
                        developer {
                            id.set("ahincho")
                            name.set("Angel Eduardo Hincho Jove")
                        }
                    }
                    scm {
                        url.set(repositoryUrl)
                        connection.set("scm:git:$repositoryUrl.git")
                    }
                }
            }
        }
        repositories {
            maven {
                name = "GitHubPackages"
                url = uri("https://maven.pkg.github.com/ahincho/nova-java-23-secrets")
                credentials {
                    username = System.getenv("GITHUB_ACTOR")
                    password = System.getenv("GITHUB_TOKEN")
                }
            }
        }
    }

    configure<SigningExtension> {
        val gpgKeyId: String? = System.getenv("GPG_SIGNING_KEY_ID")
        val gpgKey: String? = System.getenv("GPG_SIGNING_KEY")
        val gpgPassword: String? = System.getenv("GPG_SIGNING_PASSWORD")

        if (gpgKeyId != null && gpgKey != null) {
            useInMemoryPgpKeys(gpgKeyId, gpgKey, gpgPassword ?: "")
            sign(the<PublishingExtension>().publications)
        }
    }
}
