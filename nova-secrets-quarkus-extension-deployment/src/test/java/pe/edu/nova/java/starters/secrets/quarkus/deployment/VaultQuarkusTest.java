package pe.edu.nova.java.starters.secrets.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.QuarkusUnitTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.vault.VaultContainer;

/**
 * De punta a punta: una aplicación Quarkus que importa su secreto de un Vault real, sin una línea de
 * código de Vault. Es la misma prueba que la del starter de Spring Boot, y se salta sin Docker.
 */
@EnabledIf("dockerIsAvailable")
class VaultQuarkusTest {

    private static final String ROOT_TOKEN = "nova-test-root";

    private static final boolean DOCKER = DockerClientFactory.instance().isDockerAvailable();

    // Arranca antes de armar la aplicación, porque su dirección es parte de la configuración.
    private static final VaultContainer<?> VAULT = DOCKER ? startVault() : null;

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("nova.secrets.import", "vault:ms-course, optional:vault:not-there")
            .overrideConfigKey("nova.secrets.vault.address", DOCKER ? VAULT.getHttpHostAddress() : "http://localhost:1")
            .overrideConfigKey("nova.secrets.vault.token", ROOT_TOKEN)
            .overrideConfigKey("course.password", "${DB_PASSWORD}");

    static boolean dockerIsAvailable() {
        return DOCKER;
    }

    private static VaultContainer<?> startVault() {
        VaultContainer<?> vault = new VaultContainer<>("hashicorp/vault:2.1.1")
                .withVaultToken(ROOT_TOKEN)
                .withInitCommand("kv put secret/ms-course DB_USERNAME=vault-course DB_PASSWORD=from-vault");
        vault.start();
        return vault;
    }

    @Test
    void theServiceReadsItsDatabaseCredentialsFromVault() {
        assertThat(ConfigProvider.getConfig().getValue("course.password", String.class))
                .isEqualTo("from-vault");
        assertThat(ConfigProvider.getConfig().getValue("db.username", String.class))
                .isEqualTo("vault-course");
    }
}
