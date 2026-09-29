package pe.edu.nova.java.libs.secrets.vault;

/**
 * Cómo se identifica el servicio ante Vault.
 *
 * <p>El {@code toString()} de cada variante nunca muestra una credencial, para que una
 * configuración que termina en un log no la filtre.
 */
sealed interface VaultAuthentication {

    /** Un token fijo: lo habitual en desarrollo, con el Vault de modo {@code -dev}. */
    record Token(String token) implements VaultAuthentication {

        @Override
        public String toString() {
            return "Token[****]";
        }
    }

    /** AppRole: el servicio cambia un {@code role_id} y un {@code secret_id} por un token al arrancar. */
    record AppRole(String roleId, String secretId, String mount) implements VaultAuthentication {

        @Override
        public String toString() {
            return "AppRole[mount=" + mount + ", roleId=****, secretId=****]";
        }
    }
}
