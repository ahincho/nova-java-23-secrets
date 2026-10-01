package pe.edu.nova.java.starters.secrets.quarkus;

import io.quarkus.runtime.configuration.ConfigBuilder;
import io.smallrye.config.SmallRyeConfigBuilder;

/**
 * Suma {@link NovaSecretsConfigSourceFactory} a la configuración de tiempo de ejecución.
 *
 * <p>La registra el paso de build de la extensión, y solo para la configuración de ejecución: así un
 * almacén nunca se consulta al construir la aplicación, y en una imagen nativa los secretos se leen al
 * arrancar, no quedan grabados en el binario.
 */
public final class NovaSecretsConfigBuilder implements ConfigBuilder {

    /** Crea el builder; lo instancia Quarkus. */
    public NovaSecretsConfigBuilder() {}

    @Override
    public SmallRyeConfigBuilder configBuilder(SmallRyeConfigBuilder builder) {
        return builder.withSources(new NovaSecretsConfigSourceFactory());
    }
}
