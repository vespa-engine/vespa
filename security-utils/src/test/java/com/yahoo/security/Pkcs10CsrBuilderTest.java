// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.asn1.x500.X500Name;
import org.junit.jupiter.api.Test;

import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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

    @Test
    void can_build_csr_with_common_name_exceeding_rfc5280_upper_bound() {
        // Athenz role certificates use '<domain>:role.<role>' as common name, which regularly exceeds 64 characters
        String commonName = "vespa.vespa:role.hosting.tenant.my-tenant.my-application.res_group.my-instance.reader";
        X500Principal subject = new X500Principal("CN=" + commonName);
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA512_WITH_ECDSA).build();
        assertEquals(subject, csr.getSubject());
    }

    @Test
    void encodes_subject_within_upper_bound_as_default_style_does() throws IOException {
        X500Principal subject = new X500Principal("OU=organizational-unit, CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA512_WITH_ECDSA).build();
        assertArrayEquals(new X500Name(subject.getName()).getEncoded("DER"),
                          csr.getBcCsr().getSubject().getEncoded("DER"));
    }

}
