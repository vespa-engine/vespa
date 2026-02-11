package org.logstashplugins;

import org.junit.Test;
import static org.junit.Assert.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.security.cert.CertificateFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Comparator;

public class SelfSignedCertGeneratorTest {

    @Test
    public void testFileGenerationAndFormat() throws Exception {
        // Create temporary files
        Path tempDir = Files.createTempDirectory("vespa-certs");
        String privateKeyPath = tempDir.resolve("private-key.pem").toString();
        String certificatePath = tempDir.resolve("certificate.pem").toString();

        try {
            // Generate the certificate and key
            SelfSignedCertGenerator.generate(
                "test.vespa.example",  // subject CN
                365,                   // validity days
                privateKeyPath,        // private key path
                certificatePath        // certificate path
            );

            // Verify files exist
            assertTrue("Private key file should exist", Files.exists(Paths.get(privateKeyPath)));
            assertTrue("Certificate file should exist", Files.exists(Paths.get(certificatePath)));

            // Verify private key file content
            String privateKeyContent = Files.readString(Paths.get(privateKeyPath));
            assertTrue("Private key should be in PEM format", 
                      privateKeyContent.contains("-----BEGIN PRIVATE KEY-----"));
            assertTrue("Private key should be in PEM format", 
                      privateKeyContent.contains("-----END PRIVATE KEY-----"));

            // Verify certificate file content
            String certificateContent = Files.readString(Paths.get(certificatePath));
            assertTrue("Certificate should be in PEM format", 
                      certificateContent.contains("-----BEGIN CERTIFICATE-----"));
            assertTrue("Certificate should be in PEM format", 
                      certificateContent.contains("-----END CERTIFICATE-----"));

            // Verify certificate can be loaded
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            try (FileInputStream fis = new FileInputStream(certificatePath)) {
                X509Certificate cert = (X509Certificate) cf.generateCertificate(fis);
                assertEquals("Certificate subject should match", 
                           "CN=test.vespa.example", cert.getSubjectX500Principal().getName());
            }

        } finally {
            // Clean up
            Files.deleteIfExists(Paths.get(privateKeyPath));
            Files.deleteIfExists(Paths.get(certificatePath));
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testKeyPairPropertiesAndSigning() throws Exception {
        // Create temporary files
        Path tempDir = Files.createTempDirectory("vespa-certs");
        String privateKeyPath = tempDir.resolve("private-key.pem").toString();
        String certificatePath = tempDir.resolve("certificate.pem").toString();

        try {
            // Generate the certificate and key
            SelfSignedCertGenerator.generate(
                "test.vespa.example",  // subject CN
                365,                   // validity days
                privateKeyPath,        // private key path
                certificatePath        // certificate path
            );

            // Load the private key and certificate
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert;
            try (FileInputStream fis = new FileInputStream(certificatePath)) {
                cert = (X509Certificate) cf.generateCertificate(fis);
            }

            // Load the private key
            byte[] privateKeyBytes = Files.readAllBytes(Paths.get(privateKeyPath));
            String privateKeyPem = new String(privateKeyBytes);
            privateKeyPem = privateKeyPem.replace("-----BEGIN PRIVATE KEY-----", "")
                                       .replace("-----END PRIVATE KEY-----", "")
                                       .replaceAll("\\s", "");
            byte[] decoded = java.util.Base64.getDecoder().decode(privateKeyPem);
            java.security.spec.PKCS8EncodedKeySpec keySpec = new java.security.spec.PKCS8EncodedKeySpec(decoded);
            java.security.KeyFactory kf = java.security.KeyFactory.getInstance("RSA");
            java.security.PrivateKey privateKey = kf.generatePrivate(keySpec);

            // Verify key size is 4096 bits
            java.security.interfaces.RSAPublicKey rsaPublicKey = (java.security.interfaces.RSAPublicKey) cert.getPublicKey();
            assertEquals("Key size should be 4096 bits", 
                       4096, rsaPublicKey.getModulus().bitLength());

            // Test signing capability
            String testMessage = "Test message for signing";
            byte[] messageBytes = testMessage.getBytes();

            // Sign the message
            java.security.Signature signature = java.security.Signature.getInstance("SHA256withRSA");
            signature.initSign(privateKey);
            signature.update(messageBytes);
            byte[] signatureBytes = signature.sign();

            // Verify the signature
            signature.initVerify(cert.getPublicKey());
            signature.update(messageBytes);
            assertTrue("Signature should be valid", signature.verify(signatureBytes));

        } finally {
            // Clean up
            Files.deleteIfExists(Paths.get(privateKeyPath));
            Files.deleteIfExists(Paths.get(certificatePath));
            Files.deleteIfExists(tempDir);
        }
    }

    @Test
    public void testCertificatePropertiesAndValidity() throws Exception {
        // Create temporary files
        Path tempDir = Files.createTempDirectory("vespa-certs");
        String privateKeyPath = tempDir.resolve("private-key.pem").toString();
        String certificatePath = tempDir.resolve("certificate.pem").toString();

        try {
            // Generate with specific validity period
            int validityDays = 365;
            long beforeGeneration = System.currentTimeMillis();
            
            SelfSignedCertGenerator.generate(
                "test.vespa.example",  // subject CN
                validityDays,          // validity days
                privateKeyPath,        // private key path
                certificatePath        // certificate path
            );
            
            long afterGeneration = System.currentTimeMillis();

            // Load the certificate
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            X509Certificate cert;
            try (FileInputStream fis = new FileInputStream(certificatePath)) {
                cert = (X509Certificate) cf.generateCertificate(fis);
            }

            // Verify subject and issuer are the same (self-signed)
            assertEquals("Certificate should be self-signed",
                       cert.getSubjectX500Principal(),
                       cert.getIssuerX500Principal());

            // Verify subject DN
            assertEquals("Subject DN should match",
                       "CN=test.vespa.example",
                       cert.getSubjectX500Principal().getName());

            // Verify validity period
            assertTrue("Certificate not valid before generation",
                     cert.getNotBefore().getTime() <= afterGeneration);
            
            // Add a small tolerance (1 minute) to avoid flaky test failures due to timing differences
            long toleranceMs = 1000*60;
            assertTrue("Certificate should be valid at generation (with tolerance)",
                     cert.getNotBefore().getTime() >= beforeGeneration - toleranceMs);

            // Verify expiration date (with 1 minute tolerance for test execution time)
            long expectedExpiration = beforeGeneration + (validityDays * 24L * 60L * 60L * 1000L);
            long actualExpiration = cert.getNotAfter().getTime();
            assertTrue("Certificate expiration should be approximately validityDays in the future",
                     Math.abs(expectedExpiration - actualExpiration) < toleranceMs);

            // Verify signature algorithm
            assertEquals("Certificate should use SHA256withRSA",
                       "SHA256withRSA",
                       cert.getSigAlgName());

            // Verify the certificate is valid now
            cert.checkValidity();

        } finally {
            // Clean up
            Files.deleteIfExists(Paths.get(privateKeyPath));
            Files.deleteIfExists(Paths.get(certificatePath));
            Files.deleteIfExists(tempDir);
        }
    }

    @Test(expected = IOException.class)
    public void testNonExistentDirectory() throws Exception {
        Path nonExistentDir = Paths.get("/non/existent/directory");
        Path keyPath = nonExistentDir.resolve("test.key");
        Path certPath = nonExistentDir.resolve("test.crt");
        SelfSignedCertGenerator.generate("test", 365, keyPath.toString(), certPath.toString());
    }

    @Test
    public void testOverwriteExistingFiles() throws Exception {
        Path tempDir = Files.createTempDirectory("vespa-certs");
        try {
            Path keyPath = tempDir.resolve("existing.key");
            Path certPath = tempDir.resolve("existing.crt");
            
            // Create files first
            Files.createFile(keyPath);
            Files.createFile(certPath);
            
            // Should not throw when overwriting
            SelfSignedCertGenerator.generate("test", 365, keyPath.toString(), certPath.toString());
            
            // Verify files were overwritten with valid content
            assertTrue(Files.exists(keyPath));
            assertTrue(Files.exists(certPath));
            assertTrue(Files.size(keyPath) > 0);
            assertTrue(Files.size(certPath) > 0);
        } finally {
            Files.walk(tempDir)
                 .sorted(Comparator.reverseOrder())
                 .forEach(path -> {
                     try {
                         Files.deleteIfExists(path);
                     } catch (IOException e) {
                         // Ignore cleanup errors
                     }
                 });
        }
    }
} 