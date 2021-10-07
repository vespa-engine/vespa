// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.List;

import static com.yahoo.security.SubjectAlternativeName.Type.DNS_NAME;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class Pkcs10CsrTest {

    @Test
    public void can_read_subject_alternative_names() {
        X500Principal subject = new X500Principal("CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        SubjectAlternativeName san1 = new SubjectAlternativeName(DNS_NAME, "san1.com");
        SubjectAlternativeName san2 = new SubjectAlternativeName(DNS_NAME, "san2.com");
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA512_WITH_ECDSA)
                .addSubjectAlternativeName(san1)
                .addSubjectAlternativeName(san2)
                .build();
        assertEquals(Arrays.asList(san1, san2), csr.getSubjectAlternativeNames());
    }

    @Test
    public void can_read_basic_constraints() {
        X500Principal subject = new X500Principal("CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA512_WITH_ECDSA)
                .setBasicConstraints(true, true)
                .build();
        assertTrue(csr.getBasicConstraints().isPresent());
        assertTrue(csr.getBasicConstraints().get());
    }

    @Test
    public void can_read_extensions() {
        X500Principal subject = new X500Principal("CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA512_WITH_ECDSA)
                .addSubjectAlternativeName("san")
                .setBasicConstraints(true, true)
                .build();
        List<String> expected = Arrays.asList(Extension.BASIC_CONSTRAINTS.getOId(), Extension.SUBJECT_ALTERNATIVE_NAMES.getOId());
        List<String> actual = csr.getExtensionOIds();
        assertEquals(expected, actual);
    }

}