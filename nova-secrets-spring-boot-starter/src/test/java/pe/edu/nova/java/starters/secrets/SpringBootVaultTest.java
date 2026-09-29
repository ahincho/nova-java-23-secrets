package pe.edu.nova.java.starters.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.vault.VaultContainer;

/**
 * De punta a punta: una aplicación Spring Boot que importa su secreto de un Vault real, sin una
 * línea de código de Vault. Se salta si la máquina no tiene Docker.
 */
class SpringBootVaultTest {

    private static final String ROOT_TOKEN = "nova-test-root";

    private static VaultContainer<?> vault;

    @Configuration(proxyBeanMethods = false)
    static class CourseService {
    }

    @BeforeAll
    static void start() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        vault = new VaultContainer<>("hashicorp/vault:2.1.1")
                .withVaultToken(ROOT_TOKEN)
                .withInitCommand("kv put secret/ms-course DB_USERNAME=course DB_PASSWORD=s3cr3t");
        vault.start();
    }

    @AfterAll
    static void stop() {
        if (vault != null) {
            vault.stop();
        }
    }

    @Test
    void theServiceReadsItsDatabaseCredentialsFromVault() {
        SpringApplication application = new SpringApplication(CourseService.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);

        try (ConfigurableApplicationContext context = application.run(
                "--spring.config.import=nova-secrets:vault:ms-course",
                "--nova.secrets.vault.address=" + vault.getHttpHostAddress(),
                "--nova.secrets.vault.token=" + ROOT_TOKEN,
                "--spring.datasource.password=${DB_PASSWORD}")) {
            assertThat(context.getEnvironment().getProperty("spring.datasource.password")).isEqualTo("s3cr3t");
            assertThat(Binder.get(context.getEnvironment()).bind("db.username", String.class).get()).isEqualTo("course");
        }
    }
}
