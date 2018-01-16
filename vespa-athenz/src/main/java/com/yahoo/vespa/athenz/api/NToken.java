// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.util.Objects;

/**
 * Represents an Athenz NToken (principal token)
 *
 * @author bjorncs
 */
public class NToken {

    private final String rawToken;

    public NToken(String rawToken) {
        this.rawToken = rawToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NToken nToken = (NToken) o;
        return Objects.equals(rawToken, nToken.rawToken);
    }

    public String getRawToken() {
        return rawToken;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawToken);
    }

}
