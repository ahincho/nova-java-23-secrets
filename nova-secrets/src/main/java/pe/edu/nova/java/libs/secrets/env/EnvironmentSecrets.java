package pe.edu.nova.java.libs.secrets.env;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import pe.edu.nova.java.libs.secrets.Secret;
import pe.edu.nova.java.libs.secrets.SecretSettings;

/**
 * Decide qué variables de entorno traen un secreto y las desdobla.
 *
 * <p>Nada de esto nombra un secreto de ningún servicio: las variables se nombran en la
 * configuración, se descubren por un prefijo que pone la organización, o las agrega quien opera el
 * servicio con {@value #VARIABLES_VARIABLE}, sin tocar el código ni publicar una versión.
 */
public final class EnvironmentSecrets {

    /**
     * La variable con que quien opera el servicio agrega secretos, separados por coma. Es la salida
     * de emergencia para el secreto que no sigue la convención, y se llama igual en NestJS.
     */
    public static final String VARIABLES_VARIABLE = "NOVA_SECRETS";

    /** Las variables que el servicio nombra una por una, separadas por coma. */
    public static final String VARIABLES_SETTING = "nova.secrets.env.variables";

    /**
     * El prefijo por el que se descubren: toda variable que empiece así trae un secreto entero. No
     * tiene valor por defecto porque es de la organización, que es quien sabe que ninguna otra
     * variable de su entorno empieza así, y va en su starter (ADR-036).
     */
    public static final String PREFIX_SETTING = "nova.secrets.env.prefix";

    private EnvironmentSecrets() {}

    /**
     * Las variables que este servicio va a desdoblar, sin repetir.
     *
     * <p>Van primero las que nombra la configuración, después las de {@value #VARIABLES_VARIABLE}
     * y al final las que empiezan con el prefijo, en orden alfabético para que el resultado no
     * dependa del orden del entorno. {@value #VARIABLES_VARIABLE} nunca se toma como un secreto,
     * aunque el prefijo la alcance.
     *
     * <p>Se expone aparte del desdoblado para poder responder qué secretos ve este proceso sin leer
     * ninguno, que es la primera pregunta cuando una credencial no aparece.
     *
     * @param settings    la configuración del servicio
     * @param environment el entorno del proceso
     * @return los nombres de las variables, en el orden en que se desdoblan
     */
    public static List<String> variables(SecretSettings settings, Map<String, String> environment) {
        Set<String> names = new LinkedHashSet<>(settings.getList(VARIABLES_SETTING));
        Optional.ofNullable(environment.get(VARIABLES_VARIABLE))
                .map(SecretSettings::splitList)
                .ifPresent(names::addAll);
        settings.get(PREFIX_SETTING)
                .map(String::trim)
                .filter(prefix -> !prefix.isEmpty())
                .ifPresent(prefix -> environment.keySet().stream()
                        .filter(name -> name.startsWith(prefix))
                        .filter(name -> !name.equals(VARIABLES_VARIABLE))
                        .sorted()
                        .forEach(names::add));
        return List.copyOf(names);
    }

    /**
     * Desdobla las variables que traen un secreto.
     *
     * @param settings    la configuración del servicio
     * @param environment el entorno del proceso
     * @return los secretos encontrados, en el orden de {@link #variables}; una variable ausente o en
     *         blanco no aparece
     * @throws pe.edu.nova.java.libs.secrets.SecretSourceException si una variable no trae un objeto
     *                                                             JSON
     */
    public static List<Secret> unfold(SecretSettings settings, Map<String, String> environment) {
        EnvironmentSecretSource source = new EnvironmentSecretSource(environment::get);
        return variables(settings, environment).stream()
                .map(source::find)
                .flatMap(Optional::stream)
                .toList();
    }
}
