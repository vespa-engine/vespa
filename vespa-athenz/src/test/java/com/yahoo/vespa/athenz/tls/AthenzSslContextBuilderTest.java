package com.yahoo.vespa.athenz.tls;

import com.yahoo.athenz.auth.util.Crypto;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.junit.Test;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;

/**
 * @author bjorncs
 */
public class AthenzSslContextBuilderTest {

    private static final char[] PASSWORD = new char[0];

    @Test
    public void can_build_sslcontext_with_truststore_only() throws Exception {
        new AthenzSslContextBuilder()
                .withTrustStore(createKeystore())
                .build();
    }

    @Test
    public void can_build_sslcontext_with_keystore_only() throws Exception {
        new AthenzSslContextBuilder()
                .withKeyStore(createKeystore(), PASSWORD)
                .build();
    }

    @Test
    public void can_build_sslcontext_with_truststore_and_keystore() throws Exception {
        new AthenzSslContextBuilder()
                .withKeyStore(createKeystore(), PASSWORD)
                .withTrustStore(createKeystore())
                .build();
    }

    private static KeyStore createKeystore() throws Exception {
        KeyPair keyPair = createKeyPair();
        KeyStore keystore = KeyStore.getInstance("JKS");
        keystore.load(null);
        keystore.setKeyEntry("entry-name", keyPair.getPrivate(), PASSWORD, new Certificate[]{createCertificate(keyPair)});
        return keystore;
    }

    private static X509Certificate createCertificate(KeyPair keyPair) throws
            OperatorCreationException, IOException {
        String x500Principal = "CN=mysubject";
        PKCS10CertificationRequest csr =
                Crypto.getPKCS10CertRequest(
                        Crypto.generateX509CSR(keyPair.getPrivate(), x500Principal, null));
        return Crypto.generateX509Certificate(csr, keyPair.getPrivate(), new X500Name(x500Principal), 3600, false);
    }

    private static KeyPair createKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(512);
        return keyGen.genKeyPair();
    }
}