package pe.edu.nova.java.libs.secrets.aws;

import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSourceProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;

/**
 * Publica la fuente de AWS Secrets Manager con el nombre {@value #NAME}.
 *
 * <p>Su configuración va bajo {@code nova.secrets.aws-secrets-manager.*}:
 *
 * <ul>
 *   <li>{@code region}, que por defecto toma {@code AWS_REGION} y después la cadena del SDK;</li>
 *   <li>{@code endpoint}, solo para un emulador como Moto o LocalStack;</li>
 *   <li>{@code timeout}, por defecto {@code 5s}, que acota cada llamada con sus reintentos.</li>
 * </ul>
 *
 * <p>Las credenciales salen de la cadena por defecto del SDK: el entorno, un perfil o, dentro de
 * ECS, el rol de la tarea.
 */
public final class AwsSecretsManagerSecretSourceProvider implements SecretSourceProvider {

    /** El nombre de la fuente de AWS Secrets Manager. */
    public static final String NAME = "aws-secrets-manager";

    /** Crea el proveedor; lo instancia {@link java.util.ServiceLoader}. */
    public AwsSecretsManagerSecretSourceProvider() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SecretSource create(SecretSettings settings) {
        AwsSecretsManagerSettings aws = AwsSecretsManagerSettings.from(settings);
        SecretsManagerClientBuilder builder = SecretsManagerClient.builder()
                .httpClient(UrlConnectionHttpClient.builder()
                        .connectionTimeout(aws.timeout())
                        .socketTimeout(aws.timeout())
                        .build())
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .overrideConfiguration(configuration -> configuration.apiCallTimeout(aws.timeout()));
        aws.region().map(Region::of).ifPresent(builder::region);
        aws.endpoint().ifPresent(builder::endpointOverride);
        try {
            return new AwsSecretsManagerSecretSource(builder.build(), aws.timeout());
        } catch (SdkClientException e) {
            throw AwsSecretsManagerSettings.invalid("needs a region: set nova.secrets.aws-secrets-manager.region or AWS_REGION");
        }
    }
}
