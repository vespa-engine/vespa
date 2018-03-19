package com.yahoo.vespa.athenz.tls;

import org.junit.Test;

import javax.security.auth.x500.X500Principal;
import java.security.KeyPair;

import static org.junit.Assert.*;

/**
 * @author bjorncs
 */
public class Pkcs10CsrUtilsTest {

    @Test
    public void can_deserialize_serialized_pem_csr() {
        X500Principal subject = new X500Principal("CN=subject");
        KeyPair keypair = KeyUtils.generateKeypair(KeyAlgorithm.RSA, 2048);
        Pkcs10Csr csr = Pkcs10CsrBuilder.fromKeypair(subject, keypair, SignatureAlgorithm.SHA256_WITH_RSA).build();
        Pkcs10Csr deserializedCsr = Pkcs10CsrUtils.fromPem(Pkcs10CsrUtils.toPem(csr));
        assertEquals(subject, deserializedCsr.getSubject());
    }

}