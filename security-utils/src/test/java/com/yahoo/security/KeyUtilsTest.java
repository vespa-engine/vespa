// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;

import static com.yahoo.security.ArrayUtils.hex;
import static com.yahoo.security.ArrayUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bjorncs
 */
public class KeyUtilsTest {

    @Test
    void can_extract_public_key_from_rsa_private() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        PublicKey publicKey = KeyUtils.extractPublicKey(keyPair.getPrivate());
        assertNotNull(publicKey);
    }

    @Test
    void can_extract_public_key_from_ecdsa_private() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        PublicKey publicKey = KeyUtils.extractPublicKey(keyPair.getPrivate());
        assertNotNull(publicKey);
    }

    @Test
    void can_serialize_and_deserialize_rsa_privatekey_using_pkcs1_pem_format() {
        testPrivateKeySerialization(KeyAlgorithm.RSA, KeyFormat.PKCS1, "RSA PRIVATE KEY");
    }

    @Test
    void can_serialize_and_deserialize_rsa_privatekey_using_pkcs8_pem_format() {
        testPrivateKeySerialization(KeyAlgorithm.RSA, KeyFormat.PKCS8, "PRIVATE KEY");
    }

    @Test
    void can_serialize_and_deserialize_ec_privatekey_using_pkcs1_pem_format() {
        testPrivateKeySerialization(KeyAlgorithm.EC, KeyFormat.PKCS1, "EC PRIVATE KEY");
    }

    @Test
    void can_serialize_and_deserialize_ec_privatekey_using_pkcs8_pem_format() {
        testPrivateKeySerialization(KeyAlgorithm.EC, KeyFormat.PKCS8, "PRIVATE KEY");
    }

    @Test
    void can_serialize_and_deserialize_rsa_publickey_using_pem_format() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
        String pem = KeyUtils.toPem(keyPair.getPublic());
        assertTrue(pem.contains("BEGIN PUBLIC KEY"));
        assertTrue(pem.contains("END PUBLIC KEY"));
        PublicKey deserializedKey = KeyUtils.fromPemEncodedPublicKey(pem);
        assertEquals(keyPair.getPublic(), deserializedKey);
        assertEquals(KeyAlgorithm.RSA.getAlgorithmName(), deserializedKey.getAlgorithm());
    }

    @Test
    void can_serialize_and_deserialize_ec_publickey_using_pem_format() {
        KeyPair keyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        String pem = KeyUtils.toPem(keyPair.getPublic());
        assertTrue(pem.contains("BEGIN PUBLIC KEY"));
        assertTrue(pem.contains("END PUBLIC KEY"));
        PublicKey deserializedKey = KeyUtils.fromPemEncodedPublicKey(pem);
        assertEquals(keyPair.getPublic(), deserializedKey);
        assertEquals(KeyAlgorithm.EC.getAlgorithmName(), deserializedKey.getAlgorithm());
    }

    private static void testPrivateKeySerialization(KeyAlgorithm keyAlgorithm, KeyFormat keyFormat, String pemLabel) {
        KeyPair keyPair = KeyUtils.generateKeypair(keyAlgorithm);
        String pem = KeyUtils.toPem(keyPair.getPrivate(), keyFormat);
        assertTrue(pem.contains("BEGIN " + pemLabel));
        assertTrue(pem.contains("END " + pemLabel));
        PrivateKey deserializedKey = KeyUtils.fromPemEncodedPrivateKey(pem);
        assertEquals(keyPair.getPrivate(), deserializedKey);
        assertEquals(keyAlgorithm.getAlgorithmName(), deserializedKey.getAlgorithm());
    }

    private static XECPrivateKey xecPrivFromHex(String hex) {
        return KeyUtils.fromRawX25519PrivateKey(unhex(hex));
    }

    private static String xecHexFromPriv(XECPrivateKey privateKey) {
        return hex(KeyUtils.toRawX25519PrivateKeyBytes(privateKey));
    }

    private static XECPublicKey xecPubFromHex(String hex) {
        return KeyUtils.fromRawX25519PublicKey(unhex(hex));
    }

    private static String xecHexFromPub(XECPublicKey publicKey) {
        return hex(KeyUtils.toRawX25519PublicKeyBytes(publicKey));
    }

    /**
     * RFC 7748 Section 6.1, Curve25519 Diffie-Hellman test vector
     */
    @Test
    void x25519_ecdh_matches_rfc_7748_test_vector() {
        var alice_priv = xecPrivFromHex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
        var alice_pub  = xecPubFromHex( "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a");
        var bob_priv   = xecPrivFromHex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb");
        var bob_public = xecPubFromHex( "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f");

        var expectedShared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";

        byte[] sharedAliceToBob = KeyUtils.ecdh(alice_priv, bob_public);
        assertEquals(expectedShared, hex(sharedAliceToBob));

        byte[] sharedBobToAlice = KeyUtils.ecdh(bob_priv, alice_pub);
        assertEquals(expectedShared, hex(sharedBobToAlice));
    }

    // From https://github.com/google/wycheproof/blob/master/testvectors/x25519_test.json (tcId 32)
    @Test
    void x25519_ecdh_fails_if_shared_secret_is_all_zeros_case_1() {
        var alice_priv = xecPrivFromHex("88227494038f2bb811d47805bcdf04a2ac585ada7f2f23389bfd4658f9ddd45e");
        var bob_public = xecPubFromHex( "0000000000000000000000000000000000000000000000000000000000000000");
        // This actually internally fails with an InvalidKeyException due to small point order
        assertThrows(RuntimeException.class, () -> KeyUtils.ecdh(alice_priv, bob_public));
    }

    // From https://github.com/google/wycheproof/blob/master/testvectors/x25519_test.json (tcId 63)
    @Test
    void x25519_ecdh_fails_if_shared_secret_is_all_zeros_case_2() {
        var alice_priv = xecPrivFromHex("e0f978dfcd3a8f1a5093418de54136a584c20b7b349afdf6c0520886f95b1272");
        var bob_public = xecPubFromHex( "e0eb7a7c3b41b8ae1656e3faf19fc46ada098deb9c32b1fd866205165f49b800");
        // This actually internally fails with an InvalidKeyException due to small point order
        assertThrows(RuntimeException.class, () -> KeyUtils.ecdh(alice_priv, bob_public));
    }

    @Test
    void x25519_public_key_deserialization_clears_msb() {
        var alice_priv = xecPrivFromHex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
        var bob_public = xecPubFromHex( "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882bcf"); // note msb toggled in last byte
        var expectedShared = "4a5d9d5ba4ce2de1728e3bf480350f25e07e21c947d19e3376f09b3c1e161742";
        byte[] sharedAliceToBob = KeyUtils.ecdh(alice_priv, bob_public);
        assertEquals(expectedShared, hex(sharedAliceToBob));
    }

    @Test
    void x25519_private_key_serialization_roundtrip_maintains_original_structure() {
        var privHex = "77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a";
        var priv = xecPrivFromHex(privHex);
        assertEquals(privHex, xecHexFromPriv(priv));

        // Base 64
        var privB64 = KeyUtils.toBase64EncodedX25519PrivateKey(priv);
        var priv2 = KeyUtils.fromBase64EncodedX25519PrivateKey(privB64);
        assertEquals(privHex, xecHexFromPriv(priv2));

        // Base 58
        var privB58 = KeyUtils.toBase58EncodedX25519PrivateKey(priv);
        var priv3 = KeyUtils.fromBase58EncodedX25519PrivateKey(privB58);
        assertEquals(privHex, xecHexFromPriv(priv3));
    }

    @Test
    void x25519_public_key_serialization_roundtrip_maintains_original_structure() {
        var pubHex = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a";
        var pub = xecPubFromHex(pubHex);
        assertEquals(pubHex, xecHexFromPub(pub));

        // Base 64
        var pubB64 = KeyUtils.toBase64EncodedX25519PublicKey(pub);
        var pub2 = KeyUtils.fromBase64EncodedX25519PublicKey(pubB64);
        assertEquals(pubHex, xecHexFromPub(pub2));

        // Base 58
        var pubB58 = KeyUtils.toBase58EncodedX25519PublicKey(pub);
        var pub3 = KeyUtils.fromBase58EncodedX25519PublicKey(pubB58);
        assertEquals(pubHex, xecHexFromPub(pub3));
    }

    @Test
    void can_extract_public_key_from_x25519_private_key() {
        var priv = xecPrivFromHex("77076d0a7318a57d3c16c17251b26645df4c2f87ebc0992ab177fba51db92c2a");
        var expectedPubHex = "8520f0098930a754748b7ddcb43ef75a0dbf3a0d26381af4eba4a98eaa9b4e6a";
        var pub = KeyUtils.extractX25519PublicKey(priv);
        assertEquals(expectedPubHex, xecHexFromPub(pub));

        priv = xecPrivFromHex("5dab087e624a8a4b79e17f8b83800ee66f3bb1292618b6fd1c2f8b27ff88e0eb");
        expectedPubHex = "de9edb7d7b7dc1b4d35b61c2ece435373f8343c85b78674dadfc7e146f882b4f";
        pub = KeyUtils.extractX25519PublicKey(priv);
        assertEquals(expectedPubHex, xecHexFromPub(pub));
    }

}
