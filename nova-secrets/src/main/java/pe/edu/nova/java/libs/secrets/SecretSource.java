package pe.edu.nova.java.libs.secrets;

import java.util.Optional;

/**
 * Un almacén de secretos: el entorno del proceso, Vault, AWS Secrets Manager.
 *
 * <p>Es el puerto de la capacidad. Una implementación solo encuentra el secreto y lo devuelve
 * aplanado; qué pasa cuando falta, cuándo se lee y en qué orden pisa a otras propiedades lo deciden
 * las reglas del núcleo, que aplica el conector del framework y ninguna fuente puede cambiar.
 */
public interface SecretSource {

    /**
     * Busca el secreto que nombra la referencia.
     *
     * @param reference cómo lo nombra el almacén: una variable de entorno, una ruta de Vault o el
     *                  identificador de un secreto de AWS
     * @return el secreto aplanado en clave y valor, o vacío si el almacén no lo tiene
     * @throws SecretSourceException si el secreto existe pero no se puede leer, porque no es un
     *                               objeto JSON o porque el almacén contestó con un error
     */
    Optional<Secret> find(String reference);
}
