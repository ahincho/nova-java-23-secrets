package pe.edu.nova.java.starters.secrets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.Banner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import pe.edu.nova.java.libs.secrets.SecretSourceException;

class NovaSecretsConfigDataTest {

    @Configuration(proxyBeanMethods = false)
    static class EmptyApplication {
    }

    @BeforeEach
    void resetCounter() {
        FakeSecretSourceProvider.CREATED.set(0);
    }

    @Test
    void anImportedSecretBecomesProperties() {
        try (ConfigurableApplicationContext context = run("--spring.config.import=nova-secrets:fake:ms-course")) {
            assertThat(context.getEnvironment().getProperty("DB_PASSWORD")).isEqualTo("s3cr3t");
            assertThat(context.getEnvironment().getPropertySources().contains("nova-secrets:fake:ms-course")).isTrue();
        }
    }

    @Test
    void anImportedSecretBindsLikeAnEnvironmentVariable() {
        try (ConfigurableApplicationContext context = run("--spring.config.import=nova-secrets:fake:ms-course")) {
            assertThat(Binder.get(context.getEnvironment()).bind("db.username", String.class).get()).isEqualTo("course");
        }
    }

    @Test
    void theSourceIsCreatedOnceForEveryImportOfTheSameStore() {
        try (ConfigurableApplicationContext context =
                     run("--spring.config.import=nova-secrets:fake:ms-course,nova-secrets:fake:legacy")) {
            assertThat(context.getEnvironment().getProperty("LEGACY_API_KEY")).isEqualTo("k");
            assertThat(FakeSecretSourceProvider.CREATED).hasValue(1);
        }
    }

    @Test
    void theStoreReadsItsSettingsFromTheConfiguration() {
        try (ConfigurableApplicationContext context =
                     run("--fake.greeting=hola", "--spring.config.import=nova-secrets:fake:settings")) {
            assertThat(context.getEnvironment().getProperty("GREETING")).isEqualTo("hola");
        }
    }

    @Test
    void anOptionalSecretThatDoesNotExistIsSkipped() {
        try (ConfigurableApplicationContext context = run("--spring.config.import=optional:nova-secrets:fake:missing")) {
            assertThat(context.getEnvironment().getPropertySources().contains("nova-secrets:fake:missing")).isFalse();
        }
    }

    @Test
    void aRequiredSecretThatDoesNotExistStopsTheStartup() {
        assertThatThrownBy(() -> run("--spring.config.import=nova-secrets:fake:missing"))
                .satisfies(error -> assertThat(cause(error, ConfigDataResourceNotFoundException.class))
                        .hasMessageContaining("nova-secrets:fake:missing"));
    }

    @Test
    void aStoreErrorStopsTheStartupWithoutTheContent() {
        assertThatThrownBy(() -> run("--spring.config.import=nova-secrets:fake:broken"))
                .satisfies(error -> assertThat(cause(error, SecretSourceException.class))
                        .hasMessage("Secret broken could not be parsed as JSON"));
    }

    @Test
    void anUnknownStoreListsTheAvailableOnes() {
        assertThatThrownBy(() -> run("--spring.config.import=nova-secrets:key-vault:ms-course"))
                .satisfies(error -> assertThat(cause(error, IllegalArgumentException.class))
                        .hasMessageStartingWith("No secret source named 'key-vault'. Available: [env, fake, vault]"));
    }

    @Test
    void aStoreWithoutItsSettingsSaysWhatIsMissing() {
        assertThatThrownBy(() -> run("--spring.config.import=nova-secrets:vault:ms-course"))
                .satisfies(error -> assertThat(cause(error, SecretSourceException.class))
                        .hasMessage("Secret source vault needs an address: set nova.secrets.vault.address or VAULT_ADDR"));
    }

    @Test
    void anImportWithoutAReferenceIsRejected() {
        assertThatThrownBy(() -> run("--spring.config.import=nova-secrets:fake"))
                .satisfies(error -> assertThat(cause(error, IllegalArgumentException.class))
                        .hasMessageContaining("nova-secrets:<source>:<reference>"));
    }

    private static ConfigurableApplicationContext run(String... args) {
        SpringApplication application = new SpringApplication(EmptyApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setBannerMode(Banner.Mode.OFF);
        return application.run(args);
    }

    private static <T extends Throwable> T cause(Throwable error, Class<T> type) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return type.cast(current);
            }
        }
        throw new AssertionError("No " + type.getSimpleName() + " in the cause chain of " + error, error);
    }
}
