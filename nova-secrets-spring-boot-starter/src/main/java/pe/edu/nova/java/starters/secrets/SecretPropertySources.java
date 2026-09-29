package pe.edu.nova.java.starters.secrets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * Cómo se nombran y dónde se ubican las fuentes de propiedades que traen un secreto.
 */
final class SecretPropertySources {

    /** El prefijo de toda fuente de propiedades que trae un secreto; también el de las importaciones. */
    static final String PREFIX = "nova-secrets:";

    /** La propiedad que decide si un secreto pisa a una variable suelta con el mismo nombre. */
    static final String OVERRIDE_PROPERTY = "nova.secrets.override";

    private SecretPropertySources() {
    }

    /**
     * Una fuente de propiedades con las entradas de un secreto.
     *
     * <p>Es una {@link SystemEnvironmentPropertySource} a propósito: un secreto se comporta como una
     * variable de entorno, así que {@code DB_PASSWORD} se encuentra también como {@code db.password}.
     * Es lo que ve un servicio NestJS, donde el secreto se desdobla en {@code process.env}.
     */
    static PropertySource<?> of(String name, Map<String, String> entries) {
        return new SystemEnvironmentPropertySource(name, new LinkedHashMap<>(entries));
    }

    static boolean isSecret(PropertySource<?> propertySource) {
        return propertySource != null && propertySource.getName().startsWith(PREFIX);
    }

    /**
     * Ubica las fuentes de secretos respecto de las variables de entorno.
     *
     * <p>Con {@code override} quedan justo antes de ellas, así que un secreto pisa a una variable
     * suelta con el mismo nombre; sin él, justo después. Entre sí conservan su orden.
     */
    static void position(MutablePropertySources sources, boolean override) {
        String anchor = StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME;
        if (!sources.contains(anchor)) {
            return;
        }
        List<PropertySource<?>> secrets = new ArrayList<>();
        for (PropertySource<?> source : sources) {
            if (isSecret(source)) {
                secrets.add(source);
            }
        }
        secrets.forEach(source -> sources.remove(source.getName()));
        if (override) {
            secrets.forEach(source -> sources.addBefore(anchor, source));
        } else {
            for (int i = secrets.size() - 1; i >= 0; i--) {
                sources.addAfter(anchor, secrets.get(i));
            }
        }
    }
}
