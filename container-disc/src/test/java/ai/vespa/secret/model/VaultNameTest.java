package ai.vespa.secret.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author bragehk
 */
public class VaultNameTest {

    @Test
    void testVaultName() {
        VaultName.of("foo-bar");
        VaultName.of("0");
        VaultName.of("_foo");
        VaultName.of("foo.bar");
        VaultName.of("foo.bar-baz");
        VaultName.of("foo.bar.baz");
        VaultName.of("my.vault_name-1");
        VaultName.of("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
        assertThrows(IllegalArgumentException.class, () -> VaultName.of(""));
        assertThrows(IllegalArgumentException.class, () -> VaultName.of("-"));
        assertThrows(IllegalArgumentException.class, () -> VaultName.of("."));
        assertThrows(IllegalArgumentException.class, () -> VaultName.of(".."));
        assertThrows(IllegalArgumentException.class, () -> VaultName.of(".foo"));
        assertThrows(IllegalArgumentException.class, () -> VaultName.of("foo.-bar"));
        assertThrows(IllegalArgumentException.class, () -> VaultName.of("0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0"));

        for (char c : "+/$ {}[]()!\"@#?\\'".toCharArray())
            assertThrows(IllegalArgumentException.class, () -> VaultName.of("foo" + c + "bar"));
    }

}
