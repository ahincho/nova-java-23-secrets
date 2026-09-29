package pe.edu.nova.java.libs.secrets.vault;

import java.net.http.HttpClient;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSource;
import pe.edu.nova.java.libs.secrets.SecretSourceProvider;

/**
 * Publica la fuente de Vault con el nombre {@value #NAME}.
 *
 * <p>Su configuración va bajo {@code nova.secrets.vault.*}:
 *
 * <ul>
 *   <li>{@code address}, que por defecto toma {@code VAULT_ADDR};</li>
 *   <li>{@code token}, que por defecto toma {@code VAULT_TOKEN}, o {@code app-role.role-id} y
 *       {@code app-role.secret-id}, con {@code app-role.mount} por defecto {@code approle};</li>
 *   <li>{@code mount}, el motor KV versión 2, por defecto {@code secret};</li>
 *   <li>{@code timeout}, por defecto {@code 5s}.</li>
 * </ul>
 */
public final class VaultSecretSourceProvider implements SecretSourceProvider {

    /** El nombre de la fuente de Vault. */
    public static final String NAME = "vault";

    /** Crea el proveedor; lo instancia {@link java.util.ServiceLoader}. */
    public VaultSecretSourceProvider() {}

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SecretSource create(SecretSettings settings) {
        VaultSettings vault = VaultSettings.from(settings);
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(vault.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        return new VaultSecretSource(vault, http);
    }
}
