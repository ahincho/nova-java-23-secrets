package pe.edu.nova.java.libs.secrets;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Descubre las fuentes de secretos que el servicio tiene en su classpath.
 *
 * <p>Cada almacén es una dependencia que publica su {@link SecretSourceProvider}; la fuente del
 * entorno viene siempre, porque vive en este mismo módulo.
 */
public final class SecretSources {

    private SecretSources() {}

    /**
     * Las fuentes disponibles, ordenadas por nombre.
     *
     * @param classLoader el class loader donde buscar; el conector de cada framework pasa el suyo
     * @return las fuentes encontradas
     * @throws IllegalStateException si dos fuentes declaran el mismo nombre
     */
    public static List<SecretSourceProvider> providers(ClassLoader classLoader) {
        List<SecretSourceProvider> providers = ServiceLoader.load(SecretSourceProvider.class, classLoader).stream()
                .map(ServiceLoader.Provider::get)
                .sorted(Comparator.comparing(SecretSourceProvider::name))
                .toList();
        Map<String, Long> counts =
                providers.stream().collect(Collectors.groupingBy(SecretSourceProvider::name, Collectors.counting()));
        List<String> repeated = counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        if (!repeated.isEmpty()) {
            throw new IllegalStateException("More than one secret source declares the name " + repeated);
        }
        return providers;
    }

    /**
     * Las fuentes disponibles en el class loader de este módulo.
     *
     * @return las fuentes encontradas, ordenadas por nombre
     */
    public static List<SecretSourceProvider> providers() {
        return providers(defaultClassLoader());
    }

    /**
     * La fuente con ese nombre.
     *
     * @param name        el nombre, como {@code env} o {@code vault}
     * @param classLoader el class loader donde buscar
     * @return la fuente
     * @throws IllegalArgumentException si no hay ninguna con ese nombre; el mensaje lista las que
     *                                  sí hay, que es lo primero que se pregunta quien la pidió
     */
    public static SecretSourceProvider provider(String name, ClassLoader classLoader) {
        Map<String, SecretSourceProvider> byName = providers(classLoader).stream()
                .collect(Collectors.toMap(SecretSourceProvider::name, Function.identity()));
        SecretSourceProvider provider = byName.get(name);
        if (provider == null) {
            throw new IllegalArgumentException("No secret source named '" + name + "'. Available: "
                    + byName.keySet().stream().sorted().toList()
                    + ". Each store is a dependency, such as nova-secrets-vault.");
        }
        return provider;
    }

    /**
     * La fuente con ese nombre, en el class loader de este módulo.
     *
     * @param name el nombre
     * @return la fuente
     * @throws IllegalArgumentException si no hay ninguna con ese nombre
     */
    public static SecretSourceProvider provider(String name) {
        return provider(name, defaultClassLoader());
    }

    private static ClassLoader defaultClassLoader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : SecretSources.class.getClassLoader();
    }
}
