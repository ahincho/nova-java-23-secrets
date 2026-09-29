/**
 * El adaptador de la capacidad de secretos para HashiCorp Vault (ADR-042).
 *
 * <p>Lee el motor KV versión 2 con la API HTTP de Vault y el cliente HTTP del JDK, sin un cliente de
 * terceros. Se autentica con un token o con AppRole, y cada llamada lleva timeout. La respuesta se
 * abre con {@link pe.edu.nova.java.libs.secrets.Secret#fromJson}, así que aplica las mismas reglas
 * que cualquier otra fuente: solo cuentan los escalares, y ningún error cita el contenido.
 */
package pe.edu.nova.java.libs.secrets.vault;
