package pe.edu.nova.java.starters.secrets;

import java.util.List;
import org.springframework.boot.bootstrap.BootstrapRegistry.InstanceSupplier;
import org.springframework.boot.bootstrap.ConfigurableBootstrapContext;
import org.springframework.boot.context.config.ConfigDataLocation;
import org.springframework.boot.context.config.ConfigDataLocationResolver;
import org.springframework.boot.context.config.ConfigDataLocationResolverContext;
import org.springframework.core.io.ResourceLoader;

/**
 * Entiende {@code spring.config.import=nova-secrets:<fuente>:<referencia>}.
 *
 * <p>La fuente es el nombre que publica un almacén, como {@code vault} o
 * {@code aws-secrets-manager}; la referencia, cómo ese almacén nombra el secreto. Todo lo que va
 * después de la fuente es la referencia, así que un ARN de AWS, que tiene dos puntos, se escribe
 * tal cual. Con {@code optional:} delante, un secreto que no existe no corta el arranque.
 */
public class NovaSecretsConfigDataLocationResolver implements ConfigDataLocationResolver<SecretConfigDataResource> {

    private final ClassLoader classLoader;

    /**
     * Crea el resolver; lo instancia Spring Boot.
     *
     * @param resourceLoader de donde sale el class loader en que se buscan las fuentes
     */
    public NovaSecretsConfigDataLocationResolver(ResourceLoader resourceLoader) {
        ClassLoader loader = resourceLoader.getClassLoader();
        this.classLoader = loader != null ? loader : NovaSecretsConfigDataLocationResolver.class.getClassLoader();
    }

    @Override
    public boolean isResolvable(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        return location.hasPrefix(SecretPropertySources.PREFIX);
    }

    @Override
    public List<SecretConfigDataResource> resolve(ConfigDataLocationResolverContext context, ConfigDataLocation location) {
        String value = location.getNonPrefixedValue(SecretPropertySources.PREFIX);
        int separator = value.indexOf(':');
        if (separator <= 0 || separator == value.length() - 1) {
            throw new IllegalArgumentException("Secret import '" + location + "' must be written as "
                    + "nova-secrets:<source>:<reference>, such as nova-secrets:vault:ms-course");
        }
        String source = value.substring(0, separator);
        String reference = value.substring(separator + 1);
        registry(context).requireProvider(source);
        return List.of(new SecretConfigDataResource(source, reference));
    }

    private SecretSourceRegistry registry(ConfigDataLocationResolverContext context) {
        ConfigurableBootstrapContext bootstrap = context.getBootstrapContext();
        bootstrap.registerIfAbsent(SecretSourceRegistry.class,
                InstanceSupplier.of(new SecretSourceRegistry(SpringSecretSettings.of(context.getBinder()), classLoader)));
        return bootstrap.get(SecretSourceRegistry.class);
    }
}
