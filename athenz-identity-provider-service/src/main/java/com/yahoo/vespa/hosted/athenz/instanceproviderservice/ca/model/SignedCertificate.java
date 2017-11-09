// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice.ca.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Contains PEM formatted signed certificate
 *
 * @author freva
 */
public class SignedCertificate {

    @JsonProperty("certificate") public final String certificate;

    @JsonCreator
    public SignedCertificate(@JsonProperty("certificate") String certificate) {
        this.certificate = certificate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SignedCertificate that = (SignedCertificate) o;

        return certificate.equals(that.certificate);
    }

    @Override
    public int hashCode() {
        return certificate.hashCode();
    }

    @Override
    public String toString() {
        return "SignedCertificate{" +
                "certificate='" + certificate + '\'' +
                '}';
    }
}
