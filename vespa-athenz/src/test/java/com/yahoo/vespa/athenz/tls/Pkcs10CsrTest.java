package com.yahoo.vespa.athenz.tls;

import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author bjorncs
 */
public class Pkcs10CsrTest {

    @Test
    public void can_read_subject_alternative_names() {
        X500Principal subject = new X500Principal("CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        String san1 = "san1.com";
        String san2 = "san2.com";
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA256_WITH_RSA)
                .addSubjectAlternativeName(san1)
                .addSubjectAlternativeName(san2)
                .build();
        assertEquals(Arrays.asList(san1, san2), csr.getSubjectAlternativeNames());
    }

    @Test
    public void can_read_basic_constraints() {
        X500Principal subject = new X500Principal("CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA256_WITH_RSA)
                .setBasicConstraints(true, true)
                .build();
        assertTrue(csr.getBasicConstraints());
    }

    @Test
    public void can_read_extensions() {
        X500Principal subject = new X500Principal("CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA256_WITH_RSA)
                .addSubjectAlternativeName("san")
                .setBasicConstraints(true, true)
                .build();
        List<String> expected = Arrays.asList(Extension.BASIC_CONSTRAINS.getOId(), Extension.SUBJECT_ALTERNATIVE_NAMES.getOId());
        List<String> actual = csr.getExtensionOIds();
        assertEquals(expected, actual);
    }

}