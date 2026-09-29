package pe.edu.nova.java.libs.secrets.vault;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.util.Objects;
import java.util.Optional;
import pe.edu.nova.java.libs.secrets.Secret;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSourceException;

/**
 * Lee secretos del motor KV versión 2 de Vault.
 *
 * <p>La referencia es la ruta del secreto dentro del motor, como {@code ms-course} o
 * {@code team/ms-course}; el motor se configura aparte y por defecto es {@code secret}. Un secreto
 * que Vault no tiene devuelve vacío. Un permiso negado, un timeout o una respuesta que no se puede
 * leer cortan con un error que nombra la ruta y nunca cita la respuesta, porque la respuesta trae el
 * secreto.
 *
 * <p>Con AppRole, el inicio de sesión se hace una sola vez, la primera vez que se lee un secreto.
 * Los redireccionamientos no se siguen: el token viaja en una cabecera, y no debe llegar a una
 * dirección que el servicio no configuró.
 */
final class VaultSecretSource implements SecretSource {

    private static final JsonFactory JSON = JsonFactory.builder().build();

    private final VaultSettings settings;
    private final HttpClient http;
    private volatile String token;

    VaultSecretSource(VaultSettings settings, HttpClient http) {
        this.settings = settings;
        this.http = http;
        if (settings.authentication() instanceof VaultAuthentication.Token fixed) {
            this.token = fixed.token();
        }
    }

    @Override
    public Optional<Secret> find(String reference) {
        Objects.requireNonNull(reference, "reference");
        if (!VaultSettings.PATH.matcher(reference).matches()) {
            throw new SecretSourceException(reference, "is not a valid Vault path");
        }
        HttpRequest request = HttpRequest.newBuilder(endpoint(settings.mount() + "/data/" + reference))
                .timeout(settings.timeout())
                .header("X-Vault-Token", token(reference))
                .GET()
                .build();
        HttpResponse<String> response = send(request, reference);
        return switch (response.statusCode()) {
            case 200 -> data(reference, response.body());
            case 404 -> Optional.empty();
            case 403 ->
                throw new SecretSourceException(
                        reference, "was refused by Vault (403): the token or the AppRole cannot read it");
            default ->
                throw new SecretSourceException(
                        reference, "could not be read from Vault (status " + response.statusCode() + ")");
        };
    }

    private String token(String reference) {
        String current = token;
        if (current != null) {
            return current;
        }
        synchronized (this) {
            if (token == null) {
                token = login(reference, (VaultAuthentication.AppRole) settings.authentication());
            }
            return token;
        }
    }

    private String login(String reference, VaultAuthentication.AppRole appRole) {
        HttpRequest request = HttpRequest.newBuilder(endpoint("auth/" + appRole.mount() + "/login"))
                .timeout(settings.timeout())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(loginBody(appRole)))
                .build();
        HttpResponse<String> response = send(request, reference);
        if (response.statusCode() != 200) {
            throw new SecretSourceException(
                    reference,
                    "could not be read: the AppRole login was refused by Vault (status " + response.statusCode() + ")");
        }
        return clientToken(response.body())
                .orElseThrow(() -> new SecretSourceException(
                        reference, "could not be read: the AppRole login did not return a token"));
    }

    private HttpResponse<String> send(HttpRequest request, String reference) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (HttpTimeoutException e) {
            throw new SecretSourceException(
                    reference, "could not be read: Vault did not answer within " + settings.timeout(), e);
        } catch (IOException e) {
            throw new SecretSourceException(
                    reference, "could not be read: Vault is not reachable at " + settings.address(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SecretSourceException(reference, "could not be read: the thread was interrupted", e);
        }
    }

    private URI endpoint(String path) {
        return URI.create(settings.address() + "/v1/" + path);
    }

    /**
     * Saca el secreto de {@code {"data": {"data": {...}}}} y lo abre con {@link Secret#fromJson}, que
     * aplica las mismas reglas que a cualquier otra fuente.
     */
    private static Optional<Secret> data(String reference, String body) {
        try (JsonParser parser = JSON.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw unreadable(reference);
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if ("data".equals(field) && value == JsonToken.START_OBJECT) {
                    return innerData(reference, parser);
                }
                parser.skipChildren();
            }
            return Optional.empty();
        } catch (IOException ignored) {
            // La causa se descarta a propósito: el mensaje del parser cita la respuesta, que trae el secreto.
            throw unreadable(reference);
        }
    }

    private static Optional<Secret> innerData(String reference, JsonParser parser) throws IOException {
        while (parser.nextToken() == JsonToken.FIELD_NAME) {
            String field = parser.currentName();
            JsonToken value = parser.nextToken();
            if ("data".equals(field)) {
                if (value == JsonToken.VALUE_NULL) {
                    return Optional.empty();
                }
                if (value != JsonToken.START_OBJECT) {
                    throw unreadable(reference);
                }
                StringWriter json = new StringWriter();
                try (JsonGenerator generator = JSON.createGenerator(json)) {
                    generator.copyCurrentStructure(parser);
                }
                return Optional.of(Secret.fromJson(reference, json.toString()));
            }
            parser.skipChildren();
        }
        return Optional.empty();
    }

    private static Optional<String> clientToken(String body) {
        try (JsonParser parser = JSON.createParser(body)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                return Optional.empty();
            }
            while (parser.nextToken() == JsonToken.FIELD_NAME) {
                String field = parser.currentName();
                JsonToken value = parser.nextToken();
                if ("auth".equals(field) && value == JsonToken.START_OBJECT) {
                    while (parser.nextToken() == JsonToken.FIELD_NAME) {
                        String inner = parser.currentName();
                        JsonToken innerValue = parser.nextToken();
                        if ("client_token".equals(inner) && innerValue == JsonToken.VALUE_STRING) {
                            return Optional.of(parser.getText());
                        }
                        parser.skipChildren();
                    }
                    return Optional.empty();
                }
                parser.skipChildren();
            }
            return Optional.empty();
        } catch (IOException ignored) {
            // La respuesta del inicio de sesión trae un token: no se cita ni se encadena.
            return Optional.empty();
        }
    }

    private static String loginBody(VaultAuthentication.AppRole appRole) {
        StringWriter json = new StringWriter();
        try (JsonGenerator generator = JSON.createGenerator(json)) {
            generator.writeStartObject();
            generator.writeStringField("role_id", appRole.roleId());
            generator.writeStringField("secret_id", appRole.secretId());
            generator.writeEndObject();
        } catch (IOException e) {
            throw new SecretSourceException("source vault", "could not write the AppRole login");
        }
        return json.toString();
    }

    private static SecretSourceException unreadable(String reference) {
        return new SecretSourceException(reference, "could not be parsed from the Vault response");
    }
}
