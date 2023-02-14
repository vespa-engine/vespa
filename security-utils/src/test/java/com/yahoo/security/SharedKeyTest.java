// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;

import static com.yahoo.security.ArrayUtils.hex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SharedKeyTest {

    private static final KeyId KEY_ID_1 = KeyId.ofString("1");
    private static final KeyId KEY_ID_2 = KeyId.ofString("2");

    @Test
    void sealed_shared_key_uses_enc_and_ciphertext_contents_for_equals_and_hash_code() {
        var tokenStr1 = "2qW20eDfgCxDVTJfLPzihhqV4i1Ma6QrvjdoU24Csf6W0iKbYmezchhxIGeI39WcHYDvbah5tfLoYZ69ofW40zy59Nm91tavFsA";
        var tokenStr2 = "mjA83HYuulZW5SWV8FKz4m3b3m9zU8mTrX9n6iY4wZaA6ZNr8WnBZwOU4KQqhPCORPlzSYk4svlonzPZIb3Bjbqr2ePYKLOpdGhCO";
        var token1a = SealedSharedKey.fromTokenString(tokenStr1);
        var token1b = SealedSharedKey.fromTokenString(tokenStr1);
        var token2a = SealedSharedKey.fromTokenString(tokenStr2);
        var token2b = SealedSharedKey.fromTokenString(tokenStr2);
        assertEquals(token1a, token1a); // trivial
        assertEquals(token1a, token1b); // needs deep compare for array contents
        assertEquals(token1b, token1a);
        assertEquals(token2a, token2b);
        assertNotEquals(token1a, token2a);

        assertEquals(token1a.hashCode(), token1b.hashCode());
        assertEquals(token2a.hashCode(), token2b.hashCode());
        assertNotEquals(token1a.hashCode(), token2a.hashCode()); // ... with a very high probability
    }

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
    void secret_key_can_be_resealed_for_another_receiver() {
        var originalReceiverKp  = KeyUtils.generateX25519KeyPair();
        var secondaryReceiverKp = KeyUtils.generateX25519KeyPair();
        var myShared = SharedKeyGenerator.generateForReceiverPublicKey(originalReceiverKp.getPublic(), KEY_ID_1);
        var theirShared = SharedKeyGenerator.reseal(myShared, secondaryReceiverKp.getPublic(), KEY_ID_2);

        var publicToken = theirShared.sealedSharedKey().toTokenString();
        var theirSealed = SealedSharedKey.fromTokenString(publicToken);
        assertEquals(KEY_ID_2, theirSealed.keyId());
        theirShared = SharedKeyGenerator.fromSealedKey(theirSealed, secondaryReceiverKp.getPrivate());
        // Should be same internal secret key
        assertArrayEquals(myShared.secretKey().getEncoded(), theirShared.secretKey().getEncoded());
    }

    @Test
    void resealed_token_preserves_token_version_of_source_token() {
        var originalPrivate = KeyUtils.fromBase58EncodedX25519PrivateKey("GFg54SaGNCmcSGufZCx68SKLGuAFrASoDeMk3t5AjU6L");
        var v1Token         = "OntP9gRVAjXeZIr4zkYqRJFcnA993v7ZEE7VbcNs1NcR3HdE7Mpwlwi3r3anF1kVa5fn7O1CyeHQpBWpdayUTKkrtyFepG6WJrZdE";

        var originalSealed = SealedSharedKey.fromTokenString(v1Token);
        var originalSecret = SharedKeyGenerator.fromSealedKey(originalSealed, originalPrivate);

        var secondaryReceiverKp = KeyUtils.generateX25519KeyPair();
        var resealedShared = SharedKeyGenerator.reseal(originalSecret, secondaryReceiverKp.getPublic(), KEY_ID_2);

        var theirSealed = SealedSharedKey.fromTokenString(resealedShared.sealedSharedKey().toTokenString());
        assertEquals(1, theirSealed.tokenVersion());
    }

    @Test
    void token_v1_representation_is_stable() throws IOException {
        var receiverPrivate = KeyUtils.fromBase58EncodedX25519PrivateKey("GFg54SaGNCmcSGufZCx68SKLGuAFrASoDeMk3t5AjU6L");
        var receiverPublic  = KeyUtils.fromBase58EncodedX25519PublicKey( "5drrkakYLjYSBpr5Haknh13EiCYL36ndMzK4gTJo6pwh");
        var keyId           = KeyId.ofString("my key ID");

        // V1 token generated for the above receiver public key, with the below expected shared secret (in hex)
        var publicToken = "OntP9gRVAjXeZIr4zkYqRJFcnA993v7ZEE7VbcNs1NcR3HdE7Mpwlwi3r3anF1kVa5fn7O1CyeHQpBWpdayUTKkrtyFepG6WJrZdE";
        var expectedSharedSecret = "1b33b4dcd6a94e5a4a1ee6d208197d01";

        var theirSealed = SealedSharedKey.fromTokenString(publicToken);
        var theirShared = SharedKeyGenerator.fromSealedKey(theirSealed, receiverPrivate);

        assertEquals(1, theirSealed.tokenVersion());
        assertEquals(keyId, theirSealed.keyId());
        assertEquals(expectedSharedSecret, hex(theirShared.secretKey().getEncoded()));

        // Encryption with v1 tokens must use AES-GCM 128
        var plaintext = "it's Bocchi time";
        var expectedCiphertext = "a2ba842b2e0769a4a2948c4236d4ae921f1dd05c2e094dcde9699eeefcc3d7ae";
        byte[] ct = streamEncryptString(plaintext, theirShared);
        assertEquals(expectedCiphertext, hex(ct));

        // Decryption with v1 tokens must use AES-GCM 128
        var decrypted = streamDecryptString(ct, theirShared);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void token_v2_representation_is_stable() throws IOException {
        var receiverPrivate = KeyUtils.fromBase58EncodedX25519PrivateKey("GFg54SaGNCmcSGufZCx68SKLGuAFrASoDeMk3t5AjU6L");
        var receiverPublic  = KeyUtils.fromBase58EncodedX25519PublicKey( "5drrkakYLjYSBpr5Haknh13EiCYL36ndMzK4gTJo6pwh");
        var keyId           = KeyId.ofString("my key ID");

        // V2 token generated for the above receiver public key, with the below expected shared secret (in hex)
        var publicToken = "mjA83HYuulZW5SWV8FKz4m3b3m9zU8mTrX9n6iY4wZaA6ZNr8WnBZwOU4KQqhPCORPlzSYk4svlonzPZIb3Bjbqr2ePYKLOpdGhCO";
        var expectedSharedSecret = "205af82154690fd7b6d56a977563822c";

        var theirSealed = SealedSharedKey.fromTokenString(publicToken);
        var theirShared = SharedKeyGenerator.fromSealedKey(theirSealed, receiverPrivate);

        assertEquals(2, theirSealed.tokenVersion());
        assertEquals(keyId, theirSealed.keyId());
        assertEquals(expectedSharedSecret, hex(theirShared.secretKey().getEncoded()));

        // Encryption with v2 tokens must use ChaCha20-Poly1305
        var plaintext = "it's Bocchi time";
        var expectedCiphertext = "ea19dd0ac3ea6d76dc4e96430b0d5902a21cb3a27fa99490f4dcc391eaf5cec4";
        byte[] ct = streamEncryptString(plaintext, theirShared);
        assertEquals(expectedCiphertext, hex(ct));

        // Decryption with v2 tokens must use ChaCha20-Poly1305
        var decrypted = streamDecryptString(ct, theirShared);
        assertEquals(plaintext, decrypted);
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
        var keyId       = KeyId.ofString("hello key id world");
        var keyPair     = KeyUtils.generateX25519KeyPair();
        var myShared    = SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), keyId);
        var publicToken = myShared.sealedSharedKey().toTokenString();
        var theirShared = SealedSharedKey.fromTokenString(publicToken);
        assertEquals(theirShared.keyId(), keyId);
    }

    @Test
    void key_id_integrity_is_protected_by_aad() {
        var goodId   = KeyId.ofString("my key 1");
        var keyPair  = KeyUtils.generateX25519KeyPair();
        var myShared = SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), goodId);
        var mySealed = myShared.sealedSharedKey();
        var badId    = KeyId.ofString("my key 2");

        var tamperedShared = new SealedSharedKey(SealedSharedKey.CURRENT_TOKEN_VERSION, badId, mySealed.enc(), mySealed.ciphertext());
        // Should not be able to unseal the token since the AAD auth tag won't be correct
        assertThrows(RuntimeException.class, // TODO consider distinct exception class
                     () -> SharedKeyGenerator.fromSealedKey(tamperedShared, keyPair.getPrivate()));
    }

    @Test
    void can_encode_and_decode_largest_possible_key_id() {
        byte[] okIdBytes = new byte[KeyId.MAX_KEY_ID_UTF8_LENGTH];
        Arrays.fill(okIdBytes, (byte)'A');
        var okId     = KeyId.ofBytes(okIdBytes);
        var keyPair  = KeyUtils.generateX25519KeyPair();
        var myShared = SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), okId);
        assertEquals(okId, myShared.sealedSharedKey().keyId());

        var asToken = myShared.sealedSharedKey().toTokenString();
        var decoded = SealedSharedKey.fromTokenString(asToken);
        assertEquals(okId, decoded.keyId());
    }

    // TODO make this test less implementation specific if possible...
    @Test
    void malformed_utf8_key_id_is_rejected_on_parsing() {
        var goodId   = KeyId.ofBytes(new byte[] { (byte)'A' });
        var keyPair  = KeyUtils.generateX25519KeyPair();
        var myShared = SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), goodId);

        // token header is u8 version || u8 key id length || key id bytes ...
        // Since the key ID is only 1 byte long, patch it with a bad UTF-8 value
        byte[] tokenBytes = Base62.codec().decode(myShared.sealedSharedKey().toTokenString());
        tokenBytes[2] = (byte)0xC0; // First part of a 2-byte continuation without trailing byte
        var patchedTokenStr = Base62.codec().encode(tokenBytes);
        assertThrows(IllegalArgumentException.class, () -> SealedSharedKey.fromTokenString(patchedTokenStr));
    }

    static byte[] streamEncryptString(String data, SecretSharedKey secretSharedKey) throws IOException {
        var cipher = secretSharedKey.makeEncryptionCipher();
        var outStream = new ByteArrayOutputStream();
        try (var cipherStream = cipher.wrapOutputStream(outStream)) {
            cipherStream.write(data.getBytes(StandardCharsets.UTF_8));
            cipherStream.flush();
        }
        return outStream.toByteArray();
    }

    static String streamDecryptString(byte[] encrypted, SecretSharedKey secretSharedKey) throws IOException {
        var cipher   = secretSharedKey.makeDecryptionCipher();
        var inStream = new ByteArrayInputStream(encrypted);
        var total    = ByteBuffer.allocate(encrypted.length); // Assume decrypted form can't be _longer_
        byte[] tmp   = new byte[8]; // short buf to test chunking
        try (var cipherStream = cipher.wrapInputStream(inStream)) {
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

    @Test
    void shared_key_can_be_resealed_via_interactive_resealing_session() {
        var originalReceiverKp  = KeyUtils.generateX25519KeyPair();
        var shared = SharedKeyGenerator.generateForReceiverPublicKey(originalReceiverKp.getPublic(), KEY_ID_1);
        var secret = hex(shared.secretKey().getEncoded());

        // Resealing requester side; ask for token to be resealed for ephemeral session public key
        var session = SharedKeyResealingSession.newEphemeralSession();
        var wrappedResealRequest = session.resealingRequestFor(shared.sealedSharedKey());

        // Resealing request handler side; reseal using private key for original token
        var unwrappedResealRequest = SharedKeyResealingSession.ResealingRequest.fromSerializedString(wrappedResealRequest.toSerializedString());
        var wrappedResponse = SharedKeyResealingSession.reseal(unwrappedResealRequest,
                (keyId) -> Optional.ofNullable(keyId.equals(KEY_ID_1) ? originalReceiverKp.getPrivate() : null));

        // Back to resealing requester side
        var unwrappedResponse = SharedKeyResealingSession.ResealingResponse.fromSerializedString(wrappedResponse.toSerializedString());
        var resealed = session.openResealingResponse(unwrappedResponse);

        var resealedSecret = hex(resealed.secretKey().getEncoded());
        assertEquals(secret, resealedSecret);
    }

    // javax.crypto.CipherOutputStream swallows exceptions caused by MAC failures in cipher
    // decryption mode (!) and must therefore _not_ be used for this purpose. This is documented,
    // but still very surprising behavior.
    @Test
    void cipher_output_stream_tag_mismatch_is_not_swallowed() throws Exception {
        var receiverKeyPair = KeyUtils.generateX25519KeyPair();
        var myShared        = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), KEY_ID_1);
        String plaintext = "...hello world?";
        byte[] encrypted = streamEncryptString(plaintext, myShared);
        // Corrupt MAC tag in ciphertext
        encrypted[encrypted.length - 1] ^= 0x80;
        // We don't necessarily know _which_ exception is thrown, but one _should_ be thrown!
        assertThrows(Exception.class, () -> doOutputStreamCipherDecrypt(myShared, encrypted));
        // Also try with corrupted ciphertext (pre MAC tag)
        encrypted[encrypted.length - 1] ^= 0x80; // Flip MAC bit back to correct state
        encrypted[encrypted.length - 17] ^= 0x80; // Pre 128-bit MAC tag
        assertThrows(Exception.class, () -> doOutputStreamCipherDecrypt(myShared, encrypted));
    }

    private static void doOutputStreamCipherDecrypt(SecretSharedKey myShared, byte[] encrypted) throws Exception {
        var cipher = myShared.makeDecryptionCipher();
        var outStream = new ByteArrayOutputStream();
        try (var cipherStream = cipher.wrapOutputStream(outStream)) {
            cipherStream.write(encrypted);
            cipherStream.flush();
        }
    }

}
