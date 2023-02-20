// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.restapi.controller;

import com.yahoo.container.jdisc.HttpRequest;
import com.yahoo.container.jdisc.HttpResponse;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.security.KeyId;
import com.yahoo.security.KeyUtils;
import com.yahoo.security.SharedKeyResealingSession;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.SlimeUtils;

import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.restapi.controller.RequestUtils.requireField;
import static com.yahoo.vespa.hosted.controller.restapi.controller.RequestUtils.toJsonBytes;

/**
 * @author vekterli
 */
class DecryptionTokenResealer {

    private static int checkKeyNameAndExtractVersion(KeyId tokenKeyId, String expectedKeyName) {
        String keyStr = tokenKeyId.asString();
        int versionSepIdx = keyStr.lastIndexOf('.');
        if (versionSepIdx == -1) {
            throw new IllegalArgumentException("Key ID is not of the form 'name.version'");
        }
        String keyName = keyStr.substring(0, versionSepIdx);
        if (!expectedKeyName.equals(keyName)) {
            throw new IllegalArgumentException("Token is not generated for the expected key");
        }
        int keyVersion;
        try {
            keyVersion = Integer.parseInt(keyStr.substring(versionSepIdx + 1));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Key version is not a valid integer");
        }
        if (keyVersion < 0) {
            throw new IllegalArgumentException("Key version is out of range");
        }
        return keyVersion;
    }

    /**
     * Extracts a resealing requests from an <strong>already authenticated</strong> HTTP request
     * and re-seals it towards the requested public key, using the provided private key name to
     * decrypt the token contained in the request.
     *
     * @param request a request with a JSON payload that contains a resealing request.
     * @param privateKeyName The key name used to look up the decryption secret.
     *                       The token must have a matching key name, or the request will be rejected.
     * @param secretStore SecretStore instance that holds the private key. The request will fail otherwise.
     * @return a response with a JSON payload containing a resealing response (any failure will throw).
     */
    static HttpResponse handleResealRequest(HttpRequest request, String privateKeyName, SecretStore secretStore) {
        if (privateKeyName.isEmpty()) {
            throw new IllegalArgumentException("Private key ID is not set");
        }
        byte[] jsonBytes  = toJsonBytes(request.getData());
        var inspector     = SlimeUtils.jsonToSlime(jsonBytes).get();
        var resealRequest = requireField(inspector, "resealRequest", SharedKeyResealingSession.ResealingRequest::fromSerializedString);
        int keyVersion    = checkKeyNameAndExtractVersion(resealRequest.sealedKey().keyId(), privateKeyName);

        var b58EncodedPrivateKey = secretStore.getSecret(privateKeyName, keyVersion);
        if (b58EncodedPrivateKey == null) {
            throw new IllegalArgumentException("Unknown key ID or version");
        }
        var privateKey     = KeyUtils.fromBase58EncodedX25519PrivateKey(b58EncodedPrivateKey);
        var resealResponse = SharedKeyResealingSession.reseal(resealRequest, (keyId) -> Optional.of(privateKey));
        return new ResealedTokenResponse(resealResponse);
    }

}
