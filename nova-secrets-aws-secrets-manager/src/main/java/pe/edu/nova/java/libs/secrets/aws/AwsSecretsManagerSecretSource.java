package pe.edu.nova.java.libs.secrets.aws;

import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import pe.edu.nova.java.libs.secrets.Secret;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSourceException;
import software.amazon.awssdk.core.exception.ApiCallAttemptTimeoutException;
import software.amazon.awssdk.core.exception.ApiCallTimeoutException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.ResourceNotFoundException;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * Lee secretos de AWS Secrets Manager.
 *
 * <p>La referencia es el nombre o el ARN del secreto. Un secreto que no existe devuelve vacío. Un
 * permiso negado, un timeout o un secreto guardado como binario cortan con un error que nombra el
 * secreto y el código de AWS, nunca el contenido.
 */
final class AwsSecretsManagerSecretSource implements SecretSource {

    private final SecretsManagerClient client;
    private final Duration timeout;

    AwsSecretsManagerSecretSource(SecretsManagerClient client, Duration timeout) {
        this.client = client;
        this.timeout = timeout;
    }

    @Override
    public Optional<Secret> find(String reference) {
        Objects.requireNonNull(reference, "reference");
        if (reference.isBlank()) {
            throw new SecretSourceException(reference, "is not a secret name or ARN");
        }
        GetSecretValueResponse response;
        try {
            response = client.getSecretValue(
                    GetSecretValueRequest.builder().secretId(reference).build());
        } catch (ResourceNotFoundException e) {
            return Optional.empty();
        } catch (SecretsManagerException e) {
            throw refused(reference, e);
        } catch (ApiCallTimeoutException | ApiCallAttemptTimeoutException e) {
            throw new SecretSourceException(
                    reference, "could not be read: AWS Secrets Manager did not answer within " + timeout, e);
        } catch (SdkClientException e) {
            throw unreachable(reference, e);
        }
        if (response.secretString() != null) {
            return Optional.of(Secret.fromJson(reference, response.secretString()));
        }
        throw new SecretSourceException(reference, "is stored as binary, which cannot be opened as properties");
    }

    private static SecretSourceException refused(String reference, SecretsManagerException e) {
        String code = e.awsErrorDetails() != null && e.awsErrorDetails().errorCode() != null
                ? e.awsErrorDetails().errorCode()
                : "status " + e.statusCode();
        if (e.statusCode() == 403 || "AccessDeniedException".equals(code)) {
            return new SecretSourceException(
                    reference, "was refused by AWS Secrets Manager (" + code + "): the role cannot read it");
        }
        return new SecretSourceException(reference, "could not be read from AWS Secrets Manager (" + code + ")");
    }

    /**
     * Un error del lado del cliente. Solo se encadena si viene de la red: otros, como uno al leer la
     * respuesta, podrían citarla.
     */
    private static SecretSourceException unreachable(String reference, SdkClientException e) {
        String message = String.valueOf(e.getMessage());
        if (message.contains("Unable to load credentials")) {
            return new SecretSourceException(
                    reference,
                    "could not be read: no AWS credentials were found in the environment, a profile or the task role");
        }
        if (e.getCause() instanceof IOException network) {
            return new SecretSourceException(
                    reference, "could not be read: AWS Secrets Manager is not reachable", network);
        }
        return new SecretSourceException(reference, "could not be read: the AWS client failed before an answer");
    }
}
