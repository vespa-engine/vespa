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

import static com.yahoo.security.ArrayUtils.hex;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SharedKeyTest {

    @Test
    void generated_secret_key_is_128_bit_aes() {
        var receiverKeyPair = KeyUtils.generateX25519KeyPair();
        var shared = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), 1);
        var secret = shared.secretKey();
        assertEquals(secret.getAlgorithm(), "AES");
        assertEquals(secret.getEncoded().length, 16);
    }

    @Test
    void sealed_shared_key_can_be_exchanged_via_token_and_computes_identical_secret_key_at_receiver() {
        var receiverKeyPair = KeyUtils.generateX25519KeyPair();

        var myShared    = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), 1);
        var publicToken = myShared.sealedSharedKey().toTokenString();

        var theirSealed = SealedSharedKey.fromTokenString(publicToken);
        var theirShared = SharedKeyGenerator.fromSealedKey(theirSealed, receiverKeyPair.getPrivate());

        assertArrayEquals(myShared.secretKey().getEncoded(), theirShared.secretKey().getEncoded());
    }

    @Test
    void token_v1_representation_is_stable() {
        var receiverPrivate = KeyUtils.fromBase64EncodedX25519PrivateKey("4qGcntygFn_a3uqeBa1PbDlygQ-cpOuNznTPIz9ftWE");
        var receiverPublic  = KeyUtils.fromBase64EncodedX25519PublicKey( "ROAH_S862tNMpbJ49lu1dPXFCPHFIXZK30pSrMZEmEg");

        // Token generated for the above receiver public key, with the below expected shared secret (in hex)
        var publicToken = "AQAAAQAgwyxd7bFNQB_2LdL3bw-xFlvrxXhs7WWNVCKZ4EFeNVtu42JMwM74bMN4E46v6mYcfQNPzcMGaP22Wl2cTnji0A";
        var expectedSharedSecret = "85ac3c7c3a930a19334cb73e02779733";

        var theirSealed = SealedSharedKey.fromTokenString(publicToken);
        var theirShared = SharedKeyGenerator.fromSealedKey(theirSealed, receiverPrivate);

        assertEquals(1, theirSealed.keyId());
        assertEquals(expectedSharedSecret, hex(theirShared.secretKey().getEncoded()));
    }

    @Test
    void unrelated_private_key_cannot_decrypt_shared_secret_key() {
        var aliceKeyPair = KeyUtils.generateX25519KeyPair();
        var eveKeyPair   = KeyUtils.generateX25519KeyPair();
        var bobShared    = SharedKeyGenerator.generateForReceiverPublicKey(aliceKeyPair.getPublic(), 1);
        assertThrows(RuntimeException.class, // TODO consider distinct exception class
                     () -> SharedKeyGenerator.fromSealedKey(bobShared.sealedSharedKey(), eveKeyPair.getPrivate()));
    }

    @Test
    void token_carries_key_id_as_metadata() {
        int keyId       = 12345;
        var keyPair     = KeyUtils.generateX25519KeyPair();
        var myShared    = SharedKeyGenerator.generateForReceiverPublicKey(keyPair.getPublic(), keyId);
        var publicToken = myShared.sealedSharedKey().toTokenString();
        var theirShared = SealedSharedKey.fromTokenString(publicToken);
        assertEquals(theirShared.keyId(), keyId);
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
        var myShared        = SharedKeyGenerator.generateForReceiverPublicKey(receiverKeyPair.getPublic(), 1);

        String terrifyingSecret = "birds are not real D:";
        byte[] encrypted = streamEncryptString(terrifyingSecret, myShared);
        String decrypted = streamDecryptString(encrypted, myShared);
        assertEquals(terrifyingSecret, decrypted);
    }

}
