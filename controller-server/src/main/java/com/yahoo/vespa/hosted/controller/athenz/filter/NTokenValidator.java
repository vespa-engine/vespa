// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.yahoo.athenz.auth.token.PrincipalToken;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.InvalidTokenException;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsKeystore;

import java.security.PublicKey;
import java.time.Duration;
import java.util.Optional;
import java.util.logging.Logger;

import static com.yahoo.vespa.athenz.utils.AthenzIdentities.ZMS_ATHENZ_SERVICE;


/**
 * Validates the content of an NToken:
 *   1) Verifies that the token is signed by the sys.auth.zms service (by validating the signature)
 *   2) Verifies that the token is not expired
 *
 * @author bjorncs
 */
// TODO Move to vespa-athenz
class NTokenValidator {

        // Max allowed skew in token timestamp (only for creation, not expiry timestamp)
    private static final long ALLOWED_TIMESTAMP_OFFSET = Duration.ofMinutes(5).getSeconds();

    private static final Logger log = Logger.getLogger(NTokenValidator.class.getName());

    private final ZmsKeystore keystore;

    NTokenValidator(ZmsKeystore keystore) {
        this.keystore = keystore;
    }

    void preloadPublicKeys() {
        keystore.preloadKeys(ZMS_ATHENZ_SERVICE);
    }

    AthenzPrincipal validate(NToken token) throws InvalidTokenException {
        PrincipalToken principalToken = new PrincipalToken(token.getRawToken());
        PublicKey zmsPublicKey = getPublicKey(principalToken.getKeyId())
                .orElseThrow(() -> new InvalidTokenException("NToken has an unknown keyId"));
        validateSignatureAndExpiration(principalToken, zmsPublicKey);
        return new AthenzPrincipal(
                AthenzIdentities.from(
                        new AthenzDomain(principalToken.getDomain()),
                        principalToken.getName()),
                token);
    }

    private Optional<PublicKey> getPublicKey(String keyId) throws InvalidTokenException {
        try {
            return keystore.getPublicKey(ZMS_ATHENZ_SERVICE, keyId);
        } catch (Exception e) {
            logDebug(e.getMessage());
            throw new InvalidTokenException("Failed to retrieve public key");
        }
    }

    private static void validateSignatureAndExpiration(PrincipalToken token,
                                                       PublicKey zmsPublicKey) throws InvalidTokenException {
        StringBuilder errorMessageBuilder = new StringBuilder();
        if (!token.validate(zmsPublicKey, (int) ALLOWED_TIMESTAMP_OFFSET, true, errorMessageBuilder)) {
            String message = "NToken is expired or has invalid signature: " + errorMessageBuilder.toString();
            logDebug(message);
            throw new InvalidTokenException(message);
        }
    }

    private static void logDebug(String message) {
        log.log(LogLevel.DEBUG, "Failed to validate NToken: " + message);
    }

}
