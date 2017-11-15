package com.yahoo.vespa.hosted.node.certificate;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.bouncycastle.pkcs.jcajce.JcaPKCS10CertificationRequestBuilder;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Clock;

/**
 * Refreshes certificate that is used by nodes to authenticate themselves to the config server
 *
 * @author freva
 */
class CertificateRefresher {

    static final String SIGNER_ALGORITHM = "SHA256withRSA";
    private static final String KEY_STORE_INSTANCE = "PKCS12";
    private static final String KEY_STORE_ALIAS = "alias";
    private static final char[] KEY_STORE_PASSWORD = new char[0];

    private final CertificateAuthorityClient caClient;
    private final Clock clock;

    CertificateRefresher(CertificateAuthorityClient caClient, Clock clock) {
        this.caClient = caClient;
        this.clock = clock;
    }

    CertificateRefresher(CertificateAuthorityClient caClient) {
        this(caClient, Clock.systemUTC());
    }

    void refreshCertificate(Path pathToKeyStore, String commonName)
            throws CertificateException, NoSuchAlgorithmException, KeyStoreException, IOException, OperatorCreationException {
        if (! shouldRefreshCertificate(pathToKeyStore)) return;

        KeyPair keyPair = generateKeyPair();
        PKCS10CertificationRequest csr = generateCsr(keyPair, commonName);
        X509Certificate certificate = caClient.signCsr(csr);

        storeCertificate(pathToKeyStore, keyPair, certificate);
    }

    private void storeCertificate(Path pathToKeyStore, KeyPair keyPair, X509Certificate certificate)
            throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        pathToKeyStore.getParent().toFile().mkdirs();
        X509Certificate[] certificateChain = {certificate};

        try (FileOutputStream fos = new FileOutputStream(pathToKeyStore.toFile())) {
            KeyStore keyStore = KeyStore.getInstance(KEY_STORE_INSTANCE);
            keyStore.load(null, null);
            keyStore.setKeyEntry(KEY_STORE_ALIAS, keyPair.getPrivate(), KEY_STORE_PASSWORD, certificateChain);
            keyStore.store(fos, KEY_STORE_PASSWORD);
            fos.flush();
        }
    }

    private boolean shouldRefreshCertificate(Path pathToKeyStore)
            throws IOException, KeyStoreException, CertificateException, NoSuchAlgorithmException {
        try {
            X509Certificate cert = readCertificate(pathToKeyStore);
            long notBefore = cert.getNotBefore().getTime();
            long notAfter = cert.getNotAfter().getTime();
            long thirdOfLifetime = (notAfter - notBefore) / 3;

            return notBefore + thirdOfLifetime < clock.millis();
        } catch (FileNotFoundException e) {
            return true;
        }
    }

    static X509Certificate readCertificate(Path pathToKeyStore)
            throws CertificateException, NoSuchAlgorithmException, IOException, KeyStoreException {
        try (FileInputStream fis = new FileInputStream(pathToKeyStore.toFile())) {
            KeyStore keyStore = KeyStore.getInstance(KEY_STORE_INSTANCE);
            keyStore.load(fis, KEY_STORE_PASSWORD);

            return (X509Certificate) keyStore.getCertificate(KEY_STORE_ALIAS);
        }
    }

    static KeyPair generateKeyPair() throws NoSuchAlgorithmException {
        KeyPairGenerator rsa = KeyPairGenerator.getInstance("RSA");
        rsa.initialize(2048);
        return rsa.genKeyPair();
    }

    private static PKCS10CertificationRequest generateCsr(KeyPair keyPair, String commonName)
            throws NoSuchAlgorithmException, OperatorCreationException {
        ContentSigner signer = new JcaContentSignerBuilder(SIGNER_ALGORITHM).build(keyPair.getPrivate());

        return new JcaPKCS10CertificationRequestBuilder(new X500Name("CN=" + commonName), keyPair.getPublic())
                .build(signer);
    }
}
