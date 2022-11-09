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
import org.bouncycastle.math.ec.rfc7748.X25519;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.util.Arrays;
import org.bouncycastle.util.io.pem.PemObject;

import javax.crypto.KeyAgreement;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.security.GeneralSecurityException;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.NamedParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.security.spec.XECPrivateKeySpec;
import java.security.spec.XECPublicKeySpec;
import java.util.ArrayList;
import java.util.Base64;
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

    public static KeyPair toKeyPair(PrivateKey privateKey) {
        return new KeyPair(extractPublicKey(privateKey), privateKey);
    }

    public static KeyPair keyPairFromPemEncodedPrivateKey(String pem) {
        return toKeyPair(fromPemEncodedPrivateKey(pem));
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

    public static XECPublicKey fromRawX25519PublicKey(byte[] rawKeyBytes) {
        try {
            NamedParameterSpec paramSpec = new NamedParameterSpec("X25519");
            KeyFactory keyFactory        = KeyFactory.getInstance("XDH");
            // X25519 public key byte representations are in little-endian (RFC 7748).
            // Since BigInteger expects byte buffers in big-endian order, we reverse the byte ordering.
            byte[] asBigEndian = Arrays.reverse(rawKeyBytes);
            // https://datatracker.ietf.org/doc/html/rfc7748#section-5
            // "The u-coordinates are elements of the underlying field GF(2^255 - 19)
            //   or GF(2^448 - 2^224 - 1) and are encoded as an array of bytes, u, in
            //   little-endian order such that u[0] + 256*u[1] + 256^2*u[2] + ... +
            //   256^(n-1)*u[n-1] is congruent to the value modulo p and u[n-1] is
            //   minimal.  When receiving such an array, implementations of X25519
            //   (but not X448) MUST mask the most significant bit in the final byte.
            //   This is done to preserve compatibility with point formats that
            //   reserve the sign bit for use in other protocols and to increase
            //   resistance to implementation fingerprinting."
            asBigEndian[0] &= 0x7f; // MSBit of MSByte clear. TODO do we always want this? Are "we" the "implementation" here?
            BigInteger pubU = new BigInteger(asBigEndian);
            return (XECPublicKey) keyFactory.generatePublic(new XECPublicKeySpec(paramSpec, pubU));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    /** Returns the bytes representing the BigInteger of the X25519 public key EC point U coordinate */
    public static byte[] toRawX25519PublicKeyBytes(XECPublicKey publicKey) {
        // Raw byte representation is in little-endian, while BigInteger representation is
        // big-endian. Basically undoes what we do on the input path in fromRawX25519PublicKey().
        return Arrays.reverse(publicKey.getU().toByteArray());
    }

    public static XECPublicKey fromBase64EncodedX25519PublicKey(String base64pk) {
        byte[] rawKeyBytes = Base64.getUrlDecoder().decode(base64pk);
        return fromRawX25519PublicKey(rawKeyBytes);
    }

    public static String toBase64EncodedX25519PublicKey(XECPublicKey publicKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(toRawX25519PublicKeyBytes(publicKey));
    }

    // This sanity check is to avoid any DoS potential caused by passing in a very large key
    // to a quadratic Base58 decoding routing. We don't do this for the encoding since we
    // always control the input size for that case.
    private static void verifyB58InputSmallEnoughToBeX25519Key(String key) {
        if (key.length() > 64) { // a very wide margin...
            throw new IllegalArgumentException("Input Base58 is too large to represent an X25519 key");
        }
    }

    public static XECPublicKey fromBase58EncodedX25519PublicKey(String base58pk) {
        verifyB58InputSmallEnoughToBeX25519Key(base58pk);
        byte[] rawKeyBytes = Base58.codec().decode(base58pk);
        return fromRawX25519PublicKey(rawKeyBytes);
    }

    public static String toBase58EncodedX25519PublicKey(XECPublicKey publicKey) {
        return Base58.codec().encode(toRawX25519PublicKeyBytes(publicKey));
    }

    public static XECPrivateKey fromRawX25519PrivateKey(byte[] rawScalarBytes) {
        try {
            NamedParameterSpec paramSpec = new NamedParameterSpec("X25519");
            KeyFactory keyFactory        = KeyFactory.getInstance("XDH");
            return (XECPrivateKey) keyFactory.generatePrivate(new XECPrivateKeySpec(paramSpec, rawScalarBytes));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO ensure output is clamped?
    public static byte[] toRawX25519PrivateKeyBytes(XECPrivateKey privateKey) {
        var maybeScalar = privateKey.getScalar();
        if (maybeScalar.isPresent()) {
            return maybeScalar.get();
        }
        throw new IllegalArgumentException("Could not extract scalar representation of X25519 private key. " +
                                           "It might be a hardware-protected private key.");
    }

    public static XECPrivateKey fromBase64EncodedX25519PrivateKey(String base64pk) {
        byte[] rawKeyBytes = Base64.getUrlDecoder().decode(base64pk);
        return fromRawX25519PrivateKey(rawKeyBytes);
    }

    public static String toBase64EncodedX25519PrivateKey(XECPrivateKey privateKey) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(toRawX25519PrivateKeyBytes(privateKey));
    }

    public static XECPrivateKey fromBase58EncodedX25519PrivateKey(String base58pk) {
        verifyB58InputSmallEnoughToBeX25519Key(base58pk);
        byte[] rawKeyBytes = Base58.codec().decode(base58pk);
        return fromRawX25519PrivateKey(rawKeyBytes);
    }

    public static String toBase58EncodedX25519PrivateKey(XECPrivateKey privateKey) {
        return Base58.codec().encode(toRawX25519PrivateKeyBytes(privateKey));
    }

    // TODO unify with generateKeypair()?
    public static KeyPair generateX25519KeyPair() {
        try {
            return KeyPairGenerator.getInstance("X25519").generateKeyPair();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    // TODO unify with extractPublicKey()
    public static XECPublicKey extractX25519PublicKey(XECPrivateKey privateKey) {
        byte[] privScalar = toRawX25519PrivateKeyBytes(privateKey);
        byte[] pubPoint = new byte[X25519.POINT_SIZE];
        X25519.generatePublicKey(privScalar, 0, pubPoint, 0); // scalarMultBase => public key point
        return fromRawX25519PublicKey(pubPoint);
    }

    /**
     * Computes a shared secret using the Elliptic Curve Diffie-Hellman (ECDH) protocol for X25519 curves.
     * <p>
     * Let Bob have private (secret) key <code>skB</code> and public key <code>pkB</code>.
     * Let Alice have private key <code>skA</code> and public key <code>pkA</code>.
     * ECDH lets both parties separately compute their own side of:
     * </p>
     * <pre>
     *   ecdh(skB, pkA) == ecdh(skA, pkB)
     * </pre>
     * <p>
     * This arrives at the same shared secret without needing to know the secret key of
     * the other party, but both parties must know their own secret to derive the correct
     * shared secret. Third party Eve sneaking around in the bushes cannot compute the
     * shared secret without knowing at least one of the secrets.
     * </p>
     * <p>
     * Performs RFC 7748-recommended (and RFC 9180-mandated) check for "non-contributory"
     * private keys by checking if the resulting shared secret comprises all zero bytes.
     * </p>
     *
     * @param privateKey X25519 private key
     * @param publicKey X25519 public key
     * @return shared Diffie-Hellman secret. Security note: this value should never be
     *         used <em>directly</em> as a key; use a key derivation function (KDF).
     *
     * @see <a href="https://www.rfc-editor.org/rfc/rfc7748">RFC 7748 Elliptic Curves for Security</a>
     * @see <a href="https://www.rfc-editor.org/rfc/rfc9180.html">RFC 9180 Hybrid Public Key Encryption</a>
     * @see <a href="https://en.wikipedia.org/wiki/Elliptic-curve_Diffie%E2%80%93Hellman">ECDH on wiki</a>
     */
    public static byte[] ecdh(XECPrivateKey privateKey, XECPublicKey publicKey) {
        try {
            var keyAgreement = KeyAgreement.getInstance("XDH");
            keyAgreement.init(privateKey);
            keyAgreement.doPhase(publicKey, true);
            byte[] sharedSecret = keyAgreement.generateSecret();
            // RFC 7748 recommends checking that the shared secret is not all zero bytes.
            // Furthermore, RFC 9180 states "For X25519 and X448, public keys and Diffie-Hellman
            // outputs MUST be validated as described in [RFC7748]".
            // Usually we won't get here at all since Java will throw an InvalidKeyException
            // from detecting a key with a low order point. But in case we _do_ get here, fail fast.
            if (SideChannelSafe.allZeros(sharedSecret)) {
                throw new IllegalArgumentException("Computed shared secret is all zeroes");
            }
            return sharedSecret;
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

}
