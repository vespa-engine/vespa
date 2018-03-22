// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import com.yahoo.athenz.auth.util.Crypto;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
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
import java.security.spec.PKCS8EncodedKeySpec;

/**
 * @author bjorncs
 */
public class KeyUtils {
    private KeyUtils() {}

    public static KeyPair generateKeypair(KeyAlgorithm algorithm, int keySize) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm.getAlgorithmName());
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
        return Crypto.extractPublicKey(privateKey);
    }

    public static PrivateKey fromPemEncodedPrivateKey(String pem) {
        try (PEMParser parser = new PEMParser(new StringReader(pem))) {
            Object pemObject = parser.readObject();
            if (pemObject instanceof PrivateKeyInfo) {
                PrivateKeyInfo keyInfo = (PrivateKeyInfo) pemObject;
                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyInfo.getEncoded());
                return KeyFactory.getInstance(KeyAlgorithm.RSA.getAlgorithmName()).generatePrivate(keySpec);
            } else if (pemObject instanceof PEMKeyPair) {
                PEMKeyPair pemKeypair = (PEMKeyPair) pemObject;
                PrivateKeyInfo keyInfo = pemKeypair.getPrivateKeyInfo();
                JcaPEMKeyConverter pemConverter = new JcaPEMKeyConverter();
                return pemConverter.getPrivateKey(keyInfo);
            }
            throw new IllegalArgumentException("Unexpected type of PEM type: " + pemObject);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static String toPem(PrivateKey privateKey) {
        try (StringWriter stringWriter = new StringWriter(); JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
            pemWriter.writeObject(new PemObject("PRIVATE KEY", privateKey.getEncoded()));
            pemWriter.flush();
            return stringWriter.toString();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
