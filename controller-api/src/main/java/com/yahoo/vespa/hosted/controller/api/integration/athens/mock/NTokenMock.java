// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens.mock;

import com.yahoo.vespa.hosted.controller.api.identifiers.AthensDomain;
import com.yahoo.vespa.hosted.controller.api.identifiers.UserId;
import com.yahoo.vespa.hosted.controller.api.integration.athens.AthensPrincipal;
import com.yahoo.vespa.hosted.controller.api.integration.athens.InvalidTokenException;
import com.yahoo.vespa.hosted.controller.api.integration.athens.NToken;

import java.security.PublicKey;
import java.util.Objects;

/**
 * @author mpolden
 */
public class NTokenMock implements NToken {

    private static final AthensDomain domain = new AthensDomain("test");
    private static final UserId userId = new UserId("user");

    private final String rawToken;

    public NTokenMock(String rawToken) {
        this.rawToken = rawToken;
    }

    @Override
    public AthensPrincipal getPrincipal() {
        return new AthensPrincipal(domain, userId);
    }

    @Override
    public UserId getUser() {
        return userId;
    }

    @Override
    public AthensDomain getDomain() {
        return domain;
    }

    @Override
    public String getToken() {
        return "test-token";
    }

    @Override
    public String getKeyId() {
        return "test-key";
    }

    @Override
    public void validateSignatureAndExpiration(PublicKey publicKey) throws InvalidTokenException {
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof NTokenMock)) return false;
        NTokenMock that = (NTokenMock) o;
        return Objects.equals(rawToken, that.rawToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawToken);
    }
}
