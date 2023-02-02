// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.util.Arrays;

import java.nio.ByteBuffer;
import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.interfaces.XECPublicKey;
import java.util.Optional;

/**
 * <p>Delegated resealing protocol for getting access to a shared secret key of a token
 * whose private key we do not possess.</p>
 *
 * <p>The primary benefit of the interactive resealing protocol is that none of the data
 * exchanged can reveal anything about the underlying sealed secret itself.</p>
 *
 * <p>Note that neither resealing requests nor responses are authenticated (this is a property
 * inherited from the sealed shared key tokens themselves). It is assumed that an attacker
 * can <em>observe</em> all requests and responses in transit, but cannot modify them.</p>
 *
 * <h2>Protocol details</h2>
 *
 * <p>Decryptor (requester):</p>
 * <ol>
 *   <li>Create a resealing session instance that maintains an ephemeral X25519 key pair that
 *       is valid only for the lifetime of the session.</li>
 *   <li>Create a resealing request for a token <em>T</em>. The session emits a Base62-encoded
 *       binary representation of the tuple <em>&lt;ephemeral public key, T&gt;</em>.</li>
 *   <li>Send the request to the private key holder. The session must be kept alive until the
 *       response is received, or the ephemeral private key associated with the public key will
 *       be irrevocably lost.</li>
 * </ol>
 * <p>Private key holder (re-sealer):</p>
 * <ol>
 *   <li>Decode Base62-encoded request into tuple <em>&lt;ephemeral public key, T&gt;</em>.</li>
 *   <li>Look up the correct private key from the key ID contained in token <em>T</em>.</li>
 *   <li>Reseal token <em>T</em> for the requested ephemeral public key using the correct private key.</li>
 *   <li>Return resealed token <em>T<sub>R</sub></em> to requester.</li>
 * </ol>
 * <p>Decryptor (requester):</p>
 * <ol>
 *   <li>Decrypt token <em>T<sub>R</sub></em> using ephemeral private key.</li>
 *   <li>Use secret key in token to decrypt the payload protected by original token <em>T</em>.</li>
 * </ol>
 *
 * @author vekterli
 */
public class SharedKeyResealingSession {

    private final KeyPair ephemeralKeyPair;

    SharedKeyResealingSession(KeyPair ephemeralKeyPair) {
        this.ephemeralKeyPair = ephemeralKeyPair;
    }

    public static SharedKeyResealingSession newEphemeralSession() {
        return new SharedKeyResealingSession(KeyUtils.generateX25519KeyPair());
    }

    @FunctionalInterface
    public interface PrivateKeyProvider {
        Optional<PrivateKey> privateKeyForId(KeyId id);
    }

    public record ResealingRequest(XECPublicKey ephemeralPubKey, SealedSharedKey sealedKey) {

        private static final byte[] HEADER_BYTES = new byte[] {'R','S'};
        private static final byte CURRENT_VERSION = 1;

        public String toSerializedString() {
            byte[] pubKeyBytes = KeyUtils.toRawX25519PublicKeyBytes(ephemeralPubKey);
            byte[] tokenBytes  = sealedKey.toSerializedBytes();

            ByteBuffer encoded = ByteBuffer.allocate(HEADER_BYTES.length + 1 + 1 + pubKeyBytes.length + tokenBytes.length);
            encoded.put(HEADER_BYTES);
            encoded.put(CURRENT_VERSION);
            encoded.put((byte)pubKeyBytes.length);
            encoded.put(pubKeyBytes);
            encoded.put(tokenBytes);
            encoded.flip();

            byte[] encBytes = new byte[encoded.remaining()];
            encoded.get(encBytes);
            return Base62.codec().encode(encBytes);
        }

        public static ResealingRequest fromSerializedString(String request) {
            verifyInputStringNotTooLarge(request);
            byte[] rawBytes = Base62.codec().decode(request);
            if (rawBytes.length < HEADER_BYTES.length + 2) {
                throw new IllegalArgumentException("Resealing request too short to contain a header and key length");
            }
            ByteBuffer decoded = ByteBuffer.wrap(rawBytes);
            byte[] header = new byte[2];
            decoded.get(header);
            if (!Arrays.areEqual(header, HEADER_BYTES)) {
                throw new IllegalArgumentException("No resealing request header found");
            }
            byte version = decoded.get();
            if (version != CURRENT_VERSION) {
                throw new IllegalArgumentException("Unsupported version in resealing request header");
            }
            int pubKeyLen = Byte.toUnsignedInt(decoded.get());
            byte[] pubKeyBytes = new byte[pubKeyLen];
            decoded.get(pubKeyBytes);

            byte[] rawTokenBytes = new byte[decoded.remaining()];
            decoded.get(rawTokenBytes);

            return new ResealingRequest(KeyUtils.fromRawX25519PublicKey(pubKeyBytes),
                                        SealedSharedKey.fromSerializedBytes(rawTokenBytes));
        }

        private static void verifyInputStringNotTooLarge(String tokenString) {
            if (tokenString.length() > SealedSharedKey.MAX_TOKEN_STRING_LENGTH + 64) {
                throw new IllegalArgumentException("String is too long to possibly be a valid resealing request");
            }
        }

    }

    public record ResealingResponse(SealedSharedKey resealedKey) {

        public String toSerializedString() {
            return resealedKey.toTokenString();
        }

        public static ResealingResponse fromSerializedString(String response) {
            return new ResealingResponse(SealedSharedKey.fromTokenString(response));
        }

    }

    public ResealingRequest resealingRequestFor(SealedSharedKey sealedSharedKey) {
        return new ResealingRequest((XECPublicKey) ephemeralKeyPair.getPublic(), sealedSharedKey);
    }

    public static ResealingResponse reseal(ResealingRequest request, PrivateKeyProvider privateKeyProvider) {
        var privKey = privateKeyProvider.privateKeyForId(request.sealedKey.keyId()).orElseThrow(
                () -> new IllegalArgumentException("Could not find a private key for key ID '%s'".formatted(request.sealedKey.keyId())));

        var secretShared = SharedKeyGenerator.fromSealedKey(request.sealedKey, privKey);
        var resealed = SharedKeyGenerator.reseal(secretShared, request.ephemeralPubKey, KeyId.ofString("resealed-token")); // TODO key id

        return new ResealingResponse(resealed.sealedSharedKey());
    }


    public SecretSharedKey openResealingResponse(ResealingResponse response) {
        return SharedKeyGenerator.fromSealedKey(response.resealedKey, ephemeralKeyPair.getPrivate());
    }

}
