// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.api;

import java.util.Objects;

/**
 * Represents an Athenz ZToken (role token)
 *
 * @author bjorncs
 */
public class ZToken {

    private final String rawToken;

    public ZToken(String rawToken) {
        this.rawToken = rawToken;
    }

    public String getRawToken() {
        return rawToken;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ZToken zToken = (ZToken) o;
        return Objects.equals(rawToken, zToken.rawToken);
    }

    @Override
    public int hashCode() {
        return Objects.hash(rawToken);
    }

}
