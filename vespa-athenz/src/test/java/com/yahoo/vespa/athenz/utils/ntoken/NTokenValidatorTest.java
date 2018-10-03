// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.utils.ntoken;

import com.yahoo.athenz.auth.token.PrincipalToken;
import com.yahoo.vespa.athenz.api.AthenzIdentity;
import com.yahoo.vespa.athenz.api.AthenzPrincipal;
import com.yahoo.vespa.athenz.api.AthenzUser;
import com.yahoo.vespa.athenz.api.NToken;
import com.yahoo.security.KeyAlgorithm;
import com.yahoo.security.KeyUtils;
import com.yahoo.vespa.athenz.utils.ntoken.NTokenValidator.InvalidTokenException;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.Optional;

import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class NTokenValidatorTest {

    private static final KeyPair TRUSTED_KEY = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
    private static final KeyPair UNKNOWN_KEY = KeyUtils.generateKeypair(KeyAlgorithm.RSA);
    private static final AthenzIdentity IDENTITY = AthenzUser.fromUserId("myuser");

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void valid_token_is_accepted() throws InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createTruststore());
        NToken token = createNToken(IDENTITY, Instant.now(), TRUSTED_KEY.getPrivate(), "0");
        AthenzPrincipal principal = validator.validate(token);
        assertEquals("user.myuser", principal.getIdentity().getFullName());
    }

    @Test
    public void invalid_signature_is_not_accepted() throws InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createTruststore());
        NToken token = createNToken(IDENTITY, Instant.now(), UNKNOWN_KEY.getPrivate(), "0");
        exceptionRule.expect(InvalidTokenException.class);
        exceptionRule.expectMessage("NToken is expired or has invalid signature");
        validator.validate(token);
    }

    @Test
    public void expired_token_is_not_accepted() throws InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createTruststore());
        NToken token = createNToken(IDENTITY, Instant.ofEpochMilli(1234) /*long time ago*/, TRUSTED_KEY.getPrivate(), "0");
        exceptionRule.expect(InvalidTokenException.class);
        exceptionRule.expectMessage("NToken is expired or has invalid signature");
        validator.validate(token);
    }

    @Test
    public void unknown_keyId_is_not_accepted() throws InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createTruststore());
        NToken token = createNToken(IDENTITY, Instant.now(), TRUSTED_KEY.getPrivate(), "unknown-key-id");
        exceptionRule.expect(InvalidTokenException.class);
        exceptionRule.expectMessage("NToken has an unknown keyId");
        validator.validate(token);
    }

    private static AthenzTruststore createTruststore() {
        return new AthenzTruststore() {
            @Override
            public Optional<PublicKey> getZmsPublicKey(String keyId) {
                return keyId.equals("0") ? Optional.of(TRUSTED_KEY.getPublic()) : Optional.empty();
            }

            @Override
            public Optional<PublicKey> getZtsPublicKey(String keyId) {
                return Optional.empty();
            }
        };
    }

    private static NToken createNToken(AthenzIdentity identity, Instant issueTime, PrivateKey privateKey, String keyId) {
        PrincipalToken token = new PrincipalToken.Builder("U1", identity.getDomain().getName(), identity.getName())
                .keyId(keyId)
                .salt("1234")
                .host("host")
                .ip("1.2.3.4")
                .keyService("zms")
                .issueTime(issueTime.getEpochSecond())
                .expirationWindow(1000)
                .build();
        token.sign(privateKey);
        return new NToken(token.getSignedToken());
    }

}
