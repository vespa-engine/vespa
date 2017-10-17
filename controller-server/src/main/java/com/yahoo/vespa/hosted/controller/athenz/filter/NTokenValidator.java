// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.InvalidTokenException;
import com.yahoo.vespa.hosted.controller.athenz.NToken;
import com.yahoo.vespa.hosted.controller.athenz.ZmsKeystore;

import java.security.PublicKey;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.vespa.hosted.controller.athenz.AthenzUtils.ZMS_ATHENZ_SERVICE;

/**
 * Validates the content of an NToken:
 *   1) Verifies that the token is signed by the sys.auth.zms service (by validating the signature)
 *   2) Verifies that the token is not expired
 *
 * @author bjorncs
 */
class NTokenValidator {

    private static final Logger log = Logger.getLogger(NTokenValidator.class.getName());

    private final ZmsKeystore keystore;

    NTokenValidator(ZmsKeystore keystore) {
        this.keystore = keystore;
    }

    void preloadPublicKeys() {
        keystore.preloadKeys(ZMS_ATHENZ_SERVICE);
    }

    AthenzPrincipal validate(NToken token) throws InvalidTokenException {
        PublicKey zmsPublicKey = getPublicKey(token.getKeyId())
                .orElseThrow(() -> new InvalidTokenException("NToken has an unknown keyId"));
        validateSignatureAndExpiration(token, zmsPublicKey);
        return token.getPrincipal();
    }

    private Optional<PublicKey> getPublicKey(String keyId) throws InvalidTokenException {
        try {
            return keystore.getPublicKey(ZMS_ATHENZ_SERVICE, keyId);
        } catch (Exception e) {
            logDebug(e.getMessage());
            throw new InvalidTokenException("Failed to retrieve public key");
        }
    }

    private static void validateSignatureAndExpiration(NToken token, PublicKey zmsPublicKey) throws InvalidTokenException {
        try {
            token.validateSignatureAndExpiration(zmsPublicKey);
        } catch (InvalidTokenException e) {
            // The underlying error message is not user friendly
            logDebug(e.getMessage());
            throw new InvalidTokenException("NToken is expired or has invalid signature");
        }
    }

    private static void logDebug(String message) {
        log.log(LogLevel.DEBUG, "Failed to validate NToken: " + message);
    }

}
