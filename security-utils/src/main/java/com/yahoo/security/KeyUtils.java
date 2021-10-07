// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.io.pem.PemObject;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.List;

import static com.yahoo.security.KeyAlgorithm.EC;
import static com.yahoo.security.KeyAlgorithm.RSA;

/**
 * @author bjorncs
 */
public class KeyUtils {

    private KeyUtils() {}

    public static KeyPair generateKeypair(KeyAlgorithm algorithm, int keySize) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm.getAlgorithmName(), BouncyCastleProviderHolder.getInstance());
            if (keySize != -1) {
                keyGen.initialize(keySize);
            }
            if (algorithm.getSpec().isPresent()) {
                keyGen.initialize(algorithm.getSpec().get());
            }
            return keyGen.genKeyPair();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyPair generateKeypair(KeyAlgorithm algorithm) {
        return generateKeypair(algorithm, -1);
    }

    public static PublicKey extractPublicKey(PrivateKey privateKey) {
        String algorithm = privateKey.getAlgorithm();
        try {
            if (algorithm.equals(RSA.getAlgorithmName())) {
                KeyFactory keyFactory = createKeyFactory(RSA);
                RSAPrivateCrtKey rsaPrivateCrtKey = (RSAPrivateCrtKey) privateKey;
                RSAPublicKeySpec keySpec = new RSAPublicKeySpec(rsaPrivateCrtKey.getModulus(), rsaPrivateCrtKey.getPublicExponent());
                return keyFactory.generatePublic(keySpec);
            } else if (algorithm.equals(EC.getAlgorithmName())) {
                KeyFactory keyFactory = createKeyFactory(EC);
                BCECPrivateKey ecPrivateKey = (BCECPrivateKey) privateKey;
                ECParameterSpec ecParameterSpec = ecPrivateKey.getParameters();
                ECPoint ecPoint = new FixedPointCombMultiplier().multiply(ecParameterSpec.getG(), ecPrivateKey.getD());
                ECPublicKeySpec keySpec = new ECPublicKeySpec(ecPoint, ecParameterSpec);
                return keyFactory.generatePublic(keySpec);
            } else {
                throw new IllegalArgumentException("Unexpected key algorithm: " + algorithm);
            }
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static PrivateKey fromPemEncodedPrivateKey(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            List<Object> unknownObjects = new ArrayList<>();
            Object pemObject;
            while ((pemObject = parser.readObject()) != null) {
                if (pemObject instanceof PrivateKeyInfo) {
                    PrivateKeyInfo keyInfo = (PrivateKeyInfo) pemObject;
                    PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyInfo.getEncoded());
                    return createKeyFactory(keyInfo.getPrivateKeyAlgorithm())
                            .generatePrivate(keySpec);
                } else if (pemObject instanceof PEMKeyPair) {
                    PEMKeyPair pemKeypair = (PEMKeyPair) pemObject;
                    PrivateKeyInfo keyInfo = pemKeypair.getPrivateKeyInfo();
                    return createKeyFactory(keyInfo.getPrivateKeyAlgorithm())
                            .generatePrivate(new PKCS8EncodedKeySpec(keyInfo.getEncoded()));
                } else {
                    unknownObjects.add(pemObject);
                }
            }
            throw new IllegalArgumentException("Expected a private key, but found " + unknownObjects.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static PublicKey fromPemEncodedPublicKey(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            List<Object> unknownObjects = new ArrayList<>();
            Object pemObject;
            while ((pemObject = parser.readObject()) != null) {
                SubjectPublicKeyInfo keyInfo;
                if (pemObject instanceof SubjectPublicKeyInfo) {
                    keyInfo = (SubjectPublicKeyInfo) pemObject;
                } else if (pemObject instanceof PEMKeyPair) {
                    PEMKeyPair pemKeypair = (PEMKeyPair) pemObject;
                    keyInfo = pemKeypair.getPublicKeyInfo();
                } else {
                    unknownObjects.add(pemObject);
                    continue;
                }
                return createKeyFactory(keyInfo.getAlgorithm())
                        .generatePublic(new X509EncodedKeySpec(keyInfo.getEncoded()));
            }
            throw new IllegalArgumentException("Expected a public key, but found " + unknownObjects.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    // Note: Encoding using PKCS#1 as default as this is to be read by tools only supporting PKCS#1
    // Should ideally be PKCS#8
    public static String toPem(PrivateKey privateKey) {
        return toPem(privateKey, KeyFormat.PKCS1);
    }

    public static String toPem(PrivateKey privateKey, KeyFormat format) {
        switch (format) {
            case PKCS1:
                return toPkcs1Pem(privateKey);
            case PKCS8:
                return toPkcs8Pem(privateKey);
            default:
                throw new IllegalArgumentException("Unknown format: " + format);
        }
    }

    public static String toPem(PublicKey publicKey) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(publicKey);
            pemWriter.flush();
            return stringWriter.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String toPkcs1Pem(PrivateKey privateKey) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            String algorithm = privateKey.getAlgorithm();
            String type;
            if (algorithm.equals(RSA.getAlgorithmName())) {
                type = "RSA PRIVATE KEY";
            } else if (algorithm.equals(EC.getAlgorithmName())) {
                type = "EC PRIVATE KEY";
            } else {
                throw new IllegalArgumentException("Unexpected key algorithm: " + algorithm);
            }
            pemWriter.writeObject(new PemObject(type, getPkcs1Bytes(privateKey)));
            pemWriter.flush();
            return stringWriter.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String toPkcs8Pem(PrivateKey privateKey) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject("PRIVATE KEY", privateKey.getEncoded()));
            pemWriter.flush();
            return stringWriter.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static byte[] getPkcs1Bytes(PrivateKey privateKey) throws IOException{
        byte[] privBytes = privateKey.getEncoded();
        PrivateKeyInfo pkInfo = PrivateKeyInfo.getInstance(privBytes);
        ASN1Encodable encodable = pkInfo.parsePrivateKey();
        ASN1Primitive primitive = encodable.toASN1Primitive();
        return primitive.getEncoded();
    }

    private static KeyFactory createKeyFactory(AlgorithmIdentifier algorithm) throws NoSuchAlgorithmException {
        if (X9ObjectIdentifiers.id_ecPublicKey.equals(algorithm.getAlgorithm())) {
            return createKeyFactory(KeyAlgorithm.EC);
        } else if (PKCSObjectIdentifiers.rsaEncryption.equals(algorithm.getAlgorithm())) {
            return createKeyFactory(KeyAlgorithm.RSA);
        } else {
            throw new IllegalArgumentException("Unknown key algorithm: " + algorithm);
        }
    }

    private static KeyFactory createKeyFactory(KeyAlgorithm algorithm) throws NoSuchAlgorithmException {
        return KeyFactory.getInstance(algorithm.getAlgorithmName(), BouncyCastleProviderHolder.getInstance());
    }

}
