// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.yahoo.athenz.auth.token.PrincipalToken;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzIdentity;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzUser;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.InvalidTokenException;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.NToken;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.ZmsKeystore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzUtils.ZMS_ATHENZ_SERVICE;
import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class NTokenValidatorTest {

    private static final KeyPair TRUSTED_KEY = AthenzTestUtils.generateRsaKeypair();
    private static final KeyPair UNKNOWN_KEY = AthenzTestUtils.generateRsaKeypair();
    private static final AthenzIdentity IDENTITY = AthenzUser.fromUserId(new UserId("myuser"));

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void valid_token_is_accepted() throws NoSuchAlgorithmException, InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createKeystore());
        NToken token = createNToken(IDENTITY, Instant.now(), TRUSTED_KEY.getPrivate(), "0");
        AthenzPrincipal principal = validator.validate(token);
        assertEquals("user.myuser", principal.getIdentity().getFullName());
    }

    @Test
    public void invalid_signature_is_not_accepted() throws InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createKeystore());
        NToken token = createNToken(IDENTITY, Instant.now(), UNKNOWN_KEY.getPrivate(), "0");
        exceptionRule.expect(InvalidTokenException.class);
        exceptionRule.expectMessage("NToken is expired or has invalid signature");
        validator.validate(token);
    }

    @Test
    public void expired_token_is_not_accepted() throws InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createKeystore());
        NToken token = createNToken(IDENTITY, Instant.ofEpochMilli(1234) /*long time ago*/, TRUSTED_KEY.getPrivate(), "0");
        exceptionRule.expect(InvalidTokenException.class);
        exceptionRule.expectMessage("NToken is expired or has invalid signature");
        validator.validate(token);
    }

    @Test
    public void unknown_keyId_is_not_accepted() throws InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createKeystore());
        NToken token = createNToken(IDENTITY, Instant.now(), TRUSTED_KEY.getPrivate(), "unknown-key-id");
        exceptionRule.expect(InvalidTokenException.class);
        exceptionRule.expectMessage("NToken has an unknown keyId");
        validator.validate(token);
    }

    @Test
    public void failing_to_find_key_should_throw_exception() throws InvalidTokenException {
        ZmsKeystore keystore = (athensService, keyId) -> { throw new  RuntimeException(); };
        NTokenValidator validator = new NTokenValidator(keystore);
        NToken token = createNToken(IDENTITY, Instant.now(), TRUSTED_KEY.getPrivate(), "0");
        exceptionRule.expect(InvalidTokenException.class);
        exceptionRule.expectMessage("Failed to retrieve public key");
        validator.validate(token);
    }

    private static ZmsKeystore createKeystore() {
        return (athensService, keyId) ->
            athensService.equals(ZMS_ATHENZ_SERVICE) && keyId.equals("0")
                    ? Optional.of(TRUSTED_KEY.getPublic())
                    : Optional.empty();
    }

    private static NToken createNToken(AthenzIdentity identity, Instant issueTime, PrivateKey privateKey, String keyId) {
        PrincipalToken token = new PrincipalToken.Builder("U1", identity.getDomain().id(), identity.getName())
                .keyId(keyId)
                .salt("1234")
                .host("host")
                .ip("1.2.3.4")
                .issueTime(issueTime.getEpochSecond())
                .expirationWindow(1000)
                .build();
        token.sign(privateKey);
        return new NToken(token.getSignedToken());
    }

}
