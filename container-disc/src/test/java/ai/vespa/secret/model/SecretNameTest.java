package ai.vespa.secret.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author gjoranv
 */
public class SecretNameTest {

    @Test
    void testSecretName() {
        SecretName.of("foo-bar");
        SecretName.of("-");
        SecretName.of("0");
        SecretName.of("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcde");
        assertThrows(IllegalArgumentException.class, () -> SecretName.of(""));

        // TODO: enable when all secrets are < 64 characters
        //assertThrows(IllegalArgumentException.class, () -> SecretName.of("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"));

        for (char c : "+/$ {}[]()!\"@#?\\'".toCharArray())
            assertThrows(IllegalArgumentException.class, () -> SecretName.of("foo" + c + "bar"));
    }

    @Test
    void testIsValid() {
        assertTrue(SecretName.isValid("foo-bar"));
        assertTrue(SecretName.isValid("my.secret_name-1"));
        assertFalse(SecretName.isValid("dev/aws_access_key_id"));
        assertFalse(SecretName.isValid(""));
        assertFalse(SecretName.isValid("foo bar"));
    }

}
