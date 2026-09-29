/**
 * El adaptador de la capacidad de secretos para AWS Secrets Manager (ADR-042).
 *
 * <p>Pide el secreto con {@code GetSecretValue} y lo abre con
 * {@link pe.edu.nova.java.libs.secrets.Secret#fromJson}, la misma función que usa la fuente del
 * entorno cuando ECS inyecta el secreto entero en una variable. Por eso las dos rutas dan
 * exactamente las mismas propiedades, y un servicio puede pasar de una a otra sin tocar su
 * configuración.
 */
package pe.edu.nova.java.libs.secrets.aws;
