package pe.edu.nova.java.libs.secrets;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resuelve los secretos que un servicio pide a sus almacenes, con las reglas de ADR-042.
 *
 * <p>Lo usa el conector de cada framework, para que todos digan lo mismo ante el mismo caso:
 *
 * <ul>
 *   <li>una fuente que no está en el classpath corta el arranque y dice qué dependencia falta;
 *   <li>un secreto que no existe corta el arranque, salvo que el pedido sea {@code optional:};
 *   <li>cada fuente se crea una sola vez, aunque se le pidan varios secretos.
 * </ul>
 */
public final class SecretImports {

    /** La propiedad con la lista de pedidos, en Quarkus y en Spring Boot. */
    public static final String IMPORT_SETTING = "nova.secrets.import";

    private SecretImports() {}

    /**
     * Resuelve los pedidos que nombra {@value #IMPORT_SETTING}.
     *
     * @param settings la configuración del servicio, de donde también lee cada adaptador la suya
     * @param classLoader el class loader donde buscar las fuentes
     * @return cada pedido con su secreto, en el orden en que se escribieron; un opcional que no existe no aparece
     */
    public static Map<SecretImport, Secret> load(SecretSettings settings, ClassLoader classLoader) {
        return load(SecretImport.parseAll(settings.get(IMPORT_SETTING).orElse(null)), settings, classLoader);
    }

    /**
     * Resuelve una lista de pedidos.
     *
     * @param imports los pedidos
     * @param settings la configuración del servicio
     * @param classLoader el class loader donde buscar las fuentes
     * @return cada pedido con su secreto, en orden; un opcional que no existe no aparece
     * @throws IllegalArgumentException si una fuente no está en el classpath
     * @throws SecretSourceException si un secreto obligatorio no existe o un almacén falla
     */
    public static Map<SecretImport, Secret> load(
            List<SecretImport> imports, SecretSettings settings, ClassLoader classLoader) {
        Objects.requireNonNull(imports, "imports");
        Objects.requireNonNull(settings, "settings");
        Map<String, SecretSource> sources = new HashMap<>();
        Map<SecretImport, Secret> secrets = new LinkedHashMap<>();
        for (SecretImport secretImport : imports) {
            SecretSource source = sources.computeIfAbsent(
                    secretImport.source(),
                    name -> SecretSources.provider(name, classLoader).create(settings));
            Optional<Secret> secret = source.find(secretImport.reference());
            if (secret.isPresent()) {
                secrets.put(secretImport, secret.get());
            } else if (!secretImport.optional()) {
                throw new SecretSourceException(
                        secretImport.toString(), "does not exist; write optional:" + secretImport + " if it may");
            }
        }
        return secrets;
    }
}
