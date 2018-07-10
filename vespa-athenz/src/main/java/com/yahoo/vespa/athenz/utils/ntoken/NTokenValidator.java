// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils.ntoken;

import com.yahoo.athenz.auth.token.PrincipalToken;
import com.yahoo.log.LogLevel;
import com.yahoo.vespa.athenz.api.AthenzDomain;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.vespa.athenz.utils.AthenzIdentities;

import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Validates the content of an NToken:
 *   1) Verifies that the token is signed by Athenz
 *   2) Verifies that the token is not expired
 *
 * @author bjorncs
 */
public class NTokenValidator {
    // Max allowed skew in token timestamp (only for creation, not expiry timestamp)
    private static final long ALLOWED_TIMESTAMP_OFFSET = Duration.ofMinutes(5).getSeconds();

    private static final Logger log = Logger.getLogger(NTokenValidator.class.getName());
    private final AthenzTruststore truststore;


    public NTokenValidator(AthenzTruststore truststore) {
        this.truststore = truststore;
    }

    public NTokenValidator(Path athenzConfFile) {
        this(new AthenzConfTruststore(athenzConfFile));
    }

    public AthenzPrincipal validate(NToken token) throws InvalidTokenException {
        PrincipalToken principalToken = new PrincipalToken(token.getRawToken());
        String keyId = principalToken.getKeyId();
        String keyService = principalToken.getKeyService();
        PublicKey zmsPublicKey = (keyService == null || keyService.equals("zms") ? truststore.getZmsPublicKey(keyId) : truststore.getZtsPublicKey(keyId))
                .orElseThrow(() -> {
                    String message = "NToken has an unknown keyId: " + keyId;
                    log.log(LogLevel.WARNING, message);
                    return new InvalidTokenException(message);
                });
        validateSignatureAndExpiration(principalToken, zmsPublicKey);
        return new AthenzPrincipal(
                AthenzIdentities.from(
                        new AthenzDomain(principalToken.getDomain()),
                        principalToken.getName()),
                token);
    }

    private static void validateSignatureAndExpiration(PrincipalToken token, PublicKey zmsPublicKey) throws InvalidTokenException {
        StringBuilder errorMessageBuilder = new StringBuilder();
        if (!token.validate(zmsPublicKey, (int) ALLOWED_TIMESTAMP_OFFSET, true, errorMessageBuilder)) {
            String message = "NToken is expired or has invalid signature: " + errorMessageBuilder.toString();
            log.log(LogLevel.WARNING, message);
            throw new InvalidTokenException(message);
        }
    }

    public static class InvalidTokenException extends RuntimeException {
        public InvalidTokenException(String message) {
            super(message);
        }
    }

}
