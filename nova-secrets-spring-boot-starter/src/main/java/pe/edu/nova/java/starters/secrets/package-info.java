/**
 * El conector de la capacidad de secretos con Spring Boot (ADR-042).
 *
 * <p>Carga los secretos antes de que exista la aplicación, por dos caminos:
 *
 * <ul>
 *   <li>la fuente del entorno se aplica sola cuando hay algo que desdoblar, con un
 *       {@link org.springframework.boot.EnvironmentPostProcessor};</li>
 *   <li>un almacén se pide con {@code spring.config.import=nova-secrets:<fuente>:<referencia>}, con la
 *       API de {@code ConfigData}.</li>
 * </ul>
 *
 * <p>En los dos, un secreto queda como una fuente de propiedades que se comporta como una variable
 * de entorno, así que {@code ${DB_PASSWORD}} y una propiedad {@code db.password} lo encuentran por
 * igual. Nunca se muestra en {@code /actuator/env}.
 */
package pe.edu.nova.java.starters.secrets;
