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

    private static final String CREDENTIALS_DB = "{\"DB_USERNAME\": \"course\", \"DB_PASSWORD\": \"fresh\"}";

    private final NovaSecretsEnvironmentPostProcessor processor =
            new NovaSecretsEnvironmentPostProcessor(supplier -> supplier.get());

    @Test
    void theSecretsOfThePrefixBecomeProperties() {
        StandardEnvironment environment = environment(
                Map.of("CREDENTIALS_DB", CREDENTIALS_DB), Map.of("nova.secrets.env.prefix", "CREDENTIALS_"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("DB_USERNAME")).isEqualTo("course");
        assertThat(environment.getProperty("db.username")).isEqualTo("course");
        assertThat(Binder.get(environment).bind("db.password", String.class).get())
                .isEqualTo("fresh");
    }

    @Test
    void aSecretWinsOverALooseVariableWithTheSameName() {
        StandardEnvironment environment = environment(
                Map.of("CREDENTIALS_DB", CREDENTIALS_DB, "DB_PASSWORD", "stale"),
                Map.of("nova.secrets.env.prefix", "CREDENTIALS_"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("fresh");
    }

    @Test
    void withoutOverrideTheLooseVariableWins() {
        StandardEnvironment environment = environment(
                Map.of("CREDENTIALS_DB", CREDENTIALS_DB, "DB_PASSWORD", "stale"),
                Map.of("nova.secrets.env.prefix", "CREDENTIALS_", "nova.secrets.override", "false"));

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
        StandardEnvironment environment = environment(Map.of("CREDENTIALS_DB", CREDENTIALS_DB), Map.of());

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment
                        .getPropertySources()
                        .contains(NovaSecretsEnvironmentPostProcessor.ENVIRONMENT_SOURCE_NAME))
                .isFalse();
        assertThat(environment.getProperty("DB_PASSWORD")).isNull();
    }

    @Test
    void aVariableThatIsNotJsonStopsTheStartupWithoutTheContent() {
        StandardEnvironment environment = environment(
                Map.of("CREDENTIALS_DB", "password=fresh"), Map.of("nova.secrets.env.prefix", "CREDENTIALS_"));

        assertThatThrownBy(() -> processor.postProcessEnvironment(environment, new SpringApplication()))
                .isInstanceOf(SecretSourceException.class)
                .hasMessage("Secret CREDENTIALS_DB could not be parsed as JSON");
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

    @Test
    void novaSecretsImportReadsAStoreAsInQuarkusAndNestJs() {
        StandardEnvironment environment =
                environment(Map.of("DB_PASSWORD", "stale"), Map.of("nova.secrets.import", "fake:ms-course"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("DB_USERNAME")).isEqualTo("course");
        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("s3cr3t");
    }

    @Test
    void operationsAsksForAStoreWithTheSameVariableInEveryStack() {
        StandardEnvironment environment = environment(Map.of("NOVA_SECRETS_IMPORT", "fake:ms-course"), Map.of());

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("db.username")).isEqualTo("course");
    }

    @Test
    void theLaterImportWinsAndEveryImportBeatsTheEnvironmentSecrets() {
        StandardEnvironment environment = environment(
                Map.of("CREDENTIALS_DB", CREDENTIALS_DB),
                Map.of(
                        "nova.secrets.env.prefix", "CREDENTIALS_",
                        "nova.secrets.import", "fake:ms-course, fake:ms-course-v2"));

        processor.postProcessEnvironment(environment, new SpringApplication());

        assertThat(environment.getProperty("DB_PASSWORD")).isEqualTo("rotated");
        assertThat(environment.getProperty("DB_USERNAME")).isEqualTo("course");
    }

    @Test
    void aMissingImportStopsTheStartupUnlessItIsOptional() {
        StandardEnvironment required = environment(Map.of(), Map.of("nova.secrets.import", "fake:missing"));
        StandardEnvironment optional = environment(Map.of(), Map.of("nova.secrets.import", "optional:fake:missing"));

        assertThatThrownBy(() -> processor.postProcessEnvironment(required, new SpringApplication()))
                .isInstanceOf(SecretSourceException.class)
                .hasMessage("Secret fake:missing does not exist; write optional:fake:missing if it may");
        processor.postProcessEnvironment(optional, new SpringApplication());
        assertThat(optional.getProperty("DB_PASSWORD")).isNull();
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
