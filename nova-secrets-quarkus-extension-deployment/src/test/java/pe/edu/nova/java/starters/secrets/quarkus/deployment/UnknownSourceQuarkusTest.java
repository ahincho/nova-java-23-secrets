package pe.edu.nova.java.starters.secrets.quarkus.deployment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import io.quarkus.test.QuarkusUnitTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Un almacén que no está entre las dependencias corta el arranque y dice cuál falta. */
class UnknownSourceQuarkusTest {

    @RegisterExtension
    static final QuarkusUnitTest APPLICATION = new QuarkusUnitTest()
            .withEmptyApplication()
            .overrideConfigKey("nova.secrets.import", "aws-secrets-manager:prod/ms-course/db")
            .assertException(error -> assertThat(error)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("No secret source named 'aws-secrets-manager'")
                    .hasMessageContaining("such as nova-secrets-vault"));

    @Test
    void theApplicationDoesNotStart() {
        fail("The application should not have started");
    }
}
