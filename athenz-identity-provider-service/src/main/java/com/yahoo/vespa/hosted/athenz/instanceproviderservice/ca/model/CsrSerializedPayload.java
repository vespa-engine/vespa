// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains PEM formatted Certificate Signing Request (CSR)
 *
 * @author freva
 */
public class CsrSerializedPayload {

    @JsonProperty("csr") public final String csr;

    @JsonCreator
    public CsrSerializedPayload(@JsonProperty("csr") String csr) {
        this.csr = csr;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CsrSerializedPayload that = (CsrSerializedPayload) o;

        return csr.equals(that.csr);
    }

    @Override
    public int hashCode() {
        return csr.hashCode();
    }

    @Override
    public String toString() {
        return "CsrSerializedPayload{" +
                "csr='" + csr + '\'' +
                '}';
    }
}
