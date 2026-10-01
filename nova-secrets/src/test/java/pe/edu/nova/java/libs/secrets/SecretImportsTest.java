package pe.edu.nova.java.libs.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecretImportsTest {

    private static final AtomicInteger CREATED = new AtomicInteger();

    @TempDir
    private Path services;

    private URLClassLoader classLoader;

    /** Un almacén en memoria, que solo ve este test: se registra en un class loader propio. */
    public static final class MemoryProvider implements SecretSourceProvider {

        public MemoryProvider() {}

        @Override
        public String name() {
            return "memory";
        }

        @Override
        public SecretSource create(SecretSettings settings) {
            CREATED.incrementAndGet();
            String password = settings.get("nova.secrets.memory.password").orElse("default");
            return reference -> reference.equals("ms-course")
                    ? Optional.of(Secret.of(reference, Map.of("DB_PASSWORD", password)))
                    : Optional.empty();
        }
    }

    @BeforeEach
    void registerTheMemoryStore() throws IOException {
        Path file = services.resolve("META-INF/services/" + SecretSourceProvider.class.getName());
        Files.createDirectories(file.getParent());
        Files.writeString(file, MemoryProvider.class.getName());
        classLoader = new URLClassLoader(
                new URL[] {services.toUri().toURL()}, getClass().getClassLoader());
        CREATED.set(0);
    }

    @AfterEach
    void close() throws IOException {
        classLoader.close();
    }

    @Test
    void eachImportResolvesWithTheSettingsOfTheService() {
        SecretSettings settings = SecretSettings.of(
                Map.of(SecretImports.IMPORT_SETTING, "memory:ms-course", "nova.secrets.memory.password", "s3cr3t"));

        Map<SecretImport, Secret> secrets = SecretImports.load(settings, classLoader);

        assertEquals(List.of(SecretImport.parse("memory:ms-course")), List.copyOf(secrets.keySet()));
        assertEquals(Optional.of("s3cr3t"), secrets.values().iterator().next().get("DB_PASSWORD"));
    }

    @Test
    void aMissingSecretStopsTheStartupUnlessItIsOptional() {
        SecretSourceException error = assertThrows(
                SecretSourceException.class,
                () -> SecretImports.load(
                        List.of(SecretImport.parse("memory:missing")), SecretSettings.empty(), classLoader));

        assertEquals(
                "Secret memory:missing does not exist; write optional:memory:missing if it may", error.getMessage());
        assertTrue(SecretImports.load(
                        List.of(SecretImport.parse("optional:memory:missing")), SecretSettings.empty(), classLoader)
                .isEmpty());
    }

    @Test
    void anUnknownSourceNamesTheDependencyThatIsMissing() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SecretImports.load(
                        List.of(SecretImport.parse("vault:ms-course")), SecretSettings.empty(), classLoader));

        assertTrue(error.getMessage().contains("such as nova-secrets-vault"), error.getMessage());
    }

    @Test
    void aSourceIsCreatedOnceForAllItsImports() {
        SecretImports.load(
                SecretImport.parseAll("memory:ms-course, optional:memory:other"), SecretSettings.empty(), classLoader);

        assertEquals(1, CREATED.get());
    }

    @Test
    void noImportIsNoSecret() {
        assertTrue(SecretImports.load(SecretSettings.empty(), classLoader).isEmpty());
    }
}
