package com.yahoo.vespa.athenz.tls;

import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

/**
 * @author bjorncs
 */
public class Pkcs10CsrUtilsTest {

    @Test
    public void can_deserialize_serialized_pem_csr() {
        X500Principal subject = new X500Principal("CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA256_WITH_RSA).build();
        String pem = Pkcs10CsrUtils.toPem(csr);
        Pkcs10Csr deserializedCsr = Pkcs10CsrUtils.fromPem(pem);
        assertThat(pem, containsString("BEGIN CERTIFICATE REQUEST"));
        assertThat(pem, containsString("END CERTIFICATE REQUEST"));
        assertEquals(subject, deserializedCsr.getSubject());
    }

}