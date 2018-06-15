// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

import java.security.cert.X509Certificate;
import java.util.List;

/**
 * The identity of a service
 *
 * @author bjorncs
 */
public class Identity {

    private final X509Certificate certificate;
    private final List<X509Certificate> caCertificates;

    public Identity(X509Certificate certificate, List<X509Certificate> caCertificates) {
        this.certificate = certificate;
        this.caCertificates = caCertificates;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    public List<X509Certificate> caCertificates() {
        return caCertificates;
    }
}
