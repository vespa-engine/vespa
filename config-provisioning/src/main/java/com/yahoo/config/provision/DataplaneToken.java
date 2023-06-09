// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.List;
import java.util.Objects;

/**
 * Id, fingerprints and check access hashes of a data plane token
 *
 * @author mortent
 */
public class DataplaneToken {

    private final String tokenId;
    private final List<TokenValue> tokenValues;


    public DataplaneToken(String tokenId, List<TokenValue> tokenValues) {
        this.tokenId = tokenId;
        this.tokenValues = List.copyOf(tokenValues);
    }

    public String tokenId() {
        return tokenId;
    }

    public List<TokenValue> tokenValues() {
        return tokenValues;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DataplaneToken that = (DataplaneToken) o;
        return Objects.equals(tokenId, that.tokenId) && Objects.equals(tokenValues, that.tokenValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tokenId, tokenValues);
    }

    @Override
    public String toString() {
        return "DataplaneToken{" +
               "tokenId='" + tokenId + '\'' +
               ", tokenValues=" + tokenValues +
               '}';
    }

    public record TokenValue (String fingerprint, String checkAccessHash){
    }
}
