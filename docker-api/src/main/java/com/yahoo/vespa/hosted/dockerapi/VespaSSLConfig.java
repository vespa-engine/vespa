// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.dockerapi;

import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.core.SSLConfig;
import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.glassfish.jersey.SslConfigurator;

import javax.net.ssl.SSLContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import static java.util.Objects.requireNonNull;


/**
 * This class is based off {@link com.github.dockerjava.core.LocalDirectorySSLConfig}, but with the ability to
 * specify path to each of the certificates instead of directory path. Additionally it includes
 * {@link com.github.dockerjava.core.util.CertificateUtils} because of version conflict of with
 * com.google.code.findbugs.annotations
 */
public class VespaSSLConfig implements SSLConfig {
    private final DockerConfig config;

    public VespaSSLConfig(DockerConfig config) {
        this.config = config;
    }

    @Override
    public SSLContext getSSLContext() {
        try {
            Security.addProvider(new BouncyCastleProvider());

            // properties acrobatics not needed for java > 1.6
            String httpProtocols = System.getProperty("https.protocols");
            System.setProperty("https.protocols", "TLSv1");
            SslConfigurator sslConfig = SslConfigurator.newInstance(true);
            if (httpProtocols != null) {
                System.setProperty("https.protocols", httpProtocols);
            }

            String keypem = new String(Files.readAllBytes(Paths.get(config.clientKeyPath())));
            String certpem = new String(Files.readAllBytes(Paths.get(config.clientCertPath())));
            String capem = new String(Files.readAllBytes(Paths.get(config.caCertPath())));

            sslConfig.keyStore(createKeyStore(keypem, certpem));
            sslConfig.keyStorePassword("docker");
            sslConfig.trustStore(createTrustStore(capem));

            return sslConfig.createSSLContext();
        } catch (Exception e) {
            throw new DockerClientException(e.getMessage(), e);
        }
    }

    public static KeyStore createKeyStore(final String keypem, final String certpem) throws NoSuchAlgorithmException,
            IOException, CertificateException, KeyStoreException {
        PrivateKey privateKey = loadPrivateKey(keypem);
        requireNonNull(privateKey);
        List<Certificate> privateCertificates = loadCertificates(certpem);

        KeyStore keyStore = KeyStore.getInstance("JKS");
        keyStore.load(null);

        keyStore.setKeyEntry("docker",
                privateKey,
                "docker".toCharArray(),
                privateCertificates.toArray(new Certificate[privateCertificates.size()])
        );

        return keyStore;
    }

    /**
     * from "cert.pem" String
     */
    private static List<Certificate> loadCertificates(final String certpem) throws IOException,
            CertificateException {
        final StringReader certReader = new StringReader(certpem);
        try (BufferedReader reader = new BufferedReader(certReader)) {
            return loadCertificates(reader);
        }
    }

    /**
     * "cert.pem" from reader
     */
    private static List<Certificate> loadCertificates(final Reader reader) throws IOException,
            CertificateException {
        try (PEMParser pemParser = new PEMParser(reader)) {
            List<Certificate> certificates = new ArrayList<>();

            JcaX509CertificateConverter certificateConverter = new JcaX509CertificateConverter().setProvider("BC");
            Object certObj = pemParser.readObject();

            if (certObj instanceof X509CertificateHolder) {
                X509CertificateHolder certificateHolder = (X509CertificateHolder) certObj;
                certificates.add(certificateConverter.getCertificate(certificateHolder));
            }

            return certificates;
        }
    }


    /**
     * Return private key ("key.pem") from Reader
     */
    private static PrivateKey loadPrivateKey(final Reader reader) throws IOException, NoSuchAlgorithmException {
        try (PEMParser pemParser = new PEMParser(reader)) {
            Object readObject = pemParser.readObject();
            while (readObject != null) {
                if (readObject instanceof PEMKeyPair) {
                    PEMKeyPair pemKeyPair = (PEMKeyPair) readObject;
                    PrivateKey privateKey = guessKey(pemKeyPair.getPrivateKeyInfo().getEncoded());
                    if (privateKey != null) {
                        return privateKey;
                    }
                } else if (readObject instanceof PrivateKeyInfo) {
                    PrivateKeyInfo privateKeyInfo = (PrivateKeyInfo) readObject;
                    PrivateKey privateKey = guessKey(privateKeyInfo.getEncoded());
                    if (privateKey != null) {
                        return privateKey;
                    }
                } else if (readObject instanceof ASN1ObjectIdentifier) {
                    // no idea how it can be used
                    final ASN1ObjectIdentifier asn1ObjectIdentifier = (ASN1ObjectIdentifier) readObject;
                }

                readObject = pemParser.readObject();
            }
        }

        return null;
    }

    private static PrivateKey guessKey(byte[] encodedKey) throws NoSuchAlgorithmException {
        //no way to know, so iterate
        for (String guessFactory : new String[]{"RSA", "ECDSA"}) {
            try {
                KeyFactory factory = KeyFactory.getInstance(guessFactory);

                PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(encodedKey);
                return factory.generatePrivate(privateKeySpec);
            } catch (InvalidKeySpecException ignore) {
            }
        }

        return null;
    }

    /**
     * Return KeyPair from "key.pem"
     */
    private static PrivateKey loadPrivateKey(final String keypem) throws IOException, NoSuchAlgorithmException {
        try (StringReader certReader = new StringReader(keypem);
             BufferedReader reader = new BufferedReader(certReader)) {
            return loadPrivateKey(reader);
        }
    }

    /**
     * "ca.pem" from String
     */
    public static KeyStore createTrustStore(String capem) throws IOException, CertificateException,
            KeyStoreException, NoSuchAlgorithmException {
        try (Reader certReader = new StringReader(capem)) {
            return createTrustStore(certReader);
        }
    }

    /**
     * "ca.pem" from Reader
     */
    public static KeyStore createTrustStore(final Reader certReader) throws IOException, CertificateException,
            KeyStoreException, NoSuchAlgorithmException {
        try (PEMParser pemParser = new PEMParser(certReader)) {
            X509CertificateHolder certificateHolder = (X509CertificateHolder) pemParser.readObject();
            Certificate caCertificate = new JcaX509CertificateConverter()
                    .setProvider("BC")
                    .getCertificate(certificateHolder);

            KeyStore trustStore = KeyStore.getInstance("JKS");
            trustStore.load(null);
            trustStore.setCertificateEntry("ca", caCertificate);

            return trustStore;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        VespaSSLConfig that = (VespaSSLConfig) o;

        return config.equals(that.config);

    }

    @Override
    public int hashCode() {
        return config.hashCode();
    }
}
