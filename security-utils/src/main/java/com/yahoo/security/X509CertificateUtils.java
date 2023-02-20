// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

import javax.naming.NamingException;
import javax.naming.ldap.LdapName;
import javax.security.auth.x500.X500Principal;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.SignatureException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.yahoo.security.Extension.SUBJECT_ALTERNATIVE_NAMES;

/**
 * @author bjorncs
 */
public class X509CertificateUtils {

    private X509CertificateUtils() {}

    public static X509Certificate fromPem(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            return toX509Certificate(parser.readObject());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    public static List<X509Certificate> certificateListFromPem(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            List<X509Certificate> list = new ArrayList<>();
            Object pemObject;
            while ((pemObject = parser.readObject()) != null) {
                list.add(toX509Certificate(pemObject));
            }
            return list;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (CertificateException e) {
            throw new RuntimeException(e);
        }
    }

    private static X509Certificate toX509Certificate(Object pemObject) throws CertificateException {
        if (pemObject instanceof X509Certificate) {
            return (X509Certificate) pemObject;
        }
        if (pemObject instanceof X509CertificateHolder) {
            return new JcaX509CertificateConverter()
                    .setProvider(BouncyCastleProviderHolder.getInstance())
                    .getCertificate((X509CertificateHolder) pemObject);
        }
        throw new IllegalArgumentException("Invalid type of PEM object: " + pemObject);
    }

    public static String toPem(X509Certificate certificate) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject("CERTIFICATE", certificate.getEncoded()));
            pemWriter.flush();
            return stringWriter.toString();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static String toPem(List<X509Certificate> certificates) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            for (X509Certificate certificate : certificates) {
                pemWriter.writeObject(new PemObject("CERTIFICATE", certificate.getEncoded()));
            }
            pemWriter.flush();
            return stringWriter.toString();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static List<String> getSubjectCommonNames(X509Certificate certificate) {
        return getCommonNames(certificate.getSubjectX500Principal());
    }

    public static Optional<String> getSubjectCommonName(X509Certificate c) {
        List<String> names = getSubjectCommonNames(c);
        if (names.isEmpty()) return Optional.empty();
        return Optional.of(names.get(names.size() - 1));
    }

    public static List<String> getIssuerCommonNames(X509Certificate certificate) {
        return getCommonNames(certificate.getIssuerX500Principal());
    }

    public static List<String> getSubjectOrganizationalUnits(X509Certificate certificate) {
        return getRdns(certificate.getSubjectX500Principal(), "OU");
    }

    public static List<String> getCommonNames(X500Principal distinguishedName) {
        return getRdns(distinguishedName, "CN");
    }

    private static List<String> getRdns(X500Principal distinguishedName, String rdnName) {
        try {
            return new LdapName(distinguishedName.getName()).getRdns().stream()
                    .filter(rdn -> rdn.getType().equalsIgnoreCase(rdnName))
                    .map(rdn -> rdn.getValue().toString())
                    .toList();
        } catch (NamingException e) {
            throw new IllegalArgumentException("Invalid DN: " + distinguishedName.getName(), e);
        }
    }

    public static List<SubjectAlternativeName> getSubjectAlternativeNames(X509Certificate certificate) {
        try {
            byte[] extensionValue = certificate.getExtensionValue(SUBJECT_ALTERNATIVE_NAMES.getOId());
            if (extensionValue == null) return Collections.emptyList();
            ASN1Encodable asn1Encodable = ASN1Primitive.fromByteArray(extensionValue);
            if (asn1Encodable instanceof ASN1OctetString) {
                asn1Encodable = ASN1Primitive.fromByteArray(((ASN1OctetString) asn1Encodable).getOctets());
            }
            GeneralNames names = GeneralNames.getInstance(asn1Encodable);
            return SubjectAlternativeName.fromGeneralNames(names);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public static boolean privateKeyMatchesPublicKey(PrivateKey privateKey, PublicKey publicKey) {
        byte[] someRandomData = new byte[64];
        new Random().nextBytes(someRandomData);

        Signature signer = SignatureUtils.createSigner(privateKey);
        Signature verifier = SignatureUtils.createVerifier(publicKey);
        try {
            signer.update(someRandomData);
            verifier.update(someRandomData);
            byte[] signature = signer.sign();
            return verifier.verify(signature);
        } catch (SignatureException e) {
            throw new RuntimeException(e);
        }
    }

    public static X509CertificateWithKey createSelfSigned(String cn, Duration duration) {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC, 256);
        X500Principal subject = new X500Principal(cn);
        Instant now = Instant.now();
        X509Certificate cert =
                X509CertificateBuilder.fromKeypair(keyPair, subject, now,
                                                   now.plus(duration), SignatureAlgorithm.SHA256_WITH_ECDSA,
                                                   BigInteger.ONE)
                        .setBasicConstraints(true, true)
                        .build();
        return new X509CertificateWithKey(cert, keyPair.getPrivate());
    }

    /**
     * @return certificate SHA-1 fingerprint
     */
    public static byte[] getX509CertificateFingerPrint(X509Certificate certificate) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            return sha1.digest(certificate.getEncoded());
        } catch (CertificateEncodingException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }
}
