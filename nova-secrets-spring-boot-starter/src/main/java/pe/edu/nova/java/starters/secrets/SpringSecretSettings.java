package pe.edu.nova.java.starters.secrets;

import java.util.Optional;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertyName;
import org.springframework.core.env.PropertyResolver;
import pe.edu.nova.java.libs.secrets.SecretSettings;

/**
 * La configuración de una fuente de secretos, leída de Spring.
 *
 * <p>Las claves van en la forma con puntos de la documentación, como {@code vault.addr}. Spring las
 * encuentra también como variable de entorno ({@code VAULT_ADDR}), así que un adaptador respeta la
 * convención de su proveedor sin nombrar la variable.
 */
final class SpringSecretSettings {

    private SpringSecretSettings() {
    }

    static SecretSettings of(PropertyResolver properties) {
        return key -> Optional.ofNullable(properties.getProperty(key));
    }

    static SecretSettings of(Binder binder) {
        return key -> ConfigurationPropertyName.isValid(key)
                ? Optional.ofNullable(binder.bind(key, String.class).orElse(null))
                : Optional.empty();
    }
}
