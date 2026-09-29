package pe.edu.nova.java.starters.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import pe.edu.nova.java.libs.secrets.SecretSourceException;

class NovaSecretsEnvironmentPostProcessorTest {

    private static final String SECRET_DB = "{\"DB_USERNAME\": \"course\", \"DB_PASSWORD\": \"fresh\"}";

    private final NovaSecretsEnvironmentPostProcessor processor =
            new NovaSecretsEnvironmentPostProcessor(supplier -> supplier.get());

    @Test
    void theSecretsOfThePrefixBecomeProperties() {
        StandardEnvironment environment =
                environment(Map.of("SECRET_DB", SECRET_DB), Map.of("nova.secrets.env.prefix", "SECRET_"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("DB_USERNAME")).isEqualTo("course");
        assertThat(environment.getProperty("db.username")).isEqualTo("course");
        assertThat(Binder.get(environment).bind("db.password", String.class).get())
                .isEqualTo("fresh");
    }

    @Test
    void aSecretWinsOverALooseVariableWithTheSameName() {
        StandardEnvironment environment = environment(
                Map.of("SECRET_DB", SECRET_DB, "DB_PASSWORD", "stale"), Map.of("nova.secrets.env.prefix", "SECRET_"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("fresh");
    }

    @Test
    void withoutOverrideTheLooseVariableWins() {
        StandardEnvironment environment = environment(
                Map.of("SECRET_DB", SECRET_DB, "DB_PASSWORD", "stale"),
                Map.of("nova.secrets.env.prefix", "SECRET_", "nova.secrets.override", "false"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("stale");
        assertThat(environment.getProperty("DB_USERNAME")).isEqualTo("course");
    }

    @Test
    void theEscapeHatchAddsASecretWithoutConfiguration() {
        StandardEnvironment environment =
                environment(Map.of("LEGACY", "{\"API_KEY\": \"k\"}", "NOVA_SECRETS", "LEGACY"), Map.of());

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("API_KEY")).isEqualTo("k");
    }

    @Test
    void nothingIsAddedWhenThereIsNothingToUnfold() {
        StandardEnvironment environment = environment(Map.of("SECRET_DB", SECRET_DB), Map.of());

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment
                        .getPropertySources()
                        .contains(NovaSecretsEnvironmentPostProcessor.ENVIRONMENT_SOURCE_NAME))
                .isFalse();
        assertThat(environment.getProperty("DB_PASSWORD")).isNull();
    }

    @Test
    void aVariableThatIsNotJsonStopsTheStartupWithoutTheContent() {
        StandardEnvironment environment =
                environment(Map.of("SECRET_DB", "password=fresh"), Map.of("nova.secrets.env.prefix", "SECRET_"));

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(SecretSourceException.class)
                .hasMessage("Secret SECRET_DB could not be parsed as JSON");
    }

    @Test
    void importedSecretsAreMovedAboveTheEnvironmentToo() {
        StandardEnvironment environment = environment(Map.of("LEGACY_API_KEY", "stale"), Map.of());
        environment
                .getPropertySources()
                .addLast(SecretPropertySources.of("nova-secrets:fake:legacy", Map.of("LEGACY_API_KEY", "k")));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("LEGACY_API_KEY")).isEqualTo("k");
    }

    private static StandardEnvironment environment(Map<String, String> variables, Map<String, String> application) {
        StandardEnvironment environment = new StandardEnvironment();
        environment
                .getPropertySources()
                .replace(
                        StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                        new SystemEnvironmentPropertySource(
                                StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, new HashMap<>(variables)));
        environment.getPropertySources().addLast(new MapPropertySource("application", new HashMap<>(application)));
        return environment;
    }
}
