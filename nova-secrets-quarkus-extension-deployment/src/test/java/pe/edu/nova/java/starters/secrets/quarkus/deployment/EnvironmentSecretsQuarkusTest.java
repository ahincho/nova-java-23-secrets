package pe.edu.nova.java.starters.secrets.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.QuarkusUnitTest;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/**
 * Una aplicación Quarkus que recibe su secreto como lo inyecta ECS: un JSON entero en una variable,
 * {@code CREDENTIALS_DB}, que el build de la prueba pone en el entorno junto a una variable suelta
 * {@code DB_PASSWORD}.
 */
class EnvironmentSecretsQuarkusTest {

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("nova.secrets.env.variables", "CREDENTIALS_DB")
            .overrideConfigKey("course.password", "${DB_PASSWORD}");

    @Test
    void theServiceReadsTheKeysOfTheSecretAsProperties() {
        Config config = ConfigProvider.getConfig();

        assertThat(config.getValue("DB_USERNAME", String.class)).isEqualTo("course");
        assertThat(config.getValue("db.username", String.class)).isEqualTo("course");
    }

    @Test
    void aSecretWinsOverALooseVariableWithTheSameName() {
        assertThat(ConfigProvider.getConfig().getValue("course.password", String.class))
                .isEqualTo("s3cr3t");
    }
}
