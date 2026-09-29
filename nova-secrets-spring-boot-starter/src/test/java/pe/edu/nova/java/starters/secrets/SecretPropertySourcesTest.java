package pe.edu.nova.java.starters.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

class SecretPropertySourcesTest {

    @Test
    void withOverrideTheSecretsGoRightBeforeTheEnvironmentInTheirOrder() {
        MutablePropertySources sources = sources();

        SecretPropertySources.position(sources, true);

        assertThat(names(sources))
                .containsExactly(
                        "commandLineArgs", "nova-secrets:a", "nova-secrets:b", "systemEnvironment", "application");
    }

    @Test
    void withoutOverrideTheSecretsGoRightAfterTheEnvironmentInTheirOrder() {
        MutablePropertySources sources = sources();

        SecretPropertySources.position(sources, false);

        assertThat(names(sources))
                .containsExactly(
                        "commandLineArgs", "systemEnvironment", "nova-secrets:a", "nova-secrets:b", "application");
    }

    @Test
    void aSecretBehavesLikeAnEnvironmentVariable() {
        PropertySource<?> source = SecretPropertySources.of("nova-secrets:x", Map.of("DB_PASSWORD", "s3cr3t"));

        assertThat(source.getProperty("db.password")).isEqualTo("s3cr3t");
        assertThat(SecretPropertySources.isSecret(source)).isTrue();
        assertThat(SecretPropertySources.isSecret(new MapPropertySource("application", Map.of())))
                .isFalse();
    }

    private static MutablePropertySources sources() {
        MutablePropertySources sources = new MutablePropertySources();
        sources.addLast(new MapPropertySource("commandLineArgs", Map.of()));
        sources.addLast(new MapPropertySource("nova-secrets:a", Map.of()));
        sources.addLast(new MapPropertySource("systemEnvironment", Map.of()));
        sources.addLast(new MapPropertySource("application", Map.of()));
        sources.addLast(new MapPropertySource("nova-secrets:b", Map.of()));
        return sources;
    }

    private static List<String> names(MutablePropertySources sources) {
        return sources.stream().map(PropertySource::getName).toList();
    }
}
