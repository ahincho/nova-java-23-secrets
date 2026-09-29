package pe.edu.nova.java.starters.secrets;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import pe.edu.nova.java.libs.secrets.Secret;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSourceException;
import pe.edu.nova.java.libs.secrets.SecretSourceProvider;

/** Un almacén en memoria, registrado solo para las pruebas en {@code META-INF/services}. */
public class FakeSecretSourceProvider implements SecretSourceProvider {

    static final AtomicInteger CREATED = new AtomicInteger();

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public SecretSource create(SecretSettings settings) {
        CREATED.incrementAndGet();
        String greeting = settings.get("fake.greeting").orElse("none");
        return reference -> switch (reference) {
            case "ms-course" ->
                Optional.of(Secret.of(reference, Map.of("DB_USERNAME", "course", "DB_PASSWORD", "s3cr3t")));
            case "legacy" -> Optional.of(Secret.of(reference, Map.of("LEGACY_API_KEY", "k")));
            case "settings" -> Optional.of(Secret.of(reference, Map.of("GREETING", greeting)));
            case "broken" -> throw new SecretSourceException(reference, "could not be parsed as JSON");
            default -> Optional.empty();
        };
    }
}
