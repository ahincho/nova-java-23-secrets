package pe.edu.nova.java.libs.secrets;

/**
 * Crea una {@link SecretSource} a partir de su configuración.
 *
 * <p>Cada almacén publica el suyo en
 * {@code META-INF/services/pe.edu.nova.java.libs.secrets.SecretSourceProvider}, y
 * {@link SecretSources} lo encuentra con {@link java.util.ServiceLoader}. Así el conector de un
 * framework nunca nombra un almacén, y agregar uno es agregar una dependencia.
 */
public interface SecretSourceProvider {

    /**
     * El nombre con que un servicio elige esta fuente.
     *
     * @return un nombre corto y en minúsculas, como {@code env}, {@code vault} o
     *         {@code aws-secrets-manager}
     */
    String name();

    /**
     * Crea la fuente con su configuración.
     *
     * @param settings la configuración que ve el servicio, sin importar de qué framework viene
     * @return la fuente lista para buscar secretos
     * @throws SecretSourceException si falta algo sin lo cual la fuente no puede funcionar, como la
     *                               dirección de un almacén
     */
    SecretSource create(SecretSettings settings);
}
