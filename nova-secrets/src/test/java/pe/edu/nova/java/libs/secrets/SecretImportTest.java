package pe.edu.nova.java.libs.secrets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SecretImportTest {

    @Test
    void parseSplitsTheSourceFromTheReference() {
        SecretImport secretImport = SecretImport.parse(" vault:ms-course ");

        assertEquals("vault", secretImport.source());
        assertEquals("ms-course", secretImport.reference());
        assertFalse(secretImport.optional());
    }

    @Test
    void onlyTheFirstColonSeparatesTheSource() {
        SecretImport secretImport =
                SecretImport.parse("aws-secrets-manager:arn:aws:secretsmanager:us-east-1:1:secret:db");

        assertEquals("aws-secrets-manager", secretImport.source());
        assertEquals("arn:aws:secretsmanager:us-east-1:1:secret:db", secretImport.reference());
    }

    @Test
    void theOptionalPrefixMarksASecretThatMayBeMissing() {
        SecretImport secretImport = SecretImport.parse("optional:vault:ms-course");

        assertTrue(secretImport.optional());
        assertEquals("vault", secretImport.source());
        assertEquals("optional:vault:ms-course", secretImport.toString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"vault", "vault:", ":ms-course", "optional:vault", ""})
    void aMalformedImportSaysHowToWriteIt(String raw) {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> SecretImport.parse(raw));

        assertTrue(error.getMessage().contains("<source>:<reference>"), error.getMessage());
    }

    @Test
    void parseAllKeepsTheOrderAndDropsBlanks() {
        assertEquals(
                List.of(SecretImport.parse("vault:a"), SecretImport.parse("optional:aws-secrets-manager:b")),
                SecretImport.parseAll("vault:a, ,optional:aws-secrets-manager:b,"));
        assertEquals(List.of(), SecretImport.parseAll(null));
        assertEquals(List.of(), SecretImport.parseAll("  "));
    }
}
