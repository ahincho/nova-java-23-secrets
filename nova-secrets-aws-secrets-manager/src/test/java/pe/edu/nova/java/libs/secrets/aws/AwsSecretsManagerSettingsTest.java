package pe.edu.nova.java.libs.secrets.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSourceException;

class AwsSecretsManagerSettingsTest {

    @Test
    void nothingIsRequiredBecauseTheSdkChainResolvesTheRest() {
        AwsSecretsManagerSettings settings = AwsSecretsManagerSettings.from(SecretSettings.empty());

        assertTrue(settings.region().isEmpty());
        assertTrue(settings.endpoint().isEmpty());
        assertEquals(Duration.ofSeconds(5), settings.timeout());
    }

    @Test
    void theRegionFallsBackToTheAwsConvention() {
        assertEquals(Optional.of("us-east-1"),
                AwsSecretsManagerSettings.from(SecretSettings.of(Map.of("aws.region", "us-east-1"))).region());
        assertEquals(Optional.of("sa-east-1"), AwsSecretsManagerSettings.from(SecretSettings.of(Map.of(
                "aws.region", "us-east-1", "nova.secrets.aws-secrets-manager.region", "sa-east-1"))).region());
    }

    @Test
    void anEndpointIsOnlyForAnEmulator() {
        AwsSecretsManagerSettings settings = AwsSecretsManagerSettings.from(SecretSettings.of(Map.of(
                "nova.secrets.aws-secrets-manager.endpoint", "http://localhost:5000",
                "nova.secrets.aws-secrets-manager.timeout", "2s")));

        assertEquals(Optional.of(URI.create("http://localhost:5000")), settings.endpoint());
        assertEquals(Duration.ofSeconds(2), settings.timeout());
    }

    @Test
    void anEndpointMustBeHttp() {
        SecretSourceException error = assertThrows(SecretSourceException.class, () -> AwsSecretsManagerSettings.from(
                SecretSettings.of(Map.of("nova.secrets.aws-secrets-manager.endpoint", "localhost:5000"))));

        assertEquals("Secret source aws-secrets-manager needs an http or https endpoint", error.getMessage());
    }
}
