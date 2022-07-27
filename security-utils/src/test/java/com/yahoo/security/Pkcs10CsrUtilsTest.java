// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
public class Pkcs10CsrUtilsTest {

    @Test
    void can_deserialize_serialized_pem_csr() {
        X500Principal subject = new X500Principal("CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA512_WITH_ECDSA).build();
        String pem = Pkcs10CsrUtils.toPem(csr);
        Pkcs10Csr deserializedCsr = Pkcs10CsrUtils.fromPem(pem);
        assertTrue(pem.contains("BEGIN CERTIFICATE REQUEST"));
        assertTrue(pem.contains("END CERTIFICATE REQUEST"));
        assertEquals(subject, deserializedCsr.getSubject());
    }

}