/**
 * El contrato de la capacidad de secretos de Nova (ADR-042).
 *
 * <p>Un servicio nunca usa estas clases: lee sus secretos como propiedades de configuración. Las usa
 * el conector de cada framework, que antes de que exista la aplicación le pide a una
 * {@link pe.edu.nova.java.libs.secrets.SecretSource} los secretos del servicio y los convierte en
 * propiedades. De qué almacén salen lo decide la dependencia que el servicio declara: cada
 * almacén publica su {@link pe.edu.nova.java.libs.secrets.SecretSourceProvider} y
 * {@link pe.edu.nova.java.libs.secrets.SecretSources} lo descubre.
 *
 * <p>El paquete no importa nada de Spring, de Quarkus ni de ningún proveedor (ADR-015), para que la
 * misma implementación sirva en cualquier framework.
 */
package pe.edu.nova.java.libs.secrets;
