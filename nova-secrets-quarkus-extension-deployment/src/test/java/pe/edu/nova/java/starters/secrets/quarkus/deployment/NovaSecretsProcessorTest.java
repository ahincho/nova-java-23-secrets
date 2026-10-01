package pe.edu.nova.java.starters.secrets.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.secrets.SecretSourceProvider;
import pe.edu.nova.java.libs.secrets.env.EnvironmentSecretSourceProvider;
import pe.edu.nova.java.libs.secrets.vault.VaultSecretSourceProvider;
import pe.edu.nova.java.starters.secrets.quarkus.NovaSecretsConfigBuilder;

/** Lo que la extensión registra al construir: sin esto, una imagen nativa no encuentra sus almacenes. */
class NovaSecretsProcessorTest {

    private final NovaSecretsProcessor processor = new NovaSecretsProcessor();

    @Test
    void everySecretSourceOnTheClasspathIsRegisteredForTheNativeImage() {
        var providers = processor.secretSourceProviders();

        assertThat(providers.serviceDescriptorFile())
                .isEqualTo("META-INF/services/" + SecretSourceProvider.class.getName());
        assertThat(providers.providers())
                .contains(EnvironmentSecretSourceProvider.class.getName(), VaultSecretSourceProvider.class.getName());
    }

    @Test
    void theSecretsAreLoadedOnlyByTheRuntimeConfiguration() {
        assertThat(processor.secretsConfigBuilder().getBuilderClassName())
                .isEqualTo(NovaSecretsConfigBuilder.class.getName());
        assertThat(processor.feature().getName()).isEqualTo("nova-secrets");
    }
}
