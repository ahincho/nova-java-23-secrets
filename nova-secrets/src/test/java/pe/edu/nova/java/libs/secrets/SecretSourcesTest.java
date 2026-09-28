package pe.edu.nova.java.libs.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.secrets.env.EnvironmentSecretSource;
import pe.edu.nova.java.libs.secrets.env.EnvironmentSecretSourceProvider;

class SecretSourcesTest {

    @Test
    void theEnvironmentSourceIsAlwaysAvailable() {
        List<String> names = SecretSources.providers().stream().map(SecretSourceProvider::name).toList();

        assertTrue(names.contains("env"), names.toString());
    }

    @Test
    void providerFindsTheEnvironmentSourceByName() {
        SecretSourceProvider provider = SecretSources.provider("env");

        assertInstanceOf(EnvironmentSecretSourceProvider.class, provider);
        assertInstanceOf(EnvironmentSecretSource.class, provider.create(SecretSettings.empty()));
    }

    @Test
    void anUnknownNameListsTheAvailableSources() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> SecretSources.provider("vault"));

        assertEquals("No secret source named 'vault'. Available: [env]. "
                + "Each store is a dependency, such as nova-secrets-vault.", error.getMessage());
    }
}
