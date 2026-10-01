package pe.edu.nova.java.libs.secrets.env;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.secrets.Secret;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSourceException;

class EnvironmentSecretsTest {

    private static final String DB = "{\"username\": \"course\", \"password\": \"s3cr3t\"}";
    private static final String LEGACY = "{\"apiKey\": \"k\"}";

    @Test
    void nothingIsUnfoldedWhenNothingIsConfigured() {
        Map<String, String> environment = Map.of("CREDENTIALS_DB", DB);

        assertEquals(List.of(), EnvironmentSecrets.variables(SecretSettings.empty(), environment));
    }

    @Test
    void thePrefixHasNoDefault() {
        SecretSettings settings = SecretSettings.of(Map.of(EnvironmentSecrets.PREFIX_SETTING, "  "));

        assertEquals(List.of(), EnvironmentSecrets.variables(settings, Map.of("CREDENTIALS_DB", DB)));
    }

    @Test
    void namedVariablesComeFirstThenTheEscapeHatchThenThePrefixInAlphabeticalOrder() {
        SecretSettings settings = SecretSettings.of(Map.of(
                EnvironmentSecrets.VARIABLES_SETTING, "LEGACY_CREDENTIALS",
                EnvironmentSecrets.PREFIX_SETTING, "CREDENTIALS_"));
        Map<String, String> environment = Map.of(
                "CREDENTIALS_LEGACY",
                LEGACY,
                "CREDENTIALS_DB",
                DB,
                "LEGACY_CREDENTIALS",
                LEGACY,
                "OPS_ADDED",
                LEGACY,
                EnvironmentSecrets.VARIABLES_VARIABLE,
                "OPS_ADDED, CREDENTIALS_DB");

        assertEquals(
                List.of("LEGACY_CREDENTIALS", "OPS_ADDED", "CREDENTIALS_DB", "CREDENTIALS_LEGACY"),
                EnvironmentSecrets.variables(settings, environment));
    }

    @Test
    void theEscapeHatchIsNeverTakenAsASecretEvenWhenThePrefixMatchesIt() {
        SecretSettings settings = SecretSettings.of(Map.of(EnvironmentSecrets.PREFIX_SETTING, "NOVA_"));
        Map<String, String> environment =
                Map.of(EnvironmentSecrets.VARIABLES_VARIABLE, "CREDENTIALS_DB", "CREDENTIALS_DB", DB);

        assertEquals(List.of("CREDENTIALS_DB"), EnvironmentSecrets.variables(settings, environment));
    }

    @Test
    void unfoldReturnsTheSecretsThatArePresent() {
        SecretSettings settings = SecretSettings.of(
                Map.of(EnvironmentSecrets.VARIABLES_SETTING, "CREDENTIALS_DB,CREDENTIALS_MISSING,CREDENTIALS_BLANK"));
        Map<String, String> environment = Map.of("CREDENTIALS_DB", DB, "CREDENTIALS_BLANK", "   ");

        List<Secret> secrets = EnvironmentSecrets.unfold(settings, environment);

        assertEquals(1, secrets.size());
        assertEquals("CREDENTIALS_DB", secrets.getFirst().reference());
        assertEquals(
                Map.of("username", "course", "password", "s3cr3t"),
                secrets.getFirst().entries());
    }

    @Test
    void aVariableThatIsNotJsonStopsTheUnfolding() {
        SecretSettings settings = SecretSettings.of(Map.of(EnvironmentSecrets.PREFIX_SETTING, "CREDENTIALS_"));
        Map<String, String> environment = Map.of("CREDENTIALS_DB", "username=course password=s3cr3t");

        SecretSourceException error =
                assertThrows(SecretSourceException.class, () -> EnvironmentSecrets.unfold(settings, environment));

        assertEquals("CREDENTIALS_DB", error.reference());
        assertTrue(!error.getMessage().contains("s3cr3t"));
    }
}
