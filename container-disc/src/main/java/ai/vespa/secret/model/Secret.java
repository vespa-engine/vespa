package ai.vespa.secret.model;

import com.yahoo.text.Utf8;

import java.util.Arrays;
import java.util.Objects;

public class Secret implements Comparable<Secret> {

    private final Key key;
    private final byte[] secret;
    private final SecretVersionId version;
    private final SecretVersionState state;

    public Secret(Key key, byte[] secret, SecretVersionId version) {
        this(key, secret, version, SecretVersionState.CURRENT);
    }

    public Secret(Key key, byte[] secret, SecretVersionId version, SecretVersionState state) {
        this.key = key;
        this.secret = secret;
        this.version = version;
        this.state = state;
    }

    public VaultName vaultName() {
        return key.vaultName();
    }

    public SecretName secretName() {
        return key.secretName();
    }

    public byte[] secret() {
        return secret;
    }

    public SecretValue secretValue() {
        return SecretValue.of(secretAsString());
    }

    public String secretAsString() { return Utf8.toString(secret); }

    public SecretVersionId version() {
        return version;
    }

    public SecretVersionState state() {
        return state;
    }

    public static Key key(VaultName vaultName, SecretName secretName) {
        return new Key(vaultName, secretName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Secret that = (Secret) o;
        if ( ! (that.key.equals(key))) return false;
        if ( ! (Arrays.equals(that.secret, secret))) return false;
        if (! that.version.equals(version)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, version, Arrays.hashCode(secret));
    }

    @Override
    public String toString() {
        return "Secret{" +
                "key=" + key +
                ", version=" + version +
                ", state=" + state +
                ", secret=<omitted>"+
                '}';
    }

    @Override
    public int compareTo(Secret o) {
        int v = key.vaultName().compareTo(o.key.vaultName());
        if (v != 0) return v;
        int n = key.secretName().compareTo(o.key.secretName());
        if (n != 0) return n;
        int s = state.compareTo(o.state);
        if (s != 0) return s;

        // Note: reversed for descending order
        return o.version.value().compareTo(version.value());
    }
}
