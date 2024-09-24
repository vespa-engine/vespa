package ai.vespa.secret.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static ai.vespa.secret.model.SecretVersionState.CURRENT;
import static ai.vespa.secret.model.SecretVersionState.DEPRECATED;
import static ai.vespa.secret.model.SecretVersionState.PENDING;
import static ai.vespa.secret.model.SecretVersionState.PREVIOUS;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author gjoranv
 */
public class SecretTest {

    @Test
    void secrets_are_sorted_on_vault_then_name_then_state() {

        var s11pe = secret("vault1", "name1", PENDING);
        var s11cu = secret("vault1", "name1", CURRENT);
        var s12cu = secret("vault1", "name2", CURRENT);
        var s21pe = secret("vault2", "name1", PENDING);
        var s21cu = secret("vault2", "name1", CURRENT);
        var s21pr = secret("vault2", "name1", PREVIOUS);
        var s21de = secret("vault2", "name1", DEPRECATED);

        var secrets = List.of( s21pe, s11cu, s12cu, s11pe, s21de, s21pr, s21cu );

        var expected = List.of( s11pe, s11cu, s12cu, s21pe, s21cu, s21pr, s21de );

        assertEquals(expected, secrets.stream().sorted().toList());
    }

    // This is relevant for secrets from CKMS, which don't use state, but ascending version numbers.
    @Test
    void secrets_with_same_state_are_sorted_by_version_descending() {
        var v1 = secretWithIntVersion(1);
        var v2 = secretWithIntVersion(2);
        var v3 = secretWithIntVersion(3);

        var secrets = List.of(v3, v1, v2);
        var expected = List.of(v3, v2, v1);
        assertEquals(expected, secrets.stream().sorted().toList());
    }

    private static Secret secretWithIntVersion(Integer version) {
        return new Secret(new Key(VaultName.of("foo"), SecretName.of("bar")), new byte[0],
                          SecretVersionId.of(version.toString()));
    }

    private static Secret secret(String vault, String name, SecretVersionState state) {
        return new Secret(new Key(VaultName.of(vault), SecretName.of(name)), new byte[0],
                          SecretVersionId.of("0"), state);
    }

}
