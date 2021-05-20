// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.feed.client;

import org.bouncycastle.asn1.ASN1ObjectIdentifier;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

/**
 * BouncyCastle integration for creating a {@link SSLContext} instance from PEM encoded material
 *
 * @author bjorncs
 */
class SslContextBuilder {

    private static final BouncyCastleProvider bcProvider = new BouncyCastleProvider();

    private Path certificateFile;
    private Path privateKeyFile;
    private Path caCertificatesFile;

    SslContextBuilder withCertificateAndKey(Path certificate, Path privateKey) {
        this.certificateFile = certificate;
        this.privateKeyFile = privateKey;
        return this;
    }

    SslContextBuilder withCaCertificates(Path caCertificates) {
        this.caCertificatesFile = caCertificates;
        return this;
    }

    SSLContext build() throws IOException {
        try {
            KeyStore keystore = KeyStore.getInstance("PKCS12");
            if (certificateFile != null && privateKeyFile != null) {
                keystore.setKeyEntry("cert", privateKey(privateKeyFile), new char[0], certificates(certificateFile));
            }
            if (caCertificatesFile != null) {
                keystore.setCertificateEntry("ca-cert", certificates(caCertificatesFile)[0]);
            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keystore, new char[0]);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(keystore);
            SSLContext sslContext = SSLContext.getDefault();
            sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return sslContext;
        } catch (GeneralSecurityException e) {
            throw new IOException(e);
        }
    }

    private static Certificate[] certificates(Path file) throws IOException, GeneralSecurityException {
        try (PEMParser parser = new PEMParser(Files.newBufferedReader(file))) {
            List<X509Certificate> result = new ArrayList<>();
            Object pemObject;
            while ((pemObject = parser.readObject()) != null) {
                result.add(toX509Certificate(pemObject));
            }
            if (result.isEmpty()) throw new IOException("File contains no PEM encoded certificates: " + file);
            return result.toArray(new Certificate[0]);
        }
    }

    private static PrivateKey privateKey(Path file) throws IOException, GeneralSecurityException {
        try (PEMParser parser = new PEMParser(Files.newBufferedReader(file))) {
            Object pemObject;
            while ((pemObject = parser.readObject()) != null) {
                if (pemObject instanceof PrivateKeyInfo) {
                    PrivateKeyInfo keyInfo = (PrivateKeyInfo) pemObject;
                    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyInfo.getEncoded());
                    return createKeyFactory(keyInfo).generatePrivate(keySpec);
                } else if (pemObject instanceof PEMKeyPair) {
                    PEMKeyPair pemKeypair = (PEMKeyPair) pemObject;
                    PrivateKeyInfo keyInfo = pemKeypair.getPrivateKeyInfo();
                    return createKeyFactory(keyInfo).generatePrivate(new PKCS8EncodedKeySpec(keyInfo.getEncoded()));
                }
            }
            throw new IOException("Could not find private key in PEM file");
        }
    }

    private static X509Certificate toX509Certificate(Object pemObject) throws IOException, GeneralSecurityException {
        if (pemObject instanceof X509Certificate) return (X509Certificate) pemObject;
        if (pemObject instanceof X509CertificateHolder) {
            return new JcaX509CertificateConverter()
                    .setProvider(bcProvider)
                    .getCertificate((X509CertificateHolder) pemObject);
        }
        throw new IOException("Invalid type of PEM object: " + pemObject);
    }

    private static KeyFactory createKeyFactory(PrivateKeyInfo info) throws IOException, GeneralSecurityException {
        ASN1ObjectIdentifier algorithm = info.getPrivateKeyAlgorithm().getAlgorithm();
        if (X9ObjectIdentifiers.id_ecPublicKey.equals(algorithm)) {
            return KeyFactory.getInstance("EC", bcProvider);
        } else if (PKCSObjectIdentifiers.rsaEncryption.equals(algorithm)) {
            return KeyFactory.getInstance("RSA", bcProvider);
        } else {
            throw new IOException("Unknown key algorithm: " + algorithm);
        }
    }

}
