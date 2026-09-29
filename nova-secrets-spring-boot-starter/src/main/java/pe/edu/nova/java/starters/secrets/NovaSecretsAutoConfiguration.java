package pe.edu.nova.java.starters.secrets;

import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;

/**
 * Oculta los valores que vienen de un secreto en los endpoints del actuator.
 *
 * <p>Spring Boot ya oculta los valores de {@code /actuator/env} por defecto, pero un servicio puede
 * mostrarlos con {@code management.endpoint.env.show-values}. Esta función los oculta igual cuando
 * vienen de un secreto. No tiene condición para apagarse porque es una regla del núcleo
 * (ADR-042, regla 4), no una convención.
 */
@AutoConfiguration
@ConditionalOnClass(SanitizingFunction.class)
public class NovaSecretsAutoConfiguration {

    /** Crea la auto-configuración; la instancia Spring Boot. */
    public NovaSecretsAutoConfiguration() {
    }

    /**
     * La función que oculta los valores de un secreto.
     *
     * @return la función
     */
    @Bean
    public SanitizingFunction novaSecretsSanitizingFunction() {
        return NovaSecretsAutoConfiguration::sanitize;
    }

    static SanitizableData sanitize(SanitizableData data) {
        return SecretPropertySources.isSecret(data.getPropertySource()) ? data.withSanitizedValue() : data;
    }
}
