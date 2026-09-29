package pe.edu.nova.java.starters.secrets;

import java.util.List;
import org.springframework.boot.context.config.ConfigData;
import org.springframework.boot.context.config.ConfigDataLoader;
import org.springframework.boot.context.config.ConfigDataLoaderContext;
import org.springframework.boot.context.config.ConfigDataResourceNotFoundException;
import pe.edu.nova.java.libs.secrets.Secret;

/**
 * Lee de su almacén el secreto que pidió {@code spring.config.import}.
 *
 * <p>Si el almacén no lo tiene, avisa a Spring que el recurso no existe, y Spring decide: corta el
 * arranque, salvo que la importación lleve {@code optional:}. Si el almacén contesta con un error,
 * el arranque se corta con ese error, que nombra el secreto y no su contenido.
 */
public class NovaSecretsConfigDataLoader implements ConfigDataLoader<SecretConfigDataResource> {

    /** Crea el loader; lo instancia Spring Boot. */
    public NovaSecretsConfigDataLoader() {
    }

    @Override
    public ConfigData load(ConfigDataLoaderContext context, SecretConfigDataResource resource) {
        SecretSourceRegistry registry = context.getBootstrapContext().get(SecretSourceRegistry.class);
        Secret secret = registry.source(resource.source())
                .find(resource.reference())
                .orElseThrow(() -> new ConfigDataResourceNotFoundException(resource));
        return new ConfigData(List.of(SecretPropertySources.of(resource.toString(), secret.entries())));
    }
}
