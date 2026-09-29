package pe.edu.nova.java.starters.secrets;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSources;

/**
 * Las fuentes que ya se crearon durante el arranque, una por nombre.
 *
 * <p>Vive en el bootstrap context de Spring Boot, así que un servicio que importa tres secretos de
 * Vault inicia sesión en Vault una vez y no tres.
 */
final class SecretSourceRegistry {

    private final SecretSettings settings;
    private final ClassLoader classLoader;
    private final Map<String, SecretSource> sources = new ConcurrentHashMap<>();

    SecretSourceRegistry(SecretSettings settings, ClassLoader classLoader) {
        this.settings = settings;
        this.classLoader = classLoader;
    }

    /**
     * La fuente con ese nombre, creada la primera vez que se pide.
     *
     * @throws IllegalArgumentException si ninguna dependencia la publica
     */
    SecretSource source(String name) {
        return sources.computeIfAbsent(name, key -> SecretSources.provider(key, classLoader).create(settings));
    }

    /** Falla temprano si ninguna dependencia publica una fuente con ese nombre. */
    void requireProvider(String name) {
        SecretSources.provider(name, classLoader);
    }
}
