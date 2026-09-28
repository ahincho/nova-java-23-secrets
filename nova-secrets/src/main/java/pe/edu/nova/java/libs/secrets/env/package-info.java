/**
 * La implementación por defecto de Nova: secretos que el orquestador pone en el entorno del proceso.
 *
 * <p>Es el caso de ECS, que inyecta un secreto de AWS Secrets Manager entero, como un objeto JSON,
 * en una sola variable. No depende de ningún proveedor, así que vive en el mismo módulo que el
 * contrato (ADR-034). Su semántica es la misma que la de {@code unfoldSecrets()} en NestJS, para
 * que operaciones configure igual un servicio de cualquiera de los dos stacks.
 */
package pe.edu.nova.java.libs.secrets.env;
