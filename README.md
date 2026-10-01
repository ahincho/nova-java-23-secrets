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
| `nova-secrets` | `pe.edu.nova.java.libs` | el contrato, sin Spring ni proveedores, y la implementación por defecto: el entorno del proceso | publicado |
| `nova-secrets-vault` | `pe.edu.nova.java.libs` | Vault, motor KV versión 2 | publicado |
| `nova-secrets-aws-secrets-manager` | `pe.edu.nova.java.libs` | AWS Secrets Manager | publicado |
| `nova-secrets-spring-boot-starter` | `pe.edu.nova.java.starters` | conecta cualquier fuente con Spring Boot y aplica las reglas | publicado |
| `nova-secrets-quarkus-extension` | `pe.edu.nova.java.starters` | lo mismo para Quarkus, como fuentes de SmallRye Config ([ADR-049](https://github.com/ahincho/nova-shared-01-docs/blob/main/adrs/shared/ADR-049-secretos-en-quarkus-y-nestjs.md)) | nuevo |
| `nova-secrets-quarkus-extension-deployment` | `pe.edu.nova.java.starters` | sus pasos de build; Quarkus lo resuelve solo, el servicio no lo declara | nuevo |

Todos se publican en `https://maven.pkg.github.com/ahincho/nova-java-23-secrets` con la misma
versión. La última está en los [releases](https://github.com/ahincho/nova-java-23-secrets/releases).

## Cómo se usa en Spring Boot

El servicio declara el starter y, si lee de un almacén, su adaptador. Después lee sus secretos como
cualquier propiedad:

```yaml
spring:
  config:
    import: nova-secrets:vault:ms-course        # o nova-secrets:aws-secrets-manager:prod/ms-course/db
  datasource:
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
```

- **La fuente del entorno se aplica sola** cuando hay algo que desdoblar: variables nombradas en
  `nova.secrets.env.variables`, las que empiezan con `nova.secrets.env.prefix` o las que agrega
  `NOVA_SECRETS`. No hace falta ninguna importación.
- **Un almacén se pide con `spring.config.import`**, como `nova-secrets:<fuente>:<referencia>`. Todo
  lo que va después de la fuente es la referencia, así que un ARN de AWS se escribe tal cual. Con
  `optional:` delante, un secreto que no existe no corta el arranque; sin él, sí.
- **O con `nova.secrets.import`**, la misma forma que en Quarkus y en NestJS: `vault:ms-course`, sin
  el `nova-secrets:` delante, y varias separadas por coma. Desde la task definition es la variable
  `NOVA_SECRETS_IMPORT`, así que operaciones pide un almacén igual sin saber en qué framework está
  el servicio. Un pedido gana sobre lo que se desdobla del entorno, y entre dos gana el último.
- **Un secreto se comporta como una variable de entorno.** `DB_PASSWORD` se encuentra también como
  `db.password`, así que funciona igual con `${DB_PASSWORD}` y con `@ConfigurationProperties`.
- **Un secreto pisa a una variable suelta con el mismo nombre.** Es lo que hace NestJS, y va contra
  la costumbre de Spring, donde una variable de entorno gana. Con `nova.secrets.override=false` el
  secreto queda justo después de las variables de entorno.
- **Sus valores nunca se muestran en `/actuator/env`**, aunque el servicio active
  `management.endpoint.env.show-values`.

Si un servicio importa varios secretos del mismo almacén, la fuente se crea una sola vez: con Vault,
por ejemplo, inicia sesión una vez y no una por secreto.

## Cómo se usa en Quarkus

El servicio declara la extensión y, si lee de un almacén, su adaptador; los mismos adaptadores que
en Spring Boot. Los almacenes se piden con `nova.secrets.import`, o con la variable
`NOVA_SECRETS_IMPORT` desde la task definition:

```properties
nova.secrets.import=vault:ms-course                # o aws-secrets-manager:prod/ms-course/db
quarkus.datasource.username=${DB_USERNAME}
quarkus.datasource.password=${DB_PASSWORD}
```

- **La referencia es la misma en los tres stacks**: `<fuente>:<referencia>`, con `optional:` delante
  si puede faltar, y varias separadas por coma. En Spring Boot va detrás de `nova-secrets:` en
  `spring.config.import`.
- **La fuente del entorno se aplica sola**, con las mismas propiedades que en Spring Boot:
  `nova.secrets.env.variables`, `nova.secrets.env.prefix` y la variable `NOVA_SECRETS`.
- **Un secreto pisa a una variable de entorno**: sus fuentes tienen ordinal 350, por encima de las
  variables (300). Con `nova.secrets.override=false` bajan a 275, así que la variable gana y el
  secreto sigue ganando sobre `application.properties` (250). Un secreto pedido gana sobre lo que se
  desdobló del entorno, y entre dos pedidos gana el que se escribió después.
- **Un secreto se comporta como una variable de entorno**: `DB_PASSWORD` responde también a
  `db.password`.
- **Los almacenes solo se consultan al arrancar, nunca al construir.** En una imagen nativa el
  secreto se lee al iniciar el binario, no queda grabado en él, y los adaptadores se registran al
  construirla, así que agregar un almacén sigue siendo agregar una dependencia.

Reemplaza el `ConfigSource` que un servicio escribe a mano para desdoblar el JSON que inyecta ECS.

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

## Vault

`nova-secrets-vault` lee el motor KV versión 2 con la API HTTP de Vault y el cliente HTTP del JDK,
sin un cliente de terceros. La referencia es la ruta del secreto dentro del motor:

```yaml
spring:
  config:
    import: nova-secrets:vault:ms-course     # lee secret/data/ms-course
nova:
  secrets:
    vault:
      address: https://vault.internal:8200    # por defecto, VAULT_ADDR
      app-role:
        role-id: ${VAULT_ROLE_ID}
        secret-id: ${VAULT_SECRET_ID}
```

| Propiedad | Por defecto | Para qué |
|---|---|---|
| `nova.secrets.vault.address` | `VAULT_ADDR` | la dirección, http o https |
| `nova.secrets.vault.token` | `VAULT_TOKEN` | un token fijo, lo habitual con el Vault de desarrollo |
| `nova.secrets.vault.app-role.role-id` y `secret-id` | — | AppRole; si hay un `role-id`, gana sobre el token |
| `nova.secrets.vault.app-role.mount` | `approle` | dónde está montado AppRole |
| `nova.secrets.vault.mount` | `secret` | el motor KV versión 2 |
| `nova.secrets.vault.timeout` | `5s` | cuánto se espera a Vault en cada llamada |

- Un secreto que Vault no tiene es un secreto ausente: con `optional:` se salta y sin él corta el
  arranque.
- Un 403 dice que el token o el AppRole no pueden leerlo. Un timeout dice cuánto se esperó.
- Ningún error cita la respuesta, porque la respuesta trae el secreto.
- Con AppRole se inicia sesión una sola vez, la primera vez que se lee un secreto.
- Los redireccionamientos no se siguen, para que el token no viaje a una dirección que el servicio
  no configuró.
- Ni el token ni el `secret-id` aparecen en un `toString()`.

Las pruebas corren contra un Vault real (`hashicorp/vault:2.1.1`) con Testcontainers, incluida una
aplicación Spring Boot que importa su secreto sin una línea de código de Vault. Si la máquina no
tiene Docker, esas pruebas se saltan.

## AWS Secrets Manager

`nova-secrets-aws-secrets-manager` pide el secreto con `GetSecretValue`. La referencia es su nombre o
su ARN, que se escribe tal cual aunque tenga dos puntos:

```yaml
spring:
  config:
    import: nova-secrets:aws-secrets-manager:prod/ms-course/db
```

| Propiedad | Por defecto | Para qué |
|---|---|---|
| `nova.secrets.aws-secrets-manager.region` | `AWS_REGION`, después la cadena del SDK | la región |
| `nova.secrets.aws-secrets-manager.endpoint` | — | solo para un emulador, como Moto o LocalStack |
| `nova.secrets.aws-secrets-manager.timeout` | `5s` | cuánto se espera en cada llamada, reintentos incluidos |

- **Las credenciales salen de la cadena por defecto del SDK**: el entorno, un perfil o, dentro de
  ECS, el rol de la tarea. El adaptador no recibe ninguna credencial por configuración.
- **El secreto se abre con `Secret.fromJson()`**, la misma función que usa la fuente del entorno
  cuando ECS lo inyecta en una variable. Las dos rutas dan las mismas propiedades, así que pasar de
  una a otra no cambia nada más que de dónde sale el secreto.
- **Los errores:**
  - un secreto que no existe es un secreto ausente;
  - un permiso negado dice que el rol no puede leerlo, y lleva el código de AWS;
  - un secreto guardado como binario corta el arranque, porque no se puede abrir como propiedades.
- **Usa el cliente HTTP liviano del SDK**, basado en `HttpURLConnection`. Los de Apache y Netty no
  entran en el classpath del servicio.

Las pruebas corren contra Moto (`motoserver/moto:5.2.3`), un emulador de AWS de código abierto que no
pide cuenta.

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

Requiere JDK 25. El build usa el toolchain de Java de Nova: la raíz aplica
`pe.edu.nova.java.quality` y cada módulo `pe.edu.nova.java.library`, que se resuelven desde GitHub
Packages, así que Gradle necesita `GITHUB_ACTOR` y un `GITHUB_TOKEN` con `read:packages`.

```bash
./gradlew build
./gradlew novaFormat
```

`build` corre lo mismo que el CI: el formato, Checkstyle, las pruebas y una cobertura mínima del 80 %
de líneas en cada módulo. `novaFormat` corrige el formato. El primer build instala un hook que valida
cada mensaje de commit con Conventional Commits, y el CI valida los commits de cada PR.

Las pruebas levantan un Vault y un Moto reales con Testcontainers, así que piden Docker.

## Licencia

Eclipse Public License 2.0 — ver [LICENSE](LICENSE).

Copyright © 2026 Angel Hincho.
