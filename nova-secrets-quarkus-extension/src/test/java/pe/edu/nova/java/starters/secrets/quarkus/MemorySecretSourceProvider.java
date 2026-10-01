package pe.edu.nova.java.starters.secrets.quarkus;

import java.util.Map;
import java.util.Optional;
import pe.edu.nova.java.libs.secrets.Secret;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSourceProvider;

/** Un almacén en memoria para las pruebas: tiene {@code ms-course} y {@code ms-course-override}. */
public final class MemorySecretSourceProvider implements SecretSourceProvider {

    /** Lo instancia {@code ServiceLoader}. */
    public MemorySecretSourceProvider() {}

    @Override
    public String name() {
        return "memory";
    }

    @Override
    public SecretSource create(SecretSettings settings) {
        String password = settings.get("nova.secrets.memory.password").orElse("from-memory");
        return reference -> switch (reference) {
            case "ms-course" ->
                Optional.of(Secret.of(reference, Map.of("DB_USERNAME", "course", "DB_PASSWORD", password)));
            case "ms-course-override" -> Optional.of(Secret.of(reference, Map.of("DB_PASSWORD", "overridden")));
            default -> Optional.empty();
        };
    }
}
