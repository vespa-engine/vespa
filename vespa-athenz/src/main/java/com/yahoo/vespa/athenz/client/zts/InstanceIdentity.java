// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.client.zts;

import com.yahoo.vespa.athenz.api.NToken;

import java.security.cert.X509Certificate;
import java.util.Optional;

/**
 * The identity of an instance of a launched service.
 *
 * @author bjorncs
 */
public class InstanceIdentity {
    private final X509Certificate certificate;
    private final NToken nToken;

    public InstanceIdentity(X509Certificate certificate) {
        this(certificate, null);
    }

    public InstanceIdentity(X509Certificate certificate, NToken nToken) {
        this.certificate = certificate;
        this.nToken = nToken;
    }

    public X509Certificate certificate() {
        return certificate;
    }

    public Optional<NToken> nToken() {
        return Optional.ofNullable(nToken);
    }
}
