package com.yahoo.config.provision;

import ai.vespa.validation.PatternedStringWrapper;

import java.util.regex.Pattern;

/**
 * Wraps a Wireguard key.
 * For security reasons, this should only be used for public keys, although private keys use the same format.
 *
 * @author gjoranv
 */
public class WireguardKey extends PatternedStringWrapper<WireguardKey> {

    // See https://lists.zx2c4.com/pipermail/wireguard/2020-December/006222.html
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
}
