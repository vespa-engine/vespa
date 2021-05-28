// Copyright 2021 Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.support.access;

import java.security.cert.X509Certificate;
import java.util.Objects;

public class SupportAccessGrant {
    private final String requestor;
    private final X509Certificate certificate;

    public SupportAccessGrant(String requestor, X509Certificate certificate) {
        this.requestor = Objects.requireNonNull(requestor);
        this.certificate = Objects.requireNonNull(certificate);
    }

    public String requestor() {
        return requestor;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupportAccessGrant that = (SupportAccessGrant) o;
        return requestor.equals(that.requestor) && certificate.equals(that.certificate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requestor, certificate);
    }
}
