# nova-java-23-secrets

La capacidad de secretos de Nova Platform. Un servicio lee sus secretos como cualquier otra
propiedad de configuración y **nunca sabe de dónde salen**: del entorno que arma el orquestador, de
Vault o de AWS Secrets Manager. Cambiar de almacén es cambiar una dependencia y una línea de
configuración, sin tocar código.

Las decisiones están en [ADR-042](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-042-secretos-detras-de-un-contrato.md),
y la forma del repositorio —un contrato y todas sus implementaciones juntos, con una sola
versión— en [ADR-041](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/java/ADR-041-un-repositorio-por-capacidad.md).

## Módulos

| Módulo | `groupId` | Qué es | Estado |
|---|---|---|---|
| `nova-secrets` | `pe.edu.nova.java.libs` | el contrato, sin Spring ni proveedores, y la implementación por defecto: el entorno del proceso | listo, sin publicar |
| `nova-secrets-vault` | `pe.edu.nova.java.libs` | Vault, motor KV versión 2 | planificado |
| `nova-secrets-aws-secrets-manager` | `pe.edu.nova.java.libs` | AWS Secrets Manager | planificado |
| `nova-secrets-spring-boot-starter` | `pe.edu.nova.java.starters` | conecta cualquier fuente con Spring Boot y aplica las reglas | planificado |

Todos se publican en `https://maven.pkg.github.com/ahincho/nova-java-23-secrets` con la misma
versión.

## Por qué un contrato propio

Spring Cloud Vault y Spring Cloud AWS ya resuelven esto para Spring, pero cada uno con su propia
configuración, su propia respuesta a qué pasa cuando un secreto falta, y nada para Quarkus. Con un
contrato propio, las mismas reglas valen para cualquier almacén y cualquier framework, y el costo
aceptado es que Nova mantiene su cliente de Vault.

## Las reglas

Las aplica Nova siempre, sea cual sea la fuente:

1. Los secretos se cargan **antes de que exista la aplicación**, para que un `DataSource` ya los
   encuentre.
2. Una variable de entorno ausente no falla, para que una corrida local y los tests usen sus
   propias variables. Una referencia a un almacén que no existe sí falla, salvo que se marque
   `optional:`.
3. Un secreto que no es un objeto JSON **corta el arranque**, y el error nombra el secreto, nunca su
   contenido.
4. El valor **nunca llega a un log**.
5. Toda llamada a un almacén lleva timeout.
6. Un secreto pisa a una propiedad suelta con el mismo nombre.
7. Solo cuentan los valores escalares: un objeto, una lista o `null` dentro del secreto se ignoran.

## El JSON de AWS

Un secreto de AWS Secrets Manager es un texto con un objeto JSON adentro, no una variable por
clave:

```json
{ "username": "course", "password": "…", "host": "db.internal", "port": 5432 }
```

Hay que abrirlo para que cada clave sea una propiedad, y Nova lo hace en un solo lugar,
`Secret.fromJson()`. Lo usan la fuente del entorno, cuando ECS inyecta el secreto entero en una
variable, y el adaptador de Secrets Manager, cuando el servicio lo pide al arrancar. Las dos rutas
dan exactamente las mismas propiedades.

## La fuente del entorno

Es la implementación por defecto y viene en `nova-secrets`. Lee las variables de entorno que traen
un objeto JSON, que es como ECS inyecta un secreto entero. Qué variables lee se decide sin nombrar
ningún secreto en el código:

| Cómo | Dónde se declara | Para qué |
|---|---|---|
| una por una | `nova.secrets.env.variables` | los secretos que el servicio conoce |
| por prefijo | `nova.secrets.env.prefix` | la convención de la organización; no tiene valor por defecto y va en el starter de la organización |
| en tiempo de ejecución | la variable `NOVA_SECRETS` | la salida de emergencia de quien opera el servicio: agrega un secreto sin tocar el código ni publicar una versión |

Una variable ausente o en blanco no es un error. Una que no trae un objeto JSON corta el arranque.
Es la misma semántica de `unfoldSecrets()` en NestJS, con los mismos nombres, así que operaciones
configura igual un servicio de cualquiera de los dos stacks.

## Agregar un almacén

Un almacén nuevo es un módulo con dos clases y un archivo, sin tocar el contrato ni los
conectores:

```java
public final class MyStoreSecretSourceProvider implements SecretSourceProvider {

    @Override
    public String name() {
        return "my-store";
    }

    @Override
    public SecretSource create(SecretSettings settings) {
        String address = settings.get("nova.secrets.my-store.address")
                .orElseThrow(() -> new SecretSourceException("my-store", "needs nova.secrets.my-store.address"));
        return reference -> fetch(address, reference).map(json -> Secret.fromJson(reference, json));
    }
}
```

Se registra en `META-INF/services/pe.edu.nova.java.libs.secrets.SecretSourceProvider`, y
`SecretSources` lo encuentra con `ServiceLoader` en cualquier framework. Si el almacén guarda
JSON, `Secret.fromJson()` ya aplica las reglas: solo escalares, y ningún error cita el contenido.

## Desarrollo

Requiere JDK 25.

```bash
./gradlew build
```

## Licencia

Eclipse Public License 2.0 — ver [LICENSE](LICENSE).

Copyright © 2026 Angel Hincho.
