// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

import static com.yahoo.security.ArrayUtils.hex;
import static com.yahoo.security.ArrayUtils.toUtf8Bytes;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SharedKeyTest {

    private static final byte[] KEY_ID_1 = new byte[] {1};

    @Test
    void generated_secret_key_is_128_bit_aes() {
        var receiverKeyPair = KeyUtils.generateX25519KeyPair();
        var shared = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), KEY_ID_1);
        var secret = shared.secretKey();
        assertEquals(secret.getAlgorithm(), "AES");
        assertEquals(secret.getEncoded().length, 16);
    }

    @Test
    void sealed_shared_key_can_be_exchanged_via_token_and_computes_identical_secret_key_at_receiver() {
        var receiverKeyPair = KeyUtils.generateX25519KeyPair();

        var myShared    = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), KEY_ID_1);
        var publicToken = myShared.sealedSharedKey().toTokenString();

        var theirSealed = SealedSharedKey.fromTokenString(publicToken);
        var theirShared = SharedKeyGenerator.fromSealedKey(theirSealed, receiverKeyPair.getPrivate());

        assertArrayEquals(myShared.secretKey().getEncoded(), theirShared.secretKey().getEncoded());
    }

    @Test
    void token_v1_representation_is_stable() {
        var receiverPrivate = KeyUtils.fromBase64EncodedX25519PrivateKey("4qGcntygFn_a3uqeBa1PbDlygQ-cpOuNznTPIz9ftWE");
        var receiverPublic  = KeyUtils.fromBase64EncodedX25519PublicKey( "ROAH_S862tNMpbJ49lu1dPXFCPHFIXZK30pSrMZEmEg");
        byte[] keyId        = toUtf8Bytes("my key ID");

        // Token generated for the above receiver public key, with the below expected shared secret (in hex)
        var publicToken = "AQlteSBrZXkgSUQgAtTxJJdmv3eUoW5Z3NJSdZ3poKPEkW0SJOGQXP6CaC5XfyAVoUlK_NyYIMsJKyNYKU6WmagZpVG2zQGFJoqiFA";
        var expectedSharedSecret = "1b33b4dcd6a94e5a4a1ee6d208197d01";

        var theirSealed = SealedSharedKey.fromTokenString(publicToken);
        var theirShared = SharedKeyGenerator.fromSealedKey(theirSealed, receiverPrivate);

        assertArrayEquals(keyId, theirSealed.keyId());
        assertEquals(expectedSharedSecret, hex(theirShared.secretKey().getEncoded()));
    }

    @Test
    void unrelated_private_key_cannot_decrypt_shared_secret_key() {
        var aliceKeyPair = KeyUtils.generateX25519KeyPair();
        var eveKeyPair   = KeyUtils.generateX25519KeyPair();
        var bobShared    = SharedKeyGenerator.generateForReceiverPublicKey(aliceKeyPair.getPublic(), KEY_ID_1);
        assertThrows(RuntimeException.class, // TODO consider distinct exception class
                     () -> SharedKeyGenerator.fromSealedKey(bobShared.sealedSharedKey(), eveKeyPair.getPrivate()));
    }

    @Test
    void token_carries_opaque_key_id_bytes_as_metadata() {
        byte[] keyId    = toUtf8Bytes("hello key id world");
        var keyPair     = KeyUtils.generateX25519KeyPair();
        var myShared    = SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), keyId);
        var publicToken = myShared.sealedSharedKey().toTokenString();
        var theirShared = SealedSharedKey.fromTokenString(publicToken);
        assertArrayEquals(theirShared.keyId(), keyId);
    }

    @Test
    void key_id_integrity_is_protected_by_aad() {
        byte[] goodId = toUtf8Bytes("my key 1");
        var keyPair   = KeyUtils.generateX25519KeyPair();
        var myShared  = SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), goodId);
        var mySealed  = myShared.sealedSharedKey();
        byte[] badId  = toUtf8Bytes("my key 2");

        var tamperedShared = new SealedSharedKey(badId, mySealed.enc(), mySealed.ciphertext());
        // Should not be able to unseal the token since the AAD auth tag won't be correct
        assertThrows(RuntimeException.class, // TODO consider distinct exception class
                     () -> SharedKeyGenerator.fromSealedKey(tamperedShared, keyPair.getPrivate()));
    }

    @Test
    void key_id_encoding_size_is_bounded() {
        byte[] okId = new byte[SealedSharedKey.MAX_KEY_ID_UTF8_LENGTH];
        Arrays.fill(okId, (byte)'A');
        var keyPair  = KeyUtils.generateX25519KeyPair();
        var myShared = SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), okId);
        assertArrayEquals(okId, myShared.sealedSharedKey().keyId());

        var asToken = myShared.sealedSharedKey().toTokenString();
        var decoded = SealedSharedKey.fromTokenString(asToken);
        assertArrayEquals(okId, decoded.keyId());

        byte[] tooBigId = new byte[SealedSharedKey.MAX_KEY_ID_UTF8_LENGTH + 1];
        Arrays.fill(tooBigId, (byte)'A');
        assertThrows(IllegalArgumentException.class,
                     () -> SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), tooBigId));
    }

    @Test
    void malformed_utf8_key_id_is_rejected_on_construction() {
        byte[] malformedId = new byte[]{ (byte)0xC0 }; // First part of a 2-byte continuation without trailing byte
        var keyPair = KeyUtils.generateX25519KeyPair();
        assertThrows(IllegalArgumentException.class,
                     () -> SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), malformedId));
    }

    // TODO make this test less implementation specific if possible...
    @Test
    void malformed_utf8_key_id_is_rejected_on_parsing() {
        byte[] goodId = new byte[] { (byte)'A' };
        var keyPair   = KeyUtils.generateX25519KeyPair();
        var myShared  = SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), goodId);

        // token header is u8 version || u8 key id length || key id bytes ...
        // Since the key ID is only 1 bytes long, patch it with a bad UTF-8 value (see above test)
        byte[] tokenBytes = Base64.getUrlDecoder().decode(myShared.sealedSharedKey().toTokenString());
        tokenBytes[2] = (byte)0xC0;
        var tokenStr = Base64.getUrlEncoder().encodeToString(tokenBytes);
        assertThrows(IllegalArgumentException.class, () -> SealedSharedKey.fromTokenString(tokenStr));
    }

    static byte[] streamEncryptString(String data, SecretSharedKey secretSharedKey) throws IOException {
        var cipher = SharedKeyGenerator.makeAesGcmEncryptionCipher(secretSharedKey);
        var outStream = new ByteArrayOutputStream();
        try (var cipherStream = new CipherOutputStream(outStream, cipher)) {
            cipherStream.write(data.getBytes(StandardCharsets.UTF_8));
            cipherStream.flush();
        }
        return outStream.toByteArray();
    }

    static String streamDecryptString(byte[] encrypted, SecretSharedKey secretSharedKey) throws IOException {
        var cipher   = SharedKeyGenerator.makeAesGcmDecryptionCipher(secretSharedKey);
        var inStream = new ByteArrayInputStream(encrypted);
        var total    = ByteBuffer.allocate(encrypted.length); // Assume decrypted form can't be _longer_
        byte[] tmp   = new byte[8]; // short buf to test chunking
        try (var cipherStream = new CipherInputStream(inStream, cipher)) {
            while (true) {
                int read = cipherStream.read(tmp);
                if (read == -1) {
                    break;
                }
                total.put(tmp, 0, read);
            }
        }
        total.flip();
        byte[] strBytes = new byte[total.remaining()];
        total.get(strBytes);
        return new String(strBytes, StandardCharsets.UTF_8);
    }

    @Test
    void can_create_symmetric_ciphers_from_shared_secret_key_and_public_keys() throws Exception {
        var receiverKeyPair = KeyUtils.generateX25519KeyPair();
        var myShared        = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), KEY_ID_1);

        String terrifyingSecret = "birds are not real D:";
        byte[] encrypted = streamEncryptString(terrifyingSecret, myShared);
        String decrypted = streamDecryptString(encrypted, myShared);
        assertEquals(terrifyingSecret, decrypted);
    }

}
