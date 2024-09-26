package ai.vespa.secret.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author gjoranv
 */
public class KeyTest {

    @Test
    void string_can_be_converted_to_key() {
        var vault = VaultName.of("vaultName");
        var secret = SecretName.of("secretName");

        var expected = new Key(vault, secret);
        assertEquals(expected, Key.fromString("vaultName/secretName"));

        assertThrows(IllegalArgumentException.class, () -> Key.fromString("vaultName"));
        assertThrows(IllegalArgumentException.class, () -> Key.fromString("vaultName/secretName/extra"));
    }
}
