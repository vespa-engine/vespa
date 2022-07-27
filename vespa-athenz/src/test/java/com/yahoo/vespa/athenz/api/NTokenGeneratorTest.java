// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import com.yahoo.athenz.auth.util.CryptoException;
import com.yahoo.test.ManualClock;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/**
 * @author hakonhall
 */
public class NTokenGeneratorTest {
    private final PrivateKey key = mock(PrivateKey.class);

    @Test
    void ntoken() {
        var signer = new Signer("signature");
        NTokenGenerator generator = new NTokenGenerator(signer, new ManualClock(Instant.ofEpochSecond(12L)), () -> 3L);
        AthenzIdentity identity = new AthenzService("domain", "service");

        NToken token = generator.setIdentity(identity)
                .setKeyVersion("0")
                .sign(key);

        assertEquals("v=S1;d=domain;n=service;k=0;a=3;t=12;e=612", signer.message);
        assertSame(key, signer.key);
        assertEquals("v=S1;d=domain;n=service;k=0;a=3;t=12;e=612;s=signature", token.getRawToken());
    }

    private static class Signer implements NTokenGenerator.Signer {
        private final String signature;
        public String message = null;
        public PrivateKey key = null;

        public Signer(String signature) {
            this.signature = signature;
        }

        @Override
        public String sign(String message, PrivateKey key) throws CryptoException {
            this.message = message;
            this.key = key;
            return signature;
        }
    }
}