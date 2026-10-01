package pe.edu.nova.java.starters.secrets.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;

import io.quarkus.test.QuarkusUnitTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Con {@code nova.secrets.override=false}, una variable de entorno gana sobre el secreto. */
class NoOverrideQuarkusTest {

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("nova.secrets.env.variables", "CREDENTIALS_DB")
            .overrideConfigKey("nova.secrets.override", "false");

    @Test
    void theLooseVariableWins() {
        assertThat(ConfigProvider.getConfig().getValue("DB_PASSWORD", String.class))
                .isEqualTo("from-the-environment");
    }

    @Test
    void theSecretStillProvidesTheKeysNobodyElseSets() {
        assertThat(ConfigProvider.getConfig().getValue("DB_USERNAME", String.class))
                .isEqualTo("course");
    }
}
