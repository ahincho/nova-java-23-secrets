package pe.edu.nova.java.libs.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
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
    void getDurationAcceptsTheShortAndTheIsoForms() {
        SecretSettings settings = SecretSettings.of(Map.of("a", "500ms", "b", "2s", "c", "1m", "d", "PT3S", "e", " "));

        assertEquals(Optional.of(Duration.ofMillis(500)), settings.getDuration("a"));
        assertEquals(Optional.of(Duration.ofSeconds(2)), settings.getDuration("b"));
        assertEquals(Optional.of(Duration.ofMinutes(1)), settings.getDuration("c"));
        assertEquals(Optional.of(Duration.ofSeconds(3)), settings.getDuration("d"));
        assertTrue(settings.getDuration("e").isEmpty());
        assertTrue(settings.getDuration("missing").isEmpty());
    }

    @Test
    void getDurationRejectsWhatIsNotADuration() {
        SecretSettings settings = SecretSettings.of(Map.of("nova.secrets.vault.timeout", "soon"));

        SecretSourceException error =
                assertThrows(SecretSourceException.class, () -> settings.getDuration("nova.secrets.vault.timeout"));

        assertEquals(
                "Secret setting nova.secrets.vault.timeout is not a duration, such as 5s or PT5S", error.getMessage());
    }

    @Test
    void getListIsEmptyWhenTheKeyIsMissing() {
        assertEquals(List.of(), SecretSettings.empty().getList("list"));
    }
}
