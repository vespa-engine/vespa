// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.athenz.filter;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthenzDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.athenz.AthenzPrincipal;
import com.yahoo.vespa.hosted.controller.athenz.InvalidTokenException;
import com.yahoo.vespa.hosted.controller.athenz.NToken;
import com.yahoo.vespa.hosted.controller.athenz.ZmsKeystore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;

import static com.yahoo.vespa.hosted.controller.athenz.AthenzUtils.ZMS_ATHENZ_SERVICE;
import static org.junit.Assert.assertEquals;

/**
 * @author bjorncs
 */
public class NTokenValidatorTest {

    private static final KeyPair TRUSTED_KEY = AthenzTestUtils.generateRsaKeypair();
    private static final KeyPair UNKNOWN_KEY = AthenzTestUtils.generateRsaKeypair();
    private static final AthenzPrincipal PRINCIPAL = new AthenzPrincipal(new AthenzDomain("yby"), new UserId("user"));

    @Rule
    public ExpectedException exceptionRule = ExpectedException.none();

    @Test
    public void valid_token_is_accepted() throws NoSuchAlgorithmException, InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createKeystore());
        NToken token = createNToken(PRINCIPAL, System.currentTimeMillis(), TRUSTED_KEY, "0");
        AthenzPrincipal principal = validator.validate(token);
        assertEquals("yby.user", principal.toYRN());
    }

    @Test
    public void invalid_signature_is_not_accepted() throws InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createKeystore());
        NToken token = createNToken(PRINCIPAL, System.currentTimeMillis(), UNKNOWN_KEY, "0");
        exceptionRule.expect(InvalidTokenException.class);
        exceptionRule.expectMessage("NToken is expired or has invalid signature");
        validator.validate(token);
    }

    @Test
    public void expired_token_is_not_accepted() throws InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createKeystore());
        NToken token = createNToken(PRINCIPAL, 1234 /*long time ago*/, TRUSTED_KEY, "0");
        exceptionRule.expect(InvalidTokenException.class);
        exceptionRule.expectMessage("NToken is expired or has invalid signature");
        validator.validate(token);
    }

    @Test
    public void unknown_keyId_is_not_accepted() throws InvalidTokenException {
        NTokenValidator validator = new NTokenValidator(createKeystore());
        NToken token = createNToken(PRINCIPAL, System.currentTimeMillis(), TRUSTED_KEY, "unknown-key-id");
        exceptionRule.expect(InvalidTokenException.class);
        exceptionRule.expectMessage("NToken has an unknown keyId");
        validator.validate(token);
    }

    @Test
    public void failing_to_find_key_should_throw_exception() throws InvalidTokenException {
        ZmsKeystore keystore = (athensService, keyId) -> { throw new  RuntimeException(); };
        NTokenValidator validator = new NTokenValidator(keystore);
        NToken token = createNToken(PRINCIPAL, System.currentTimeMillis(), TRUSTED_KEY, "0");
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

    private static NToken createNToken(AthenzPrincipal principal, long issueTime, KeyPair keyPair, String keyId) {
        return new NToken.Builder("U1", principal, keyPair.getPrivate(), keyId)
                .salt("1234")
                .hostname("host")
                .ip("1.2.3.4")
                .issueTime(issueTime / 1000)
                .expirationWindow(1000)
                .build();
    }

}
