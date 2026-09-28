package pe.edu.nova.java.libs.secrets.env;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import pe.edu.nova.java.libs.secrets.Secret;
import pe.edu.nova.java.libs.secrets.SecretSource;

/**
 * Lee un secreto de una variable de entorno que trae un objeto JSON.
 *
 * <p>La referencia es el nombre de la variable. Una variable ausente o en blanco no es un error: es
 * lo que permite que una corrida local y los tests funcionen con sus propias variables. Una que no
 * trae un objeto JSON sí lo es, y corta el arranque.
 */
public final class EnvironmentSecretSource implements SecretSource {

    private final Function<String, String> variables;

    /**
     * Crea la fuente sobre un entorno cualquiera.
     *
     * @param variables cómo se lee una variable por su nombre; devuelve {@code null} si no existe
     */
    public EnvironmentSecretSource(Function<String, String> variables) {
        this.variables = Objects.requireNonNull(variables, "variables");
    }

    /**
     * La fuente sobre el entorno real del proceso.
     *
     * @return la fuente
     */
    public static EnvironmentSecretSource systemEnvironment() {
        return new EnvironmentSecretSource(System::getenv);
    }

    @Override
    public Optional<Secret> find(String variable) {
        String raw = variables.apply(Objects.requireNonNull(variable, "variable"));
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(Secret.fromJson(variable, raw));
    }
}
