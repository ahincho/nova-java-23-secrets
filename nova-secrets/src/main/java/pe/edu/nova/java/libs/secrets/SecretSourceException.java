package pe.edu.nova.java.libs.secrets;

import java.util.Objects;

/**
 * Un secreto que existe pero no se pudo leer, o una fuente que no se pudo crear.
 *
 * <p>El mensaje nombra la referencia y la razón, y <strong>nunca el contenido del secreto</strong>.
 * Por eso quien la lanza no encadena una excepción cuyo mensaje cite el texto que no pudo leer: el
 * parser de JSON lo hace, y ese texto es el secreto.
 */
public class SecretSourceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** El secreto que no se pudo leer, tal como lo nombró el servicio. */
    private final String reference;

    /**
     * Crea la excepción sin causa.
     *
     * @param reference el secreto, tal como lo nombró el servicio
     * @param reason    por qué no se pudo leer, sin citar su contenido
     */
    public SecretSourceException(String reference, String reason) {
        super(message(reference, reason));
        this.reference = reference;
    }

    /**
     * Crea la excepción con una causa que no cita el contenido, como un timeout o un error de red.
     *
     * @param reference el secreto, tal como lo nombró el servicio
     * @param reason    por qué no se pudo leer, sin citar su contenido
     * @param cause     la causa; nunca una excepción que cite el texto del secreto
     */
    public SecretSourceException(String reference, String reason, Throwable cause) {
        super(message(reference, reason), cause);
        this.reference = reference;
    }

    /**
     * El secreto que no se pudo leer.
     *
     * @return la referencia, tal como la escribió el servicio
     */
    public String reference() {
        return reference;
    }

    private static String message(String reference, String reason) {
        return "Secret " + Objects.requireNonNull(reference, "reference") + " "
                + Objects.requireNonNull(reason, "reason");
    }
}
