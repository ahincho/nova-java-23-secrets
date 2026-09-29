package pe.edu.nova.java.libs.secrets.aws;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSourceException;

/**
 * La configuración del adaptador, leída de {@link SecretSettings}.
 *
 * <p>La región sale de {@code nova.secrets.aws-secrets-manager.region}, después de
 * {@code aws.region} —que un framework encuentra como {@code AWS_REGION}— y, si ninguna está, de la
 * cadena por defecto del SDK. Las credenciales salen siempre de esa cadena, que dentro de ECS toma
 * el rol de la tarea: el adaptador no recibe ninguna credencial por configuración.
 */
record AwsSecretsManagerSettings(Optional<String> region, Optional<URI> endpoint, Duration timeout) {

    static final String PREFIX = "nova.secrets.aws-secrets-manager.";
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    static AwsSecretsManagerSettings from(SecretSettings settings) {
        Optional<String> region = settings.get(PREFIX + "region")
                .filter(value -> !value.isBlank())
                .or(() -> settings.get("aws.region").filter(value -> !value.isBlank()));
        Optional<URI> endpoint = settings.get(PREFIX + "endpoint")
                .filter(value -> !value.isBlank())
                .map(AwsSecretsManagerSettings::endpoint);
        Duration timeout = settings.getDuration(PREFIX + "timeout").orElse(DEFAULT_TIMEOUT);
        return new AwsSecretsManagerSettings(region, endpoint, timeout);
    }

    private static URI endpoint(String value) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (IllegalArgumentException e) {
            throw invalid("has an endpoint that is not a URI");
        }
        if (uri.getScheme() == null
                || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
            throw invalid("needs an http or https endpoint");
        }
        return uri;
    }

    static SecretSourceException invalid(String reason) {
        return new SecretSourceException("source aws-secrets-manager", reason);
    }
}
