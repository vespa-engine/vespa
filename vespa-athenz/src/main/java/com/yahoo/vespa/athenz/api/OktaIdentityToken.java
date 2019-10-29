// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.util.Objects;

/**
 * @author bjorncs
 */
public class OktaIdentityToken {

    private final String token;

    public OktaIdentityToken(String token) {
        this.token = token;
    }

    public String token() {
        return token;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        OktaIdentityToken that = (OktaIdentityToken) o;
        return Objects.equals(token, that.token);
    }

    @Override
    public int hashCode() {
        return Objects.hash(token);
    }

    @Override
    public String toString() {
        return "OktaIdentityToken{" +
                "token='" + token + '\'' +
                '}';
    }
}
