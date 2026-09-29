package pe.edu.nova.java.libs.secrets.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSourceException;

class VaultSettingsTest {

    @Test
    void theDefaultsFollowTheDevelopmentServer() {
        VaultSettings settings = VaultSettings.from(settings(Map.of(
                "nova.secrets.vault.address", "http://127.0.0.1:8200/", "nova.secrets.vault.token", "t")));

        assertEquals(URI.create("http://127.0.0.1:8200"), settings.address());
        assertEquals("secret", settings.mount());
        assertEquals(Duration.ofSeconds(5), settings.timeout());
        assertInstanceOf(VaultAuthentication.Token.class, settings.authentication());
    }

    @Test
    void theAddressAndTokenFallBackToTheVaultConvention() {
        VaultSettings settings = VaultSettings.from(settings(Map.of("vault.addr", "https://vault.internal", "vault.token", "t")));

        assertEquals(URI.create("https://vault.internal"), settings.address());
        assertEquals(new VaultAuthentication.Token("t"), settings.authentication());
    }

    @Test
    void anAppRoleWinsOverAToken() {
        VaultSettings settings = VaultSettings.from(settings(Map.of(
                "vault.addr", "http://vault:8200", "vault.token", "t",
                "nova.secrets.vault.app-role.role-id", "role", "nova.secrets.vault.app-role.secret-id", "id")));

        assertEquals(new VaultAuthentication.AppRole("role", "id", "approle"), settings.authentication());
    }

    @Test
    void anAddressIsRequired() {
        SecretSourceException error = assertThrows(SecretSourceException.class,
                () -> VaultSettings.from(settings(Map.of("vault.token", "t"))));

        assertEquals("Secret source vault needs an address: set nova.secrets.vault.address or VAULT_ADDR", error.getMessage());
    }

    @Test
    void anAddressMustBeHttp() {
        assertThrows(SecretSourceException.class,
                () -> VaultSettings.from(settings(Map.of("vault.addr", "ftp://vault", "vault.token", "t"))));
    }

    @Test
    void someAuthenticationIsRequired() {
        SecretSourceException error = assertThrows(SecretSourceException.class,
                () -> VaultSettings.from(settings(Map.of("vault.addr", "http://vault:8200"))));

        assertTrue(error.getMessage().contains("needs a token or an AppRole"), error.getMessage());
    }

    @Test
    void aRoleIdNeedsItsSecretId() {
        assertThrows(SecretSourceException.class, () -> VaultSettings.from(settings(Map.of(
                "vault.addr", "http://vault:8200", "nova.secrets.vault.app-role.role-id", "role"))));
    }

    @Test
    void aMountMustBeAVaultPath() {
        assertThrows(SecretSourceException.class, () -> VaultSettings.from(settings(Map.of(
                "vault.addr", "http://vault:8200", "vault.token", "t", "nova.secrets.vault.mount", "../sys"))));
    }

    @Test
    void timeoutsAcceptTheShortAndTheIsoForms() {
        assertEquals(Duration.ofMillis(500), VaultSettings.duration("500ms"));
        assertEquals(Duration.ofSeconds(2), VaultSettings.duration("2s"));
        assertEquals(Duration.ofMinutes(1), VaultSettings.duration("1m"));
        assertEquals(Duration.ofSeconds(3), VaultSettings.duration("PT3S"));
        assertThrows(SecretSourceException.class, () -> VaultSettings.duration("soon"));
    }

    @Test
    void credentialsNeverAppearInToString() {
        VaultSettings token = VaultSettings.from(settings(Map.of("vault.addr", "http://vault:8200", "vault.token", "s.root-token")));
        VaultSettings appRole = VaultSettings.from(settings(Map.of("vault.addr", "http://vault:8200",
                "nova.secrets.vault.app-role.role-id", "my-role-id", "nova.secrets.vault.app-role.secret-id", "my-secret-id")));

        assertFalse(token.toString().contains("s.root-token"), token.toString());
        assertFalse(appRole.toString().contains("my-role-id"), appRole.toString());
        assertFalse(appRole.toString().contains("my-secret-id"), appRole.toString());
    }

    private static SecretSettings settings(Map<String, String> values) {
        return SecretSettings.of(new HashMap<>(values));
    }
}
