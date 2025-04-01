package org.logstashplugins;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Security;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.io.IOException;

/**
 * Utility class for generating an RSA keypair (4096 bits) and a self-signed X.509 certificate,
 * then writing both to PEM files (private key + certificate).
 */
public final class SelfSignedCertGenerator {

    private SelfSignedCertGenerator() {
        // Prevent instantiation
    }

    /**
     * Generates a 4096-bit RSA key pair and a self-signed X.509 certificate (valid for 'validityDays' days),
     * with subject (and issuer) of "CN={subjectCn}".
     * 
     * Writes the private key to {@code privateKeyPath} and the certificate to {@code certificatePath},
     * both in PEM format.
     *
     * @param subjectCn        The common name (CN) in the certificate (e.g. "cloud.vespa.example")
     * @param validityDays     How many days until this certificate expires
     * @param privateKeyPath   Destination file path for the private key PEM
     * @param certificatePath  Destination file path for the public certificate PEM
     * @throws Exception if anything goes wrong (IO, crypto errors, etc.)
     */
    public static void generate(String subjectCn,
                                int validityDays,
                                String privateKeyPath,
                                String certificatePath) 
            throws Exception {

        // Ensure Bouncy Castle is registered as a security provider
        Security.addProvider(new BouncyCastleProvider());

        // 1) Generate an RSA 4096-bit key pair
        KeyPair keyPair = generateRsaKeyPair(4096);

        // 2) Generate a self-signed X.509 certificate
        X509Certificate certificate = generateSelfSignedCertificate(
                keyPair, subjectCn, validityDays);

        // 3) Write the private key (PEM) and the certificate (PEM) to files
        writePemFile(keyPair.getPrivate(), "PRIVATE KEY", privateKeyPath);
        writePemFile(certificate.getEncoded(), "CERTIFICATE", certificatePath);
    }

    /** Helper method to generate RSA key pair using Bouncy Castle. */
    private static KeyPair generateRsaKeyPair(int keySize) throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA", "BC");
        kpg.initialize(keySize);
        return kpg.generateKeyPair();
    }

    /** 
     * Creates a self-signed certificate, valid for 'validityDays' days,
     * with Subject and Issuer = "CN=<subjectCn>". 
     */
    private static X509Certificate generateSelfSignedCertificate(KeyPair keyPair,
                                                                 String subjectCn,
                                                                 int validityDays) 
            throws OperatorCreationException, CertificateException {

        // Valid from now until now + validityDays
        long now = System.currentTimeMillis();
        Date notBefore = new Date(now);
        Date notAfter  = new Date(now + validityDays * 24L * 60L * 60L * 1000L);

        // Use current time (millis) as the serial number
        BigInteger serialNumber = BigInteger.valueOf(now);

        X500Name dnName = new X500Name("CN=" + subjectCn);

        // Build the certificate (Subject = Issuer = dnName)
        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                dnName,                  // issuer
                serialNumber,            // serial
                notBefore,               // valid from
                notAfter,                // valid to
                dnName,                  // subject
                keyPair.getPublic()      // public key
        );

        // Sign with private key (SHA256withRSA)
        ContentSigner signer = new JcaContentSignerBuilder("SHA256withRSA")
                .setProvider("BC")
                .build(keyPair.getPrivate());

        // Convert to a standard Java X509Certificate
        return new JcaX509CertificateConverter()
                .setProvider("BC")
                .getCertificate(certBuilder.build(signer));
    }

    /**
     * Writes the given byte[] (e.g. DER-encoded certificate) as a PEM file with the specified label.
     */
    private static void writePemFile(byte[] content, String label, String outputPath) 
            throws IOException {
        String pem = toPem(content, label);
        Files.write(Path.of(outputPath), pem.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Overload for PrivateKey objects; calls getEncoded() and writes to PEM.
     */
    private static void writePemFile(PrivateKey key, String label, String outputPath) 
            throws IOException {
        writePemFile(key.getEncoded(), label, outputPath);
    }

    /**
     * Convert raw DER bytes into a PEM-format string, with BEGIN/END lines.
     */
    private static String toPem(byte[] binary, String label) {
        String base64 = Base64.getMimeEncoder(64, "\n".getBytes())
                              .encodeToString(binary);
        return "-----BEGIN " + label + "-----\n"
             + base64
             + "\n-----END " + label + "-----\n";
    }
}
