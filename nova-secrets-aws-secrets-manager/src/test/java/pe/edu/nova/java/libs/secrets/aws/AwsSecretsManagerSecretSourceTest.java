package pe.edu.nova.java.libs.secrets.aws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.secrets.SecretSourceException;
import pe.edu.nova.java.libs.secrets.SecretSources;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.core.SdkBytes;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/** Pruebas con un cliente falso de Secrets Manager, para cubrir cada respuesta sin un emulador. */
class AwsSecretsManagerSecretSourceTest {

    private static final String CREDENTIALS_DB = "{\"username\": \"course\", \"password\": \"s3cr3t\", \"port\": 5432}";

    @Test
    void theProviderIsFoundByName() {
        assertEquals(
                "aws-secrets-manager",
                SecretSources.provider("aws-secrets-manager").name());
    }

    @Test
    void aJsonSecretIsOpenedIntoProperties() {
        AwsSecretsManagerSecretSource source = source(request ->
                GetSecretValueResponse.builder().secretString(CREDENTIALS_DB).build());

        assertEquals(
                Map.of("username", "course", "password", "s3cr3t", "port", "5432"),
                source.find("prod/ms-course/db").orElseThrow().entries());
    }

    @Test
    void theReferenceIsSentAsTheSecretId() {
        String[] asked = new String[1];
        AwsSecretsManagerSecretSource source = source(request -> {
            asked[0] = request.secretId();
            return GetSecretValueResponse.builder().secretString("{}").build();
        });

        source.find("arn:aws:secretsmanager:us-east-1:123456789012:secret:prod/ms-course/db-AbCdEf");

        assertEquals("arn:aws:secretsmanager:us-east-1:123456789012:secret:prod/ms-course/db-AbCdEf", asked[0]);
    }

    @Test
    void aSecretThatDoesNotExistIsEmpty() {
        AwsSecretsManagerSecretSource source = source(request -> {
            throw ResourceNotFoundException.builder()
                    .message("Secrets Manager can't find the specified secret.")
                    .build();
        });

        assertTrue(source.find("prod/missing").isEmpty());
    }

    @Test
    void aDeniedReadSaysTheRoleCannotReadIt() {
        AwsSecretsManagerSecretSource source = source(request -> {
            throw awsError("AccessDeniedException", 400);
        });

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> source.find("prod/ms-course/db"));

        assertEquals(
                "Secret prod/ms-course/db was refused by AWS Secrets Manager (AccessDeniedException): the role cannot read it",
                error.getMessage());
    }

    @Test
    void anyOtherErrorNamesTheAwsCode() {
        AwsSecretsManagerSecretSource source = source(request -> {
            throw awsError("DecryptionFailure", 400);
        });

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> source.find("prod/ms-course/db"));

        assertEquals(
                "Secret prod/ms-course/db could not be read from AWS Secrets Manager (DecryptionFailure)",
                error.getMessage());
    }

    @Test
    void aBinarySecretCannotBeOpened() {
        AwsSecretsManagerSecretSource source = source(request -> GetSecretValueResponse.builder()
                .secretBinary(SdkBytes.fromUtf8String(CREDENTIALS_DB))
                .build());

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> source.find("prod/ms-course/db"));

        assertEquals(
                "Secret prod/ms-course/db is stored as binary, which cannot be opened as properties",
                error.getMessage());
    }

    @Test
    void aSecretThatIsNotJsonIsRejectedWithoutQuotingIt() {
        AwsSecretsManagerSecretSource source = source(request ->
                GetSecretValueResponse.builder().secretString("s3cr3t").build());

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> source.find("prod/api-key"));

        assertEquals("Secret prod/api-key could not be parsed as JSON", error.getMessage());
        assertFalse(error.getMessage().contains("s3cr3t"));
    }

    @Test
    void aTimeoutSaysHowLongItWaited() {
        AwsSecretsManagerSecretSource source = source(request -> {
            throw ApiCallTimeoutException.builder()
                    .message("Client execution did not complete before the specified timeout")
                    .build();
        });

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> source.find("prod/ms-course/db"));

        assertEquals(
                "Secret prod/ms-course/db could not be read: AWS Secrets Manager did not answer within PT2S",
                error.getMessage());
    }

    @Test
    void missingCredentialsAreNamedAsSuch() {
        AwsSecretsManagerSecretSource source = source(request -> {
            throw SdkClientException.builder()
                    .message("Unable to load credentials from any of the providers in the chain")
                    .build();
        });

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> source.find("prod/ms-course/db"));

        assertEquals(
                "Secret prod/ms-course/db could not be read: no AWS credentials were found in the environment, "
                        + "a profile or the task role",
                error.getMessage());
    }

    @Test
    void aNetworkFailureKeepsItsCause() {
        IOException network = new IOException("Connection refused");
        AwsSecretsManagerSecretSource source = source(request -> {
            throw SdkClientException.builder()
                    .message("Unable to execute HTTP request")
                    .cause(network)
                    .build();
        });

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> source.find("prod/ms-course/db"));

        assertEquals(
                "Secret prod/ms-course/db could not be read: AWS Secrets Manager is not reachable", error.getMessage());
        assertEquals(network, error.getCause());
    }

    private static AwsSecretsManagerSecretSource source(
            Function<GetSecretValueRequest, GetSecretValueResponse> answer) {
        SecretsManagerClient client = new SecretsManagerClient() {
            @Override
            public GetSecretValueResponse getSecretValue(GetSecretValueRequest request) {
                return answer.apply(request);
            }

            @Override
            public String serviceName() {
                return "secretsmanager";
            }

            @Override
            public void close() {}
        };
        return new AwsSecretsManagerSecretSource(client, Duration.ofSeconds(2));
    }

    private static SecretsManagerException awsError(String code, int status) {
        return (SecretsManagerException) SecretsManagerException.builder()
                .statusCode(status)
                .awsErrorDetails(AwsErrorDetails.builder()
                        .errorCode(code)
                        .errorMessage("denied")
                        .build())
                .build();
    }
}
