package pe.edu.nova.java.starters.secrets;

import java.util.Objects;
import org.springframework.boot.context.config.ConfigDataResource;

/**
 * Un secreto pedido con {@code spring.config.import=nova-secrets:<fuente>:<referencia>}.
 *
 * <p>Su {@link #toString()} es la misma importación que escribió el servicio: es lo que Spring
 * muestra cuando el secreto no existe, y no trae nada del contenido.
 */
public final class SecretConfigDataResource extends ConfigDataResource {

    private final String source;
    private final String reference;

    SecretConfigDataResource(String source, String reference) {
        this.source = source;
        this.reference = reference;
    }

    /**
     * La fuente que se pidió.
     *
     * @return el nombre de la fuente, como {@code vault}
     */
    public String source() {
        return source;
    }

    /**
     * Cómo nombra el secreto su almacén.
     *
     * @return la referencia, tal como la escribió el servicio
     */
    public String reference() {
        return reference;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof SecretConfigDataResource resource
                && source.equals(resource.source)
                && reference.equals(resource.reference);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, reference);
    }

    @Override
    public String toString() {
        return SecretPropertySources.PREFIX + source + ":" + reference;
    }
}
