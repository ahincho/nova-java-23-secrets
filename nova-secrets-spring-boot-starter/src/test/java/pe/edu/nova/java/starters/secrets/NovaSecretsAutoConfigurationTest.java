package pe.edu.nova.java.starters.secrets;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;

class NovaSecretsAutoConfigurationTest {

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(NovaSecretsAutoConfiguration.class));

    @Test
    void theSanitizingFunctionIsRegistered() {
        runner.run(context -> assertThat(context).hasSingleBean(SanitizingFunction.class));
    }

    @Test
    void aValueThatComesFromASecretIsHidden() {
        SanitizableData data = new SanitizableData(
                SecretPropertySources.of("nova-secrets:env", Map.of("DB_PASSWORD", "s3cr3t")), "DB_PASSWORD", "s3cr3t");

        assertThat(NovaSecretsAutoConfiguration.sanitize(data).getValue()).isEqualTo(SanitizableData.SANITIZED_VALUE);
    }

    @Test
    void aValueFromAnyOtherSourceIsLeftAlone() {
        SanitizableData data =
                new SanitizableData(new MapPropertySource("application", Map.of()), "server.port", "8080");

        assertThat(NovaSecretsAutoConfiguration.sanitize(data).getValue()).isEqualTo("8080");
    }
}
