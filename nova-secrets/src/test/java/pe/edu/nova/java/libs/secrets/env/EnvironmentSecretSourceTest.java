package pe.edu.nova.java.libs.secrets.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.secrets.SecretSourceException;

class EnvironmentSecretSourceTest {

    private final EnvironmentSecretSource source = new EnvironmentSecretSource(Map.of(
            "CREDENTIALS_DB", "{\"password\": \"s3cr3t\"}",
            "CREDENTIALS_BLANK", "",
            "CREDENTIALS_BROKEN", "{\"password\": ")::get);

    @Test
    void findReadsTheJsonOfTheVariable() {
        assertEquals(Optional.of("s3cr3t"), source.find("CREDENTIALS_DB").flatMap(secret -> secret.get("password")));
    }

    @Test
    void anAbsentOrBlankVariableIsNotAnError() {
        assertTrue(source.find("CREDENTIALS_MISSING").isEmpty());
        assertTrue(source.find("CREDENTIALS_BLANK").isEmpty());
    }

    @Test
    void aMalformedVariableIsAnError() {
        SecretSourceException error =
                assertThrows(SecretSourceException.class, () -> source.find("CREDENTIALS_BROKEN"));

        assertEquals("Secret CREDENTIALS_BROKEN could not be parsed as JSON", error.getMessage());
    }
}
