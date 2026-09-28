package pe.edu.nova.java.libs.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SecretSettingsTest {

    @Test
    void ofReturnsTheConfiguredValues() {
        SecretSettings settings = SecretSettings.of(Map.of("nova.secrets.env.prefix", "SECRET_"));

        assertEquals(Optional.of("SECRET_"), settings.get("nova.secrets.env.prefix"));
        assertTrue(settings.get("missing").isEmpty());
    }

    @Test
    void ofIsNotAffectedByLaterChangesToTheMap() {
        Map<String, String> values = new HashMap<>();
        SecretSettings settings = SecretSettings.of(values);
        values.put("key", "late");

        assertTrue(settings.get("key").isEmpty());
    }

    @Test
    void getListSplitsOnCommasAndDropsBlanks() {
        SecretSettings settings = SecretSettings.of(Map.of("list", " SECRET_DB , ,SECRET_LEGACY,"));

        assertEquals(List.of("SECRET_DB", "SECRET_LEGACY"), settings.getList("list"));
    }

    @Test
    void getListIsEmptyWhenTheKeyIsMissing() {
        assertEquals(List.of(), SecretSettings.empty().getList("list"));
    }
}
