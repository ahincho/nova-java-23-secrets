package pe.edu.nova.java.starters.secrets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.logging.Log;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import pe.edu.nova.java.libs.secrets.Secret;
import pe.edu.nova.java.libs.secrets.SecretImport;
import pe.edu.nova.java.libs.secrets.SecretImports;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.env.EnvironmentSecrets;

/**
 * Desdobla los secretos que el orquestador pone en el entorno y ubica todas las fuentes de secretos.
 *
 * <p>Corre justo después de que Spring lee los archivos de configuración, así que ve el prefijo y
 * la lista de variables aunque vengan del {@code application.yml}, y ya encuentra cargados los
 * secretos que se pidieron con {@code spring.config.import}. Hace tres cosas:
 *
 * <ol>
 *   <li>Carga los secretos que se piden con {@code nova.secrets.import}, la misma forma que en Quarkus
 *       y en NestJS, para que operaciones los pida con la variable {@code NOVA_SECRETS_IMPORT} sin saber
 *       en qué framework está el servicio.</li>
 *   <li>Desdobla las variables de entorno que traen un secreto (ver {@link EnvironmentSecrets}) en
 *       una fuente de propiedades. Si dos secretos traen la misma clave, gana el último, igual que
 *       en NestJS. Si no hay nada que desdoblar, no agrega nada.</li>
 *   <li>Ubica las fuentes de secretos respecto de las variables de entorno, según
 *       {@code nova.secrets.override}.</li>
 * </ol>
 *
 * <p>Un secreto que no trae un objeto JSON corta el arranque con un error que nombra la variable y
 * no cita su contenido.
 */
public class NovaSecretsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    /** Justo después de la carga de los archivos de configuración. */
    public static final int ORDER = ConfigDataEnvironmentPostProcessor.ORDER + 1;

    /** El nombre de la fuente de propiedades con los secretos del entorno. */
    static final String ENVIRONMENT_SOURCE_NAME = SecretPropertySources.PREFIX + "env";

    private final Log log;

    /**
     * Crea el procesador; lo instancia Spring Boot.
     *
     * @param logFactory el log diferido, porque el sistema de logging todavía no está listo
     */
    public NovaSecretsEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(NovaSecretsEnvironmentPostProcessor.class);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        SecretSettings settings = SpringSecretSettings.of(environment);

        // Los pedidos de nova.secrets.import, que en los tres stacks se piden igual (ADR-049). Se agregan
        // del último al primero y antes que los del entorno, para que el último pedido gane y todo pedido
        // gane sobre lo que se desdobla del entorno, como en Quarkus.
        Map<SecretImport, Secret> imported = SecretImports.load(settings, application.getClassLoader());
        List<Map.Entry<SecretImport, Secret>> newestFirst = new ArrayList<>(imported.entrySet());
        Collections.reverse(newestFirst);
        newestFirst.forEach(entry -> environment
                .getPropertySources()
                .addLast(SecretPropertySources.of(
                        SecretPropertySources.PREFIX + entry.getKey(),
                        entry.getValue().entries())));
        if (!imported.isEmpty()) {
            log.info("Imported the secrets " + imported.keySet());
        }

        List<Secret> secrets = EnvironmentSecrets.unfold(settings, systemEnvironment(environment));
        if (!secrets.isEmpty()) {
            Map<String, String> entries = new LinkedHashMap<>();
            secrets.forEach(secret -> entries.putAll(secret.entries()));
            environment.getPropertySources().addLast(SecretPropertySources.of(ENVIRONMENT_SOURCE_NAME, entries));
            log.info("Unfolded " + entries.size() + " keys from the secrets in "
                    + secrets.stream().map(Secret::reference).toList());
        }
        boolean override = environment.getProperty(SecretPropertySources.OVERRIDE_PROPERTY, Boolean.class, true);
        SecretPropertySources.position(environment.getPropertySources(), override);
    }

    /**
     * Las variables de entorno tal como las ve Spring: se leen de su fuente de propiedades y no de
     * {@link System#getenv()}, para que una prueba pueda reemplazarlas.
     */
    private static Map<String, String> systemEnvironment(ConfigurableEnvironment environment) {
        PropertySource<?> source =
                environment.getPropertySources().get(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        Map<String, Object> raw =
                source instanceof MapPropertySource map ? map.getSource() : environment.getSystemEnvironment();
        Map<String, String> variables = new HashMap<>();
        raw.forEach((name, value) -> {
            if (value != null) {
                variables.put(name, value.toString());
            }
        });
        return variables;
    }
}
