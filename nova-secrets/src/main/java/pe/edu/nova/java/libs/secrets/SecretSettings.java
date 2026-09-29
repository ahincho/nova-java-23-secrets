package pe.edu.nova.java.libs.secrets;

import java.time.Duration;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La configuración de una fuente, leída sin saber de qué framework viene.
 *
 * <p>La implementan los conectores: en Spring Boot lee del {@code Environment}, en Quarkus de
 * MicroProfile Config. Es lo que permite que un adaptador lea la dirección de su almacén sin
 * importar ninguno de los dos.
 */
@FunctionalInterface
public interface SecretSettings {

    /**
     * El valor de una clave, como {@code nova.secrets.env.prefix}.
     *
     * @param key la clave, en la forma con puntos que usa la documentación
     * @return el valor, o vacío si no está configurado
     */
    Optional<String> get(String key);

    /**
     * El valor de una clave que lista varios elementos separados por coma.
     *
     * @param key la clave
     * @return los elementos, sin espacios alrededor ni elementos vacíos; una lista vacía si la clave
     *         no está configurada
     */
    default List<String> getList(String key) {
        return get(key).map(SecretSettings::splitList).orElse(List.of());
    }

    /**
     * El valor de una clave que es una duración, como el timeout de un almacén.
     *
     * @param key la clave
     * @return la duración, o vacío si la clave no está configurada o está en blanco
     * @throws SecretSourceException si el valor no es una duración; acepta {@code 500ms}, {@code 5s},
     *                               {@code 1m} o la forma ISO-8601, como {@code PT5S}
     */
    default Optional<Duration> getDuration(String key) {
        return get(key).map(String::trim).filter(value -> !value.isEmpty()).map(value -> parseDuration(key, value));
    }

    /**
     * Una configuración sin ninguna clave.
     *
     * @return la configuración vacía
     */
    static SecretSettings empty() {
        return key -> Optional.empty();
    }

    /**
     * Una configuración con valores fijos, útil en pruebas y en código que no tiene un framework.
     *
     * @param values las claves y sus valores
     * @return la configuración, que copia los valores y no se ve afectada si el mapa cambia después
     */
    static SecretSettings of(Map<String, String> values) {
        Map<String, String> copy = Map.copyOf(values);
        return key -> Optional.ofNullable(copy.get(key));
    }

    /**
     * Separa una lista escrita con comas.
     *
     * @param raw el texto, como {@code "SECRET_DB, SECRET_LEGACY"}
     * @return los elementos, sin espacios alrededor ni elementos vacíos
     */
    static List<String> splitList(String raw) {
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static Duration parseDuration(String key, String value) {
        Matcher simple = Pattern.compile("(\\d+)\\s*(ms|s|m)").matcher(value);
        if (simple.matches()) {
            long amount = Long.parseLong(simple.group(1));
            return switch (simple.group(2)) {
                case "ms" -> Duration.ofMillis(amount);
                case "s" -> Duration.ofSeconds(amount);
                default -> Duration.ofMinutes(amount);
            };
        }
        try {
            return Duration.parse(value);
        } catch (DateTimeParseException e) {
            throw new SecretSourceException("setting " + key, "is not a duration, such as 5s or PT5S");
        }
    }
}
