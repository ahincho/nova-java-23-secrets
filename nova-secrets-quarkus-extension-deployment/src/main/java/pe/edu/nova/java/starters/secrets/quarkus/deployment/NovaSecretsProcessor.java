package pe.edu.nova.java.starters.secrets.quarkus.deployment;

import io.quarkus.deployment.annotations.BuildStep;
import io.quarkus.deployment.builditem.FeatureBuildItem;
import io.quarkus.deployment.builditem.RunTimeConfigBuilderBuildItem;
import io.quarkus.deployment.builditem.nativeimage.ServiceProviderBuildItem;
import pe.edu.nova.java.libs.secrets.SecretSourceProvider;
import pe.edu.nova.java.starters.secrets.quarkus.NovaSecretsConfigBuilder;

/**
 * Los pasos de build de la extensión de secretos (ADR-049).
 *
 * <p>Corren al construir la aplicación, nunca al arrancarla: registran la carga de los secretos para la
 * configuración de ejecución y las fuentes para la imagen nativa.
 */
public class NovaSecretsProcessor {

    /** El nombre con que Quarkus lista la extensión al arrancar. */
    static final String FEATURE = "nova-secrets";

    /** Crea el procesador; lo instancia Quarkus. */
    public NovaSecretsProcessor() {}

    @BuildStep
    FeatureBuildItem feature() {
        return new FeatureBuildItem(FEATURE);
    }

    /**
     * Solo para la configuración de ejecución: un almacén nunca se consulta al construir, y en una imagen
     * nativa el secreto se lee al arrancar en lugar de quedar grabado en el binario.
     */
    @BuildStep
    RunTimeConfigBuilderBuildItem secretsConfigBuilder() {
        return new RunTimeConfigBuilderBuildItem(NovaSecretsConfigBuilder.class);
    }

    /**
     * Cada {@link SecretSourceProvider} del classpath. En una imagen nativa {@code ServiceLoader} solo
     * encuentra lo que se registró al construirla; así agregar un almacén sigue siendo agregar una
     * dependencia, también en nativo.
     */
    @BuildStep
    ServiceProviderBuildItem secretSourceProviders() {
        return ServiceProviderBuildItem.allProvidersFromClassPath(SecretSourceProvider.class.getName());
    }
}
