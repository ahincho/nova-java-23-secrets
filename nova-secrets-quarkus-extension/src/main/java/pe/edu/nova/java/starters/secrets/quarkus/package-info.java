/**
 * El conector de la capacidad de secretos con Quarkus (ADR-049).
 *
 * <p>Carga los secretos como fuentes de configuración antes de que exista la aplicación, con los mismos
 * adaptadores y las mismas reglas que el starter de Spring Boot. El servicio escribe
 * {@code ${DB_PASSWORD}} y nunca sabe de qué almacén sale.
 */
package pe.edu.nova.java.starters.secrets.quarkus;
