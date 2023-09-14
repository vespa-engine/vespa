package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;
import com.google.common.io.CharStreams;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Wraps a Wireguard key.
 * For security reasons, this should only be used for public keys, although private keys use the same format.
 *
 * @author gjoranv
 */
public class WireguardKey extends PatternedStringWrapper<WireguardKey> {

    // See https://stackoverflow.com/questions/74438436/how-to-validate-a-wireguard-public-key
    private static final Pattern pattern = Pattern.compile("^[A-Za-z0-9+/]{42}[AEIMQUYcgkosw480]=$");

    public WireguardKey(String value) {
        super(value, pattern, "Wireguard key");
    }

    public static WireguardKey from(String value) {
        return new WireguardKey(value);
    }

    @Override
    public String toString() {
        return "Wireguard key '" + value() + "'";
    }

    public static WireguardKey generateRandomForTesting() {
        var str = UUID.randomUUID().toString().replace("-", "");
        return new WireguardKey(str + "12345678900=");
    }
}
