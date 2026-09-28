package pe.edu.nova.java.libs.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.junit.jupiter.api.Test;

class SecretTest {

    @Test
    void fromJsonTurnsEachTopLevelKeyIntoAnEntry() {
        Secret secret = Secret.fromJson("SECRET_DB",
                "{\"username\": \"course\", \"password\": \"s3cr3t\", \"host\": \"db.internal\"}");

        assertEquals(Map.of("username", "course", "password", "s3cr3t", "host", "db.internal"), secret.entries());
        assertEquals("SECRET_DB", secret.reference());
    }

    @Test
    void fromJsonKeepsTheOrderOfTheKeys() {
        Secret secret = Secret.fromJson("s", "{\"c\": \"3\", \"a\": \"1\", \"b\": \"2\"}");

        assertEquals(List.of("c", "a", "b"), List.copyOf(secret.keys()));
    }

    @Test
    void fromJsonWritesNumbersAndBooleansAsTheyAreWritten() {
        Secret secret = Secret.fromJson("s", "{\"port\": 5432, \"ratio\": 1.50, \"tls\": true, \"debug\": false}");

        assertEquals(Optional.of("5432"), secret.get("port"));
        assertEquals(Optional.of("1.50"), secret.get("ratio"));
        assertEquals(Optional.of("true"), secret.get("tls"));
        assertEquals(Optional.of("false"), secret.get("debug"));
    }

    @Test
    void fromJsonIgnoresObjectsListsAndNulls() {
        Secret secret = Secret.fromJson("s",
                "{\"kept\": \"yes\", \"nested\": {\"password\": \"x\"}, \"list\": [1, 2], \"empty\": null}");

        assertEquals(Map.of("kept", "yes"), secret.entries());
    }

    @Test
    void fromJsonLetsTheLastRepeatedKeyWin() {
        Secret secret = Secret.fromJson("s", "{\"key\": \"first\", \"key\": \"last\"}");

        assertEquals(Optional.of("last"), secret.get("key"));
    }

    @Test
    void fromJsonAcceptsAnEmptyObject() {
        assertTrue(Secret.fromJson("s", "{}").entries().isEmpty());
    }

    @Test
    void fromJsonRejectsTextThatIsNotJson() {
        SecretSourceException error = assertThrows(SecretSourceException.class,
                () -> Secret.fromJson("SECRET_DB", "username=course"));

        assertEquals("Secret SECRET_DB could not be parsed as JSON", error.getMessage());
        assertEquals("SECRET_DB", error.reference());
        assertNull(error.getCause());
    }

    @Test
    void fromJsonRejectsJsonThatIsNotAnObject() {
        for (String json : List.of("[{\"a\": \"1\"}]", "\"text\"", "42", "true", "null")) {
            SecretSourceException error = assertThrows(SecretSourceException.class, () -> Secret.fromJson("s", json));

            assertEquals("Secret s does not contain a JSON object", error.getMessage(), json);
        }
    }

    @Test
    void fromJsonRejectsContentAfterTheObject() {
        assertThrows(SecretSourceException.class, () -> Secret.fromJson("s", "{\"a\": \"1\"} {\"b\": \"2\"}"));
        assertThrows(SecretSourceException.class, () -> Secret.fromJson("s", "{\"a\": \"1\"} trailing"));
    }

    @Test
    void fromJsonRejectsAnEmptyText() {
        assertThrows(SecretSourceException.class, () -> Secret.fromJson("s", ""));
    }

    @Property
    void aBrokenSecretNeverAppearsInTheError(@ForAll @AlphaChars @StringLength(min = 12, max = 40) String value) {
        String truncated = "{\"password\": \"" + value;

        SecretSourceException error = assertThrows(SecretSourceException.class, () -> Secret.fromJson("s", truncated));

        assertFalse(error.getMessage().contains(value));
        assertNull(error.getCause());
    }

    @Property
    void toStringNeverShowsAValue(@ForAll @AlphaChars @StringLength(min = 12, max = 40) String value) {
        Secret secret = Secret.fromJson("SECRET_DB", "{\"password\": \"" + value + "\"}");

        assertEquals("Secret[reference=SECRET_DB, keys=[password]]", secret.toString());
        assertFalse(secret.toString().contains(value));
    }

    @Test
    void ofCopiesTheEntries() {
        Map<String, String> entries = new LinkedHashMap<>();
        entries.put("username", "course");

        Secret secret = Secret.of("vault:ms-course", entries);
        entries.put("password", "late");

        assertEquals(Map.of("username", "course"), secret.entries());
        assertThrows(UnsupportedOperationException.class, () -> secret.entries().put("x", "y"));
    }

    @Test
    void secretsWithTheSameReferenceAndEntriesAreEqual() {
        Secret first = Secret.fromJson("s", "{\"a\": \"1\"}");
        Secret second = Secret.of("s", Map.of("a", "1"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }
}
