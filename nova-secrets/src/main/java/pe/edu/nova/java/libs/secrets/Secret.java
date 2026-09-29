package pe.edu.nova.java.libs.secrets;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Un secreto ya aplanado: cada clave es una propiedad de configuración y cada valor, su texto.
 *
 * <p>Su {@link #toString()} muestra la referencia y las claves, nunca los valores, para que un
 * secreto que termina en un log por descuido no filtre nada.
 */
public final class Secret {

    private static final JsonFactory JSON = JsonFactory.builder().build();

    private final String reference;
    private final Map<String, String> entries;

    private Secret(String reference, Map<String, String> entries) {
        this.reference = reference;
        this.entries = entries;
    }

    /**
     * Un secreto con claves y valores ya separados, como los que devuelve Vault.
     *
     * @param reference cómo lo nombró el servicio
     * @param entries   las claves y sus valores, que se copian en el orden en que llegan
     * @return el secreto
     */
    public static Secret of(String reference, Map<String, String> entries) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(entries, "entries");
        return new Secret(reference, Collections.unmodifiableMap(new LinkedHashMap<>(entries)));
    }

    /**
     * Abre un secreto que llega como un objeto JSON, que es como llega un secreto de AWS Secrets
     * Manager: tanto el que ECS inyecta entero en una variable como el que devuelve
     * {@code GetSecretValue}.
     *
     * <p>Cada clave de primer nivel con un valor escalar se vuelve una entrada. Un texto queda como
     * está; un número o un booleano, tal como está escrito. Una clave cuyo valor es un objeto, una
     * lista o {@code null} se ignora, en lugar de convertirse en un texto que pasaría cualquier
     * validación llevando basura. Si una clave se repite, gana la última, igual que en NestJS.
     *
     * @param reference cómo lo nombró el servicio, para el mensaje de error
     * @param json      el texto del secreto
     * @return el secreto aplanado
     * @throws SecretSourceException si el texto no es JSON o no es un objeto; el mensaje no cita el
     *                               texto y no lleva causa, porque la del parser lo citaría
     */
    public static Secret fromJson(String reference, String json) {
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(json, "json");
        Map<String, String> entries = new LinkedHashMap<>();
        try (JsonParser parser = JSON.createParser(json)) {
            if (parser.nextToken() != JsonToken.START_OBJECT) {
                throw new SecretSourceException(reference, "does not contain a JSON object");
            }
            JsonToken token = parser.nextToken();
            while (token == JsonToken.FIELD_NAME) {
                String key = parser.currentName();
                JsonToken value = parser.nextToken();
                if (value == null) {
                    throw new SecretSourceException(reference, "could not be parsed as JSON");
                }
                if (value.isScalarValue() && value != JsonToken.VALUE_NULL) {
                    entries.put(key, parser.getText());
                } else {
                    parser.skipChildren();
                }
                token = parser.nextToken();
            }
            if (parser.nextToken() != null) {
                throw new SecretSourceException(reference, "could not be parsed as JSON");
            }
        } catch (IOException ignored) {
            // La causa se descarta a propósito: el mensaje del parser cita el texto que no pudo leer.
            throw new SecretSourceException(reference, "could not be parsed as JSON");
        }
        return new Secret(reference, Collections.unmodifiableMap(entries));
    }

    /**
     * Cómo lo nombró el servicio.
     *
     * @return la referencia
     */
    public String reference() {
        return reference;
    }

    /**
     * Las claves y sus valores, en el orden en que llegaron.
     *
     * @return un mapa que no se puede modificar
     */
    public Map<String, String> entries() {
        return entries;
    }

    /**
     * Las claves, sin sus valores.
     *
     * @return las claves, en el orden en que llegaron
     */
    public Set<String> keys() {
        return entries.keySet();
    }

    /**
     * El valor de una clave.
     *
     * @param key la clave
     * @return el valor, o vacío si el secreto no la tiene
     */
    public Optional<String> get(String key) {
        return Optional.ofNullable(entries.get(key));
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Secret secret && reference.equals(secret.reference) && entries.equals(secret.entries);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reference, entries);
    }

    @Override
    public String toString() {
        return "Secret[reference=" + reference + ", keys=" + entries.keySet() + "]";
    }
}
