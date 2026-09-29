package pe.edu.nova.java.libs.secrets.vault;

import java.net.URI;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;
import pe.edu.nova.java.libs.secrets.SecretSettings;
import pe.edu.nova.java.libs.secrets.SecretSourceException;

/**
 * La configuración del adaptador, leída de {@link SecretSettings}.
 *
 * <p>Donde Vault tiene su propia convención, se respeta como valor por defecto: la dirección sale de
 * {@code vault.addr} y el token de {@code vault.token}, que un framework encuentra como las variables
 * {@code VAULT_ADDR} y {@code VAULT_TOKEN} que usa la CLI de Vault.
 */
record VaultSettings(URI address, String mount, Duration timeout, VaultAuthentication authentication) {

    static final String PREFIX = "nova.secrets.vault.";
    static final String DEFAULT_MOUNT = "secret";
    static final String DEFAULT_APP_ROLE_MOUNT = "approle";
    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

    /** Una ruta de Vault: segmentos de letras, dígitos, punto, guion y guion bajo, separados por barras. */
    static final Pattern PATH = Pattern.compile("[A-Za-z0-9_-][A-Za-z0-9._-]*(/[A-Za-z0-9_-][A-Za-z0-9._-]*)*");

    static VaultSettings from(SecretSettings settings) {
        String address = first(settings, PREFIX + "address", "vault.addr")
                .orElseThrow(() -> invalid("needs an address: set nova.secrets.vault.address or VAULT_ADDR"));
        URI uri;
        try {
            uri = URI.create(address.endsWith("/") ? address.substring(0, address.length() - 1) : address);
        } catch (IllegalArgumentException e) {
            throw invalid("has an address that is not a URI");
        }
        if (uri.getScheme() == null || !(uri.getScheme().equals("http") || uri.getScheme().equals("https"))) {
            throw invalid("needs an http or https address");
        }
        String mount = settings.get(PREFIX + "mount").filter(value -> !value.isBlank()).orElse(DEFAULT_MOUNT);
        if (!PATH.matcher(mount).matches()) {
            throw invalid("has a mount that is not a Vault path");
        }
        Duration timeout = settings.getDuration(PREFIX + "timeout").orElse(DEFAULT_TIMEOUT);
        return new VaultSettings(uri, mount, timeout, authentication(settings));
    }

    private static VaultAuthentication authentication(SecretSettings settings) {
        Optional<String> roleId = settings.get(PREFIX + "app-role.role-id").filter(value -> !value.isBlank());
        if (roleId.isPresent()) {
            String secretId = settings.get(PREFIX + "app-role.secret-id").filter(value -> !value.isBlank())
                    .orElseThrow(() -> invalid("has an AppRole role-id without its secret-id"));
            String mount = settings.get(PREFIX + "app-role.mount").filter(value -> !value.isBlank())
                    .orElse(DEFAULT_APP_ROLE_MOUNT);
            if (!PATH.matcher(mount).matches()) {
                throw invalid("has an AppRole mount that is not a Vault path");
            }
            return new VaultAuthentication.AppRole(roleId.get(), secretId, mount);
        }
        return first(settings, PREFIX + "token", "vault.token")
                .<VaultAuthentication>map(VaultAuthentication.Token::new)
                .orElseThrow(() -> invalid("needs a token or an AppRole: set nova.secrets.vault.token, VAULT_TOKEN "
                        + "or nova.secrets.vault.app-role.role-id and secret-id"));
    }

    private static Optional<String> first(SecretSettings settings, String key, String fallback) {
        return settings.get(key).filter(value -> !value.isBlank())
                .or(() -> settings.get(fallback).filter(value -> !value.isBlank()));
    }

    private static SecretSourceException invalid(String reason) {
        return new SecretSourceException("source vault", reason);
    }
}
