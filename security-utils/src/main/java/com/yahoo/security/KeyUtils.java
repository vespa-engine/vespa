// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.jcajce.provider.asymmetric.ec.BCECPrivateKey;
import org.bouncycastle.jce.spec.ECParameterSpec;
import org.bouncycastle.jce.spec.ECPublicKeySpec;
import org.bouncycastle.math.ec.ECPoint;
import org.bouncycastle.math.ec.FixedPointCombMultiplier;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
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
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
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
                KeyFactory keyFactory = KeyFactory.getInstance(RSA.getAlgorithmName(), BouncyCastleProviderHolder.getInstance());
                RSAPrivateCrtKey rsaPrivateCrtKey = (RSAPrivateCrtKey) privateKey;
                RSAPublicKeySpec keySpec = new RSAPublicKeySpec(rsaPrivateCrtKey.getModulus(), rsaPrivateCrtKey.getPublicExponent());
                return keyFactory.generatePublic(keySpec);
            } else if (algorithm.equals(EC.getAlgorithmName())) {
                KeyFactory keyFactory = KeyFactory.getInstance(EC.getAlgorithmName(), BouncyCastleProviderHolder.getInstance());
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
                    return KeyFactory.getInstance(RSA.getAlgorithmName()).generatePrivate(keySpec);
                } else if (pemObject instanceof PEMKeyPair) {
                    PEMKeyPair pemKeypair = (PEMKeyPair) pemObject;
                    PrivateKeyInfo keyInfo = pemKeypair.getPrivateKeyInfo();
                    JcaPEMKeyConverter pemConverter = new JcaPEMKeyConverter().setProvider(BouncyCastleProviderHolder.getInstance());
                    return pemConverter.getPrivateKey(keyInfo);
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

    public static String toPem(PrivateKey privateKey) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            String algorithm = privateKey.getAlgorithm();
            // Note: Encoding using PKCS#1 as this is to be read by tools only supporting PKCS#1
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

    private static byte[] getPkcs1Bytes(PrivateKey privateKey) throws IOException{

        byte[] privBytes = privateKey.getEncoded();
        PrivateKeyInfo pkInfo = PrivateKeyInfo.getInstance(privBytes);
        ASN1Encodable encodable = pkInfo.parsePrivateKey();
        ASN1Primitive primitive = encodable.toASN1Primitive();
        return primitive.getEncoded();
    }
}
