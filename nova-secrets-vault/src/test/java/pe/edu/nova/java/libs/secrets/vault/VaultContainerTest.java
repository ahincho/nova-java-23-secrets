package pe.edu.nova.java.libs.secrets.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.vault.VaultContainer;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSourceException;
import pe.edu.nova.java.libs.secrets.SecretSources;

/** Contra un Vault real, en modo desarrollo. Se salta si la máquina no tiene Docker. */
class VaultContainerTest {

    private static final String ROOT_TOKEN = "nova-test-root";

    private static VaultContainer<?> vault;

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
    void aSecretIsReadFromARealVault() {
        SecretSource source = source(ROOT_TOKEN);

        assertEquals(
                Map.of("DB_USERNAME", "course", "DB_PASSWORD", "s3cr3t"),
                source.find("ms-course").orElseThrow().entries());
        assertTrue(source.find("missing").isEmpty());
    }

    @Test
    void aWrongTokenIsRefused() {
        SecretSourceException error =
                assertThrows(SecretSourceException.class, () -> source("wrong").find("ms-course"));

        assertEquals(
                "Secret ms-course was refused by Vault (403): the token or the AppRole cannot read it",
                error.getMessage());
    }

    private static SecretSource source(String token) {
        return SecretSources.provider("vault")
                .create(SecretSettings.of(Map.of(
                        "nova.secrets.vault.address", vault.getHttpHostAddress(), "nova.secrets.vault.token", token)));
    }
}
