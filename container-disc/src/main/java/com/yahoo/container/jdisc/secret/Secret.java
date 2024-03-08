package com.yahoo.container.jdisc.secret;

import com.yahoo.security.YBase64;
import com.yahoo.text.Utf8;

import java.util.Arrays;
import java.util.Objects;

public class Secret {

    private final Key key;
    private final byte[] secret;
    private final int version;

    public Secret(Key key, byte[] secret, int version) {
        this.key = key;
        this.secret = secret;
        this.version = version;
    }

    public String keyGroup() {
        return key.keyGroup();
    }

    public String keyName() {
        return key.keyName();
    }

    public byte[] secret() {
        return secret;
    }

    public String secretAsString() { return Utf8.toString(secret); }

    /** @return secret value for keys that are auto-rotated by CKMS */
    public byte[] secretAsYbase64Decoded() { return YBase64.decode(secret); }

    public int version() {
        return version;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Secret that = (Secret) o;
        if ( ! (that.key.equals(key))) return false;
        if ( ! (Arrays.equals(that.secret, secret))) return false;
        if (that.version != (version)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, version, Arrays.hashCode(secret));
    }

}
