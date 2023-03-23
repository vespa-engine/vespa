// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import com.yahoo.security.hpke.Aead;
import com.yahoo.security.hpke.Ciphersuite;
import com.yahoo.security.hpke.Hpke;
import com.yahoo.security.hpke.Kdf;
import com.yahoo.security.hpke.Kem;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;

import static com.yahoo.security.ArrayUtils.hex;
import static com.yahoo.security.ArrayUtils.toUtf8Bytes;
import static com.yahoo.security.ArrayUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author vekterli
 */
public class HpkeTest {

    // Base mode key pairs
    static KeyPair ephemeralRfc9180BaseTestVectorKeyPair() {
        var priv = KeyUtils.fromRawX25519PrivateKey(unhex("52c4a758a802cd8b936eceea314432798d5baf2d7e9235dc084ab1b9cfa2f736"));
        var pub  = KeyUtils.fromRawX25519PublicKey(unhex("37fda3567bdbd628e88668c3c8d7e97d1d1253b6d4ea6d44c150f741f1bf4431"));
        return new KeyPair(pub, priv);
    }

    static KeyPair receiverRfc9180BaseTestVectorKeyPair() {
        var priv = KeyUtils.fromRawX25519PrivateKey(unhex("4612c550263fc8ad58375df3f557aac531d26850903e55a9f23f21d8534e8ac8"));
        var pub  = KeyUtils.fromRawX25519PublicKey(unhex("3948cfe0ad1ddb695d780e59077195da6c56506b027329794ab02bca80815c4d"));
        return new KeyPair(pub, priv);
    }

    // Auth mode key pairs
    static KeyPair ephemeralRfc9180AuthTestVectorKeyPair() {
        var priv = KeyUtils.fromRawX25519PrivateKey(unhex("ff4442ef24fbc3c1ff86375b0be1e77e88a0de1e79b30896d73411c5ff4c3518"));
        var pub  = KeyUtils.fromRawX25519PublicKey(unhex("23fb952571a14a25e3d678140cd0e5eb47a0961bb18afcf85896e5453c312e76"));
        return new KeyPair(pub, priv);
    }

    static KeyPair receiverRfc9180AuthTestVectorKeyPair() {
        var priv = KeyUtils.fromRawX25519PrivateKey(unhex("fdea67cf831f1ca98d8e27b1f6abeb5b7745e9d35348b80fa407ff6958f9137e"));
        var pub  = KeyUtils.fromRawX25519PublicKey(unhex("1632d5c2f71c2b38d0a8fcc359355200caa8b1ffdf28618080466c909cb69b2e"));
        return new KeyPair(pub, priv);
    }

    static KeyPair senderRfc9180AuthTestVectorKeyPair() {
        var priv = KeyUtils.fromRawX25519PrivateKey(unhex("dc4a146313cce60a278a5323d321f051c5707e9c45ba21a3479fecdf76fc69dd"));
        var pub  = KeyUtils.fromRawX25519PublicKey(unhex("8b0c70873dc5aecb7f9ee4e62406a397b350e57012be45cf53b7105ae731790b"));
        return new KeyPair(pub, priv);
    }

    private static XECPublicKey pk(KeyPair kp) {
        return (XECPublicKey) kp.getPublic();
    }

    private static XECPrivateKey sk(KeyPair kp) {
        return (XECPrivateKey) kp.getPrivate();
    }

    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html">RFC 9180</a> test vector, Base mode
     *
     * Appendix A.1.1
     *
     * DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, AES-128-GCM
     *
     * Only tests first encryption, i.e. sequence number 0.
     */
    @Test
    void passes_rfc_9180_dhkem_x25519_hkdf_sha256_hkdf_sha256_aes_gcm_128_base_test_vector() {
        byte[] info = unhex("4f6465206f6e2061204772656369616e2055726e");
        byte[] pt   = unhex("4265617574792069732074727574682c20747275746820626561757479");
        byte[] aad  = unhex("436f756e742d30");
        var kpR     = receiverRfc9180BaseTestVectorKeyPair();

        var kem = Kem.dHKemX25519HkdfSha256(new Kem.UnsafeDeterminsticKeyPairOnlyUsedByTesting(ephemeralRfc9180BaseTestVectorKeyPair()));
        var ciphersuite = Ciphersuite.of(kem, Kdf.hkdfSha256(), Aead.aes128Gcm());

        var hpke = Hpke.of(ciphersuite);
        var s = hpke.sealBase(pk(kpR), info, aad, pt);

        // The "enc" output is the ephemeral public key
        var expectedEnc = "37fda3567bdbd628e88668c3c8d7e97d1d1253b6d4ea6d44c150f741f1bf4431";
        assertEquals(expectedEnc, hex(s.enc()));

        var expectedCiphertext = "f938558b5d72f1a23810b4be2ab4f84331acc02fc97babc53a52ae8218a355a9" +
                                 "6d8770ac83d07bea87e13c512a";
        assertEquals(expectedCiphertext, hex(s.ciphertext()));

        byte[] openedPt = hpke.openBase(s.enc(), sk(kpR), info, aad, s.ciphertext());
        assertEquals(hex(pt), hex(openedPt));
    }

    /**
     * <a href="https://www.rfc-editor.org/rfc/rfc9180.html">RFC 9180</a> test vector, Auth mode
     *
     * Appendix A.1.3
     *
     * DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, AES-128-GCM
     *
     * Only tests first encryption, i.e. sequence number 0.
     */
    @Test
    void passes_rfc_9180_dhkem_x25519_hkdf_sha256_hkdf_sha256_aes_gcm_128_auth_test_vector() {
        byte[] info = unhex("4f6465206f6e2061204772656369616e2055726e");
        byte[] pt   = unhex("4265617574792069732074727574682c20747275746820626561757479");
        byte[] aad  = unhex("436f756e742d30");
        var kpR     = receiverRfc9180AuthTestVectorKeyPair();
        var kpS     = senderRfc9180AuthTestVectorKeyPair();

        var kem = Kem.dHKemX25519HkdfSha256(new Kem.UnsafeDeterminsticKeyPairOnlyUsedByTesting(ephemeralRfc9180AuthTestVectorKeyPair()));
        var ciphersuite = Ciphersuite.of(kem, Kdf.hkdfSha256(), Aead.aes128Gcm());

        var hpke = Hpke.of(ciphersuite);
        var s = hpke.sealAuth(pk(kpR), info, aad, pt, sk(kpS));

        // The "enc" output is the ephemeral public key
        var expectedEnc = "23fb952571a14a25e3d678140cd0e5eb47a0961bb18afcf85896e5453c312e76";
        assertEquals(expectedEnc, hex(s.enc()));

        var expectedCiphertext = "5fd92cc9d46dbf8943e72a07e42f363ed5f721212cd90bcfd072bfd9f44e06b8" +
                                 "0fd17824947496e21b680c141b";
        assertEquals(expectedCiphertext, hex(s.ciphertext()));

        byte[] openedPt = hpke.openAuth(s.enc(), sk(kpR), info, aad, s.ciphertext(), pk(kpS));
        assertEquals(hex(pt), hex(openedPt));
    }

    @Test
    void sealing_creates_new_ephemeral_key_pair_per_invocation() {
        byte[] info = toUtf8Bytes("the finest info");
        byte[] pt   = toUtf8Bytes("seagulls attack at dawn");
        byte[] aad  = toUtf8Bytes("cool AAD");
        var kpR     = receiverRfc9180BaseTestVectorKeyPair();

        var hpke = Hpke.of(Ciphersuite.defaultSuite());

        var s1 = hpke.sealBase(pk(kpR), info, aad, pt);
        byte[] openedPt = hpke.openBase(s1.enc(), sk(kpR), info, aad, s1.ciphertext());
        assertEquals(hex(pt), hex(openedPt));

        var s2 = hpke.sealBase(pk(kpR), info, aad, pt);
        openedPt = hpke.openBase(s2.enc(), sk(kpR), info, aad, s2.ciphertext());
        assertEquals(hex(pt), hex(openedPt));

        assertNotEquals(hex(s1.enc()), hex(s2.enc())); // This is the ephemeral public key
    }

    @Test
    void opening_ciphertext_with_different_info_or_aad_fails() {
        byte[] info = toUtf8Bytes("the finest info");
        byte[] pt   = toUtf8Bytes("seagulls attack at dawn");
        byte[] aad  = toUtf8Bytes("cool AAD");
        var kpR     = receiverRfc9180BaseTestVectorKeyPair();

        var hpke = Hpke.of(Ciphersuite.defaultSuite());
        var s = hpke.sealBase(pk(kpR), info, aad, pt);

        byte[] badInfo = toUtf8Bytes("lesser info");
        // TODO better exception classes! Triggers AEAD auth tag mismatch behind the scenes
        assertThrows(RuntimeException.class, () -> hpke.openBase(s.enc(), sk(kpR), badInfo, aad, s.ciphertext()));
        byte[] badAad = toUtf8Bytes("non-groovy AAD");
        assertThrows(RuntimeException.class, () -> hpke.openBase(s.enc(), sk(kpR), info, badAad, s.ciphertext()));
    }

}
