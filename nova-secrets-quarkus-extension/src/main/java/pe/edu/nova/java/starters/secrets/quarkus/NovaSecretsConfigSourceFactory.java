package pe.edu.nova.java.starters.secrets.quarkus;

import io.smallrye.config.ConfigSourceContext;
import io.smallrye.config.ConfigSourceFactory;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.EnvConfigSource;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.Supplier;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.jboss.logging.Logger;
import pe.edu.nova.java.libs.secrets.Secret;
import pe.edu.nova.java.libs.secrets.SecretImport;
import pe.edu.nova.java.libs.secrets.SecretImports;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.env.EnvironmentSecrets;

/**
 * Convierte los secretos del servicio en fuentes de configuración de Quarkus, antes de que exista la
 * aplicación (ADR-042, regla 1).
 *
 * <p>Arma una fuente con lo que desdobla del entorno (el JSON que inyecta ECS) y una por cada pedido de
 * {@value SecretImports#IMPORT_SETTING}. Cada adaptador lee su dirección o su región de la misma
 * configuración, a través de {@link SecretSettings}, sin saber que lo llama Quarkus.
 *
 * <p>El ordinal decide qué gana (ADR-049):
 *
 * <ul>
 *   <li>con {@value #OVERRIDE_SETTING} en {@code true}, el valor por defecto, {@value #OVERRIDE_ORDINAL}:
 *       un secreto gana sobre una variable de entorno (300), como en NestJS y en Spring Boot;
 *   <li>con {@code false}, {@value #NO_OVERRIDE_ORDINAL}: la variable de entorno gana, y el secreto
 *       sigue ganando sobre {@code application.properties} (250).
 * </ul>
 *
 * <p>Los pedidos ganan sobre lo que se desdobla del entorno, y entre ellos gana el que se escribió
 * después, como en {@code spring.config.import}.
 */
public final class NovaSecretsConfigSourceFactory implements ConfigSourceFactory {

    /** Si un secreto pisa a una propiedad suelta con el mismo nombre. */
    public static final String OVERRIDE_SETTING = "nova.secrets.override";

    /** El ordinal de los secretos cuando pisan a las variables de entorno. */
    public static final int OVERRIDE_ORDINAL = 350;

    /** El ordinal de los secretos cuando una variable de entorno gana. */
    public static final int NO_OVERRIDE_ORDINAL = 275;

    /** El prefijo del nombre de cada fuente, para reconocerlas en un listado de la configuración. */
    public static final String SOURCE_NAME_PREFIX = "nova-secrets:";

    private static final Logger LOG = Logger.getLogger(NovaSecretsConfigSourceFactory.class);

    private final Supplier<Map<String, String>> environment;

    /** Crea la fábrica sobre el entorno del proceso. */
    public NovaSecretsConfigSourceFactory() {
        this(System::getenv);
    }

    NovaSecretsConfigSourceFactory(Supplier<Map<String, String>> environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    @Override
    public Iterable<ConfigSource> getConfigSources(ConfigSourceContext context) {
        SecretSettings settings = settings(context);
        int ordinal = settings.get(OVERRIDE_SETTING)
                        .map(String::trim)
                        .map(Boolean::parseBoolean)
                        .orElse(true)
                ? OVERRIDE_ORDINAL
                : NO_OVERRIDE_ORDINAL;

        List<ConfigSource> sources = new ArrayList<>();
        List<Secret> unfolded = EnvironmentSecrets.unfold(settings, environment.get());
        if (!unfolded.isEmpty()) {
            Map<String, String> entries = new LinkedHashMap<>();
            unfolded.forEach(secret -> entries.putAll(secret.entries()));
            sources.add(new SecretConfigSource(SOURCE_NAME_PREFIX + "env", entries, ordinal));
            LOG.infof("Unfolded %d keys from the secrets in %s", entries.size(), references(unfolded));
        }

        Map<SecretImport, Secret> imported = SecretImports.load(settings, classLoader());
        int position = 1;
        for (Map.Entry<SecretImport, Secret> entry : imported.entrySet()) {
            sources.add(new SecretConfigSource(
                    SOURCE_NAME_PREFIX + entry.getKey(), entry.getValue().entries(), ordinal + position++));
        }
        if (!imported.isEmpty()) {
            LOG.infof("Imported the secrets %s", imported.keySet());
        }
        return sources;
    }

    @Override
    public OptionalInt getPriority() {
        return OptionalInt.of(OVERRIDE_ORDINAL);
    }

    private static SecretSettings settings(ConfigSourceContext context) {
        return key -> Optional.ofNullable(context.getValue(key))
                .map(ConfigValue::getValue)
                .filter(value -> !value.isBlank());
    }

    private static List<String> references(List<Secret> secrets) {
        return secrets.stream().map(Secret::reference).toList();
    }

    private static ClassLoader classLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : NovaSecretsConfigSourceFactory.class.getClassLoader();
    }

    /**
     * Una fuente con las claves de un secreto. Hereda de {@link EnvConfigSource} para que una clave con
     * forma de variable, como {@code DB_PASSWORD}, también responda a {@code db.password}, igual que en
     * Spring Boot. Su {@code toString} nunca muestra valores.
     */
    static final class SecretConfigSource extends EnvConfigSource {

        private static final long serialVersionUID = 1L;

        private final String name;

        SecretConfigSource(String name, Map<String, String> entries, int ordinal) {
            super(Map.copyOf(entries), ordinal);
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String toString() {
            return "SecretConfigSource[name=" + name + ", ordinal=" + getOrdinal() + ", keys=" + getPropertyNames()
                    + "]";
        }
    }
}
