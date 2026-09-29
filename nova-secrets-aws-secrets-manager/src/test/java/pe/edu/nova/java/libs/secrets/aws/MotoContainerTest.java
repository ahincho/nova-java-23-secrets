package pe.edu.nova.java.libs.secrets.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.net.URI;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSources;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

/**
 * Contra Moto, un emulador de AWS de código abierto que no pide cuenta. Se salta si la máquina no
 * tiene Docker.
 */
class MotoContainerTest {

    private static final String SECRET_DB =
            "{\"DB_USERNAME\": \"course\", \"DB_PASSWORD\": \"s3cr3t\", \"port\": 5432}";

    private static GenericContainer<?> moto;

    @BeforeAll
    static void start() {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker is not available");
        moto = new GenericContainer<>("motoserver/moto:5.2.3")
                .withExposedPorts(5000)
                .waitingFor(Wait.forHttp("/moto-api/").forStatusCode(200));
        moto.start();
        // Las credenciales del adaptador salen de la cadena del SDK; en la prueba, de estas propiedades.
        System.setProperty("aws.accessKeyId", "test");
        System.setProperty("aws.secretAccessKey", "test");
        try (SecretsManagerClient setup = SecretsManagerClient.builder()
                .httpClient(UrlConnectionHttpClient.create())
                .region(Region.US_EAST_1)
                .endpointOverride(endpoint())
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .build()) {
            setup.createSecret(request -> request.name("prod/ms-course/db").secretString(SECRET_DB));
        }
    }

    @AfterAll
    static void stop() {
        System.clearProperty("aws.accessKeyId");
        System.clearProperty("aws.secretAccessKey");
        if (moto != null) {
            moto.stop();
        }
    }

    @Test
    void aSecretIsReadAndOpenedLikeTheOneEcsInjects() {
        SecretSource source = SecretSources.provider("aws-secrets-manager")
                .create(SecretSettings.of(Map.of(
                        "nova.secrets.aws-secrets-manager.region",
                        "us-east-1",
                        "nova.secrets.aws-secrets-manager.endpoint",
                        endpoint().toString())));

        assertEquals(
                Map.of("DB_USERNAME", "course", "DB_PASSWORD", "s3cr3t", "port", "5432"),
                source.find("prod/ms-course/db").orElseThrow().entries());
        assertTrue(source.find("prod/missing").isEmpty());
    }

    private static URI endpoint() {
        return URI.create("http://" + moto.getHost() + ":" + moto.getMappedPort(5000));
    }
}
