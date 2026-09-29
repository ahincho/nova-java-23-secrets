package pe.edu.nova.java.libs.secrets.vault;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import pe.edu.nova.java.libs.secrets.Secret;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSourceException;
import pe.edu.nova.java.libs.secrets.SecretSources;

/** Pruebas contra un servidor HTTP que imita la API de Vault, para cubrir los errores sin Docker. */
class VaultSecretSourceTest {

    private static final String MS_COURSE = """
            {"request_id": "1", "data": {"data": {"DB_USERNAME": "course", "DB_PASSWORD": "s3cr3t",
             "nested": {"ignored": true}}, "metadata": {"version": 3}}}""";

    private HttpServer server;
    private final List<String> tokens = new CopyOnWriteArrayList<>();
    private final AtomicInteger logins = new AtomicInteger();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/secret/data/", exchange -> {
            tokens.add(String.valueOf(exchange.getRequestHeaders().getFirst("X-Vault-Token")));
            String path = exchange.getRequestURI().getPath().substring("/v1/secret/data/".length());
            switch (path) {
                case "ms-course" -> respond(exchange, 200, MS_COURSE);
                case "forbidden" -> respond(exchange, 403, "{\"errors\": [\"permission denied\"]}");
                case "broken" -> respond(exchange, 500, "{\"errors\": [\"internal\"]}");
                case "not-json" -> respond(exchange, 200, "{\"data\": {\"data\": {\"DB_PASSWORD\": \"s3cr3t\"");
                case "deleted" -> respond(exchange, 200, "{\"data\": {\"data\": null, \"metadata\": {}}}");
                case "slow" -> {
                    sleep(1500);
                    respond(exchange, 200, MS_COURSE);
                }
                default -> respond(exchange, 404, "{\"errors\": []}");
            }
        });
        server.createContext("/v1/auth/approle/login", exchange -> {
            logins.incrementAndGet();
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (body.contains("\"role_id\":\"role\"") && body.contains("\"secret_id\":\"id\"")) {
                respond(exchange, 200, "{\"auth\": {\"client_token\": \"t-from-approle\", \"lease_duration\": 60}}");
            } else {
                respond(exchange, 400, "{\"errors\": [\"invalid role or secret ID\"]}");
            }
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    @Test
    void theProviderIsFoundByName() {
        assertEquals("vault", SecretSources.provider("vault").name());
    }

    @Test
    void aSecretIsReadWithTheToken() {
        Optional<Secret> secret = source(Map.of("vault.token", "root")).find("ms-course");

        assertEquals(Map.of("DB_USERNAME", "course", "DB_PASSWORD", "s3cr3t"), secret.orElseThrow().entries());
        assertEquals(List.of("root"), tokens);
    }

    @Test
    void aSecretThatDoesNotExistIsEmpty() {
        assertTrue(source(Map.of("vault.token", "root")).find("missing").isEmpty());
        assertTrue(source(Map.of("vault.token", "root")).find("deleted").isEmpty());
    }

    @Test
    void aRefusalNamesThePathAndNothingElse() {
        SecretSourceException error = assertThrows(SecretSourceException.class,
                () -> source(Map.of("vault.token", "root")).find("forbidden"));

        assertEquals("Secret forbidden was refused by Vault (403): the token or the AppRole cannot read it", error.getMessage());
    }

    @Test
    void aServerErrorStopsTheRead() {
        SecretSourceException error = assertThrows(SecretSourceException.class,
                () -> source(Map.of("vault.token", "root")).find("broken"));

        assertEquals("Secret broken could not be read from Vault (status 500)", error.getMessage());
    }

    @Test
    void aResponseThatCannotBeParsedNeverQuotesIt() {
        SecretSourceException error = assertThrows(SecretSourceException.class,
                () -> source(Map.of("vault.token", "root")).find("not-json"));

        assertEquals("Secret not-json could not be parsed from the Vault response", error.getMessage());
        assertFalse(String.valueOf(error.getCause()).contains("s3cr3t"));
    }

    @Test
    void aSlowVaultTimesOut() {
        SecretSource source = source(Map.of("vault.token", "root", "nova.secrets.vault.timeout", "200ms"));

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> source.find("slow"));

        assertEquals("Secret slow could not be read: Vault did not answer within PT0.2S", error.getMessage());
    }

    @Test
    void anUnreachableVaultNamesTheAddress() throws IOException {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }
        SecretSource source = SecretSources.provider("vault").create(SecretSettings.of(Map.of(
                "vault.addr", "http://127.0.0.1:" + closedPort, "vault.token", "root")));

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> source.find("ms-course"));

        assertEquals("Secret ms-course could not be read: Vault is not reachable at http://127.0.0.1:" + closedPort,
                error.getMessage());
    }

    @Test
    void anAppRoleLogsInOnceAndUsesItsToken() {
        SecretSource source = source(Map.of(
                "nova.secrets.vault.app-role.role-id", "role", "nova.secrets.vault.app-role.secret-id", "id"));

        source.find("ms-course");
        source.find("missing");

        assertEquals(1, logins.get());
        assertEquals(List.of("t-from-approle", "t-from-approle"), tokens);
    }

    @Test
    void aRefusedAppRoleLoginStopsTheRead() {
        SecretSource source = source(Map.of(
                "nova.secrets.vault.app-role.role-id", "role", "nova.secrets.vault.app-role.secret-id", "wrong"));

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> source.find("ms-course"));

        assertEquals("Secret ms-course could not be read: the AppRole login was refused by Vault (status 400)",
                error.getMessage());
    }

    @Test
    void aReferenceMustBeAVaultPath() {
        SecretSource source = source(Map.of("vault.token", "root"));

        for (String reference : List.of("../sys/raw", "/ms-course", "ms course", "ms-course/", "")) {
            assertThrows(SecretSourceException.class, () -> source.find(reference), reference);
        }
        assertTrue(tokens.isEmpty());
    }

    private SecretSource source(Map<String, String> values) {
        Map<String, String> settings = new HashMap<>(values);
        settings.put("nova.secrets.vault.address", "http://127.0.0.1:" + server.getAddress().getPort());
        return SecretSources.provider("vault").create(SecretSettings.of(settings));
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
