package pe.edu.nova.java.libs.secrets;

import java.util.List;
import java.util.Objects;

/**
 * Un secreto que el servicio pide a un almacén: {@code <fuente>:<referencia>}, como {@code vault:ms-course}.
 *
 * <p>Es la misma forma en los tres stacks (ADR-049), sea que llegue por {@code nova.secrets.import}, por
 * la variable {@code NOVA_SECRETS_IMPORT} o, en Spring Boot, detrás de {@code nova-secrets:} en
 * {@code spring.config.import}. Con {@code optional:} delante, un secreto que no existe no corta el
 * arranque; sin él, sí, porque quien escribió la referencia prometió que existe.
 *
 * @param source el nombre de la fuente, como {@code vault} o {@code aws-secrets-manager}
 * @param reference la referencia dentro de esa fuente, como {@code ms-course} o {@code prod/ms-course/db}
 * @param optional si el secreto puede faltar
 */
public record SecretImport(String source, String reference, boolean optional) {

    /** Lo que marca un secreto que puede faltar, la misma convención de {@code spring.config.import}. */
    public static final String OPTIONAL_PREFIX = "optional:";

    /**
     * Valida los componentes.
     *
     * @param source el nombre de la fuente
     * @param reference la referencia
     * @param optional si el secreto puede faltar
     */
    public SecretImport {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(reference, "reference");
        if (source.isBlank() || reference.isBlank()) {
            throw new IllegalArgumentException("A secret import needs a source and a reference");
        }
    }

    /**
     * Lee un pedido escrito como {@code [optional:]<fuente>:<referencia>}. La referencia puede tener
     * {@code :} adentro: solo el primero separa la fuente.
     *
     * @param raw el texto
     * @return el pedido
     * @throws IllegalArgumentException si no tiene la forma esperada
     */
    public static SecretImport parse(String raw) {
        Objects.requireNonNull(raw, "raw");
        String value = raw.trim();
        boolean optional = value.startsWith(OPTIONAL_PREFIX);
        if (optional) {
            value = value.substring(OPTIONAL_PREFIX.length());
        }
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Secret import '" + raw.trim() + "' must be written as "
                    + "<source>:<reference>, such as vault:ms-course");
        }
        return new SecretImport(value.substring(0, separator), value.substring(separator + 1), optional);
    }

    /**
     * Lee una lista separada por comas, como la de {@code NOVA_SECRETS_IMPORT}.
     *
     * @param raw el texto; vacío o en blanco es una lista vacía
     * @return los pedidos, en el orden en que se escribieron
     */
    public static List<SecretImport> parseAll(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return SecretSettings.splitList(raw).stream().map(SecretImport::parse).toList();
    }

    /**
     * El pedido como se escribe, con {@code optional:} si corresponde.
     *
     * @return el texto
     */
    @Override
    public String toString() {
        return (optional ? OPTIONAL_PREFIX : "") + source + ":" + reference;
    }
}
