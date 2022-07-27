// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bjorncs
 */
public class Pkcs10CsrBuilderTest {

    @Test
    void can_build_csr_with_sans() {
        X500Principal subject = new X500Principal("CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA512_WITH_ECDSA)
                .addSubjectAlternativeName("san1.com")
                .addSubjectAlternativeName("san2.com")
                .build();
        assertEquals(subject, csr.getSubject());
    }

}