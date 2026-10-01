package pe.edu.nova.java.starters.secrets.quarkus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigValue;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.secrets.SecretSourceException;

/** Las reglas de ADR-042 en la fábrica de Quarkus, sin levantar una aplicación. */
class NovaSecretsConfigSourceFactoryTest {

    private static final String DB_JSON = "{\"DB_USERNAME\":\"course\",\"DB_PASSWORD\":\"s3cr3t\",\"DB_PORT\":5432}";

    @Test
    void aVariableThatTheServiceNamesIsUnfoldedWithTheOverrideOrdinal() {
        ConfigSource source = single(
                sources(Map.of("nova.secrets.env.variables", "CREDENTIALS_DB"), Map.of("CREDENTIALS_DB", DB_JSON)));

        assertThat(source.getName()).isEqualTo("nova-secrets:env");
        assertThat(source.getOrdinal()).isEqualTo(NovaSecretsConfigSourceFactory.OVERRIDE_ORDINAL);
        assertThat(source.getValue("DB_PASSWORD")).isEqualTo("s3cr3t");
        assertThat(source.getValue("DB_PORT")).isEqualTo("5432");
    }

    @Test
    void aKeyShapedLikeAVariableAlsoAnswersToItsPropertyName() {
        ConfigSource source = single(
                sources(Map.of("nova.secrets.env.variables", "CREDENTIALS_DB"), Map.of("CREDENTIALS_DB", DB_JSON)));

        assertThat(source.getValue("db.password")).isEqualTo("s3cr3t");
    }

    @Test
    void withoutOverrideAnEnvironmentVariableWinsButTheSecretStillBeatsTheApplicationProperties() {
        ConfigSource source = single(sources(
                Map.of("nova.secrets.env.variables", "CREDENTIALS_DB", "nova.secrets.override", "false"),
                Map.of("CREDENTIALS_DB", DB_JSON)));

        assertThat(source.getOrdinal())
                .isEqualTo(NovaSecretsConfigSourceFactory.NO_OVERRIDE_ORDINAL)
                .isLessThan(300)
                .isGreaterThan(250);
    }

    @Test
    void theOperationsVariableAddsASecretWithoutTouchingTheCode() {
        ConfigSource source =
                single(sources(Map.of(), Map.of("NOVA_SECRETS", "CREDENTIALS_DB", "CREDENTIALS_DB", DB_JSON)));

        assertThat(source.getValue("DB_USERNAME")).isEqualTo("course");
    }

    @Test
    void novaHasNoDefaultPrefix() {
        // Un prefijo de descubrimiento es de la organización y lo pone su perfil, nunca Nova.
        assertThat(sources(Map.of(), Map.of("CREDENTIALS_DB", DB_JSON))).isEmpty();
    }

    @Test
    void aMalformedSecretStopsTheStartupWithoutQuotingIt() {
        assertThatThrownBy(() -> sources(
                        Map.of("nova.secrets.env.variables", "CREDENTIALS_DB"),
                        Map.of("CREDENTIALS_DB", "{\"DB_PASSWORD\": s3cr3t")))
                .isInstanceOf(SecretSourceException.class)
                .hasMessage("Secret CREDENTIALS_DB could not be parsed as JSON")
                .hasNoCause();
    }

    @Test
    void anImportReadsTheStoreWithTheSettingsOfTheService() {
        ConfigSource source = single(sources(
                Map.of("nova.secrets.import", "memory:ms-course", "nova.secrets.memory.password", "from-settings"),
                Map.of()));

        assertThat(source.getName()).isEqualTo("nova-secrets:memory:ms-course");
        assertThat(source.getValue("DB_PASSWORD")).isEqualTo("from-settings");
    }

    @Test
    void importsBeatTheEnvironmentAndTheLaterImportWins() {
        List<ConfigSource> sources = sources(
                Map.of(
                        "nova.secrets.env.variables", "CREDENTIALS_DB",
                        "nova.secrets.import", "memory:ms-course, memory:ms-course-override"),
                Map.of("CREDENTIALS_DB", DB_JSON));

        assertThat(sources)
                .extracting(ConfigSource::getName)
                .containsExactly(
                        "nova-secrets:env", "nova-secrets:memory:ms-course", "nova-secrets:memory:ms-course-override");
        assertThat(winner(sources, "DB_PASSWORD")).isEqualTo("overridden");
    }

    @Test
    void aMissingImportStopsTheStartupUnlessItIsOptional() {
        assertThatThrownBy(() -> sources(Map.of("nova.secrets.import", "memory:missing"), Map.of()))
                .isInstanceOf(SecretSourceException.class)
                .hasMessage("Secret memory:missing does not exist; write optional:memory:missing if it may");
        assertThat(sources(Map.of("nova.secrets.import", "optional:memory:missing"), Map.of()))
                .isEmpty();
    }

    @Test
    void anUnknownSourceNamesTheDependencyThatIsMissing() {
        assertThatThrownBy(() -> sources(Map.of("nova.secrets.import", "vault:ms-course"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("such as nova-secrets-vault");
    }

    @Test
    void theSourceNeverPrintsAValue() {
        ConfigSource source = single(
                sources(Map.of("nova.secrets.env.variables", "CREDENTIALS_DB"), Map.of("CREDENTIALS_DB", DB_JSON)));

        assertThat(source.toString()).contains("DB_PASSWORD").doesNotContain("s3cr3t");
    }

    private static List<ConfigSource> sources(Map<String, String> config, Map<String, String> environment) {
        Iterable<ConfigSource> sources =
                new NovaSecretsConfigSourceFactory(() -> environment).getConfigSources(context(config));
        return StreamSupport.stream(sources.spliterator(), false).toList();
    }

    private static ConfigSource single(List<ConfigSource> sources) {
        assertThat(sources).hasSize(1);
        return sources.getFirst();
    }

    private static String winner(List<ConfigSource> sources, String key) {
        return sources.stream()
                .filter(source -> source.getValue(key) != null)
                .max((a, b) -> Integer.compare(a.getOrdinal(), b.getOrdinal()))
                .orElseThrow()
                .getValue(key);
    }

    private static ConfigSourceContext context(Map<String, String> config) {
        return new ConfigSourceContext() {
            @Override
            public ConfigValue getValue(String name) {
                String value = config.get(name);
                return value == null
                        ? null
                        : ConfigValue.builder().withName(name).withValue(value).build();
            }

            @Override
            public Iterator<String> iterateNames() {
                return config.keySet().iterator();
            }
        };
    }
}
