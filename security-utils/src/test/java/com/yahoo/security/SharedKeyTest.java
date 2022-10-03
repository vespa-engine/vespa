// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SharedKeyTest {

    @Test
    void generated_secret_key_is_256_bit_aes() {
        var receiverKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        var shared = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), 1);
        var secret = shared.secretKey();
        assertEquals(secret.getAlgorithm(), "AES");
        assertEquals(secret.getEncoded().length, 32);
    }

    @Test
    void sealed_shared_key_can_be_exchanged_via_token_and_computes_identical_secret_key_at_receiver() {
        var receiverKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);

        var myShared    = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), 1);
        var publicToken = myShared.sealedSharedKey().toTokenString();

        var theirSealed = SealedSharedKey.fromTokenString(publicToken);
        var theirShared = SharedKeyGenerator.fromSealedKey(theirSealed, receiverKeyPair);

        assertArrayEquals(myShared.secretKey().getEncoded(), theirShared.secretKey().getEncoded());
    }

    @Test
    void token_v1_representation_is_stable() {
        var receiverPrivate = KeyUtils.fromPemEncodedPrivateKey(
            """
            -----BEGIN EC PRIVATE KEY-----
            MHcCAQEEIO+CkAccoU9jPjX64mwU54Ar9DNZSLBBTYRSINerSW8EoAoGCCqGSM49
            AwEHoUQDQgAE3FA2VSuOn0vVhtQgNe13H2UE0Vx5A41demyX8nkHTCO4BDXSEPca
            vejY7YaVcNSvFUbzDvia51X4pxbr1pe56g==
            -----END EC PRIVATE KEY-----
            """);
        var receiverPublic = KeyUtils.fromPemEncodedPublicKey(
            """
            -----BEGIN PUBLIC KEY-----
            MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE3FA2VSuOn0vVhtQgNe13H2UE0Vx5
            A41demyX8nkHTCO4BDXSEPcavejY7YaVcNSvFUbzDvia51X4pxbr1pe56g==
            -----END PUBLIC KEY-----
            """
        );
        var receiverKeyPair = new KeyPair(receiverPublic, receiverPrivate);

        // Token generated for the above receiver public key, with the below expected shared secret (in hex)
        var publicToken = "AQAAAQTAVVthmrVAbMgKt8hBc4xColmDmEeEAyPD-ZcPlRmeId9wBaZTTctwV3pwT3FyV0UtvX_7zrRId" +
                          "3mNxvaru0tvFucd7mYY73Hi9d3j8qS6pN0bTTb1sw_dKYrR_0BXhEFE_py8uZnNxvV8-wHtBIAVXBk_4Q";
        var expectedSharedSecret = "b1aded9dc19593baa08fe64f916dcbaf3328ec666d2e0c81b1f6f8af9794187b";

        var theirSealed = SealedSharedKey.fromTokenString(publicToken);
        var theirShared = SharedKeyGenerator.fromSealedKey(theirSealed, receiverKeyPair);

        assertEquals(expectedSharedSecret, Hex.toHexString(theirShared.secretKey().getEncoded()));
    }

    @Test
    void unrelated_private_key_cannot_decrypt_shared_secret_key() {
        var aliceKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        var eveKeyPair   = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        var bobShared    = SharedKeyGenerator.generateForReceiverPublicKey(aliceKeyPair.getPublic(), 1);
        assertThrows(IllegalArgumentException.class, // TODO consider distinct exception class
                     () -> SharedKeyGenerator.fromSealedKey(bobShared.sealedSharedKey(), eveKeyPair));
    }

    @Test
    void token_carries_key_id_as_metadata() {
        int keyId       = 12345;
        var keyPair     = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        var myShared    = SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), keyId);
        var publicToken = myShared.sealedSharedKey().toTokenString();
        var theirShared = SealedSharedKey.fromTokenString(publicToken);
        assertEquals(theirShared.keyId(), keyId);
    }

    static byte[] streamEncryptString(String data, SecretSharedKey secretSharedKey) throws IOException {
        var cipher = SharedKeyGenerator.makeAes256GcmEncryptionCipher(secretSharedKey);
        var outStream = new ByteArrayOutputStream();
        try (var cipherStream = new CipherOutputStream(outStream, cipher)) {
            cipherStream.write(data.getBytes(StandardCharsets.UTF_8));
            cipherStream.flush();
        }
        return outStream.toByteArray();
    }

    static String streamDecryptString(byte[] encrypted, SecretSharedKey secretSharedKey) throws IOException {
        var cipher   = SharedKeyGenerator.makeAes256GcmDecryptionCipher(secretSharedKey);
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
        var receiverKeyPair = KeyUtils.generateKeypair(KeyAlgorithm.EC);
        var myShared        = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), 1);

        String terrifyingSecret = "birds are not real D:";
        byte[] encrypted = streamEncryptString(terrifyingSecret, myShared);
        String decrypted = streamDecryptString(encrypted, myShared);
        assertEquals(terrifyingSecret, decrypted);
    }

}
