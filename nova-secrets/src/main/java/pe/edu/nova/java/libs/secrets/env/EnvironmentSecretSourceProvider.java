package pe.edu.nova.java.libs.secrets.env;

import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSourceProvider;

/**
 * Publica la fuente del entorno con el nombre {@value #NAME}.
 */
public final class EnvironmentSecretSourceProvider implements SecretSourceProvider {

    /** El nombre de la fuente del entorno. */
    public static final String NAME = "env";

    /** Crea el proveedor; lo instancia {@link java.util.ServiceLoader}. */
    public EnvironmentSecretSourceProvider() {
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SecretSource create(SecretSettings settings) {
        return EnvironmentSecretSource.systemEnvironment();
    }
}
