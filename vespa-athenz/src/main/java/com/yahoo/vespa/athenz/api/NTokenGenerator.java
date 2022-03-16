// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import com.yahoo.athenz.auth.util.Crypto;
import com.yahoo.athenz.auth.util.CryptoException;

import java.security.PrivateKey;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.function.LongSupplier;

import static java.lang.String.format;
import static java.util.Objects.requireNonNull;

/**
 * @author hakonhall
 */
public class NTokenGenerator {
    private final Signer signer;
    private final Clock clock;

    private String domain = null;
    private String name = null;
    private String keyVersion = null;
    private String keyService = null;
    private String hostname = null;
    private String ip = null;

    private final StringBuilder token = new StringBuilder();
    private final LongSupplier randomGenerator;

    @FunctionalInterface
    public interface Signer {
        /** @see Crypto#sign(String, PrivateKey) */
        String sign(String message, PrivateKey key) throws CryptoException;
    }

    public NTokenGenerator() { this(Crypto::sign, Clock.systemUTC(), new SecureRandom()::nextLong); }

    /** For testing. */
    NTokenGenerator(Signer signer, Clock clock, LongSupplier randomGenerator) {
        this.signer = signer;
        this.clock = clock;
        this.randomGenerator = randomGenerator;
    }

    /** Required. */
    public NTokenGenerator setIdentity(AthenzIdentity identity) {
        this.domain = identity.getDomainName();
        this.name = identity.getName();
        return this;
    }

    /** Required. */
    public NTokenGenerator setKeyVersion(String keyVersion) {
        this.keyVersion = requireNonNull(keyVersion);
        return this;
    }

    public NTokenGenerator setKeyService(String keyService) {
        this.keyService = requireNonNull(keyService);
        return this;
    }

    public NTokenGenerator setHostname(String hostname) {
        this.hostname = requireNonNull(hostname);
        return this;
    }

    public NTokenGenerator setIp(String ip) {
        this.ip = requireNonNull(ip);
        return this;
    }

    public NToken sign(PrivateKey privateKey) {
        // See https://github.com/AthenZ/athenz/blob/master/libs/go/zmssvctoken/token.go

        var generationTime = clock.instant();

        token.setLength(0);
        append('v', "S1");
        append('d', domain);
        append('n', name);
        append('k', keyVersion);
        append('z', keyService, false);
        append('h', hostname, false);
        append('i', ip, false);
        append('a', format("%x", randomGenerator.getAsLong()));
        append('t', format("%d", generationTime.getEpochSecond()));
        append('e', format("%d", generationTime.plus(Duration.ofMinutes(10)).getEpochSecond()));
        append('s', signer.sign(token.toString(), privateKey));

        return new NToken(token.toString());
    }

    private void append(char name, String value) { append(name, value, true); }

    private void append(char name, String value, boolean required) {
        if (value == null) {
            if (required) {
                throw new IllegalStateException("Missing value for NToken key " + name);
            } else {
                return;
            }
        }

        if (token.length() > 0) {
            token.append(';');
        }

        token.append(name).append('=').append(value);
    }
}
