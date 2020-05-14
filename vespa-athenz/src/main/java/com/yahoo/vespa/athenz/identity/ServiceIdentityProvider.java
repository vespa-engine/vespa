// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.security.X509CertificateWithKey;
import com.yahoo.vespa.athenz.api.AthenzIdentity;

import javax.net.ssl.SSLContext;
import java.nio.file.Path;

/**
 * A interface for types that provides the Athenz service identity (SIA) from the environment.
 * Some similarities to {@link AthenzIdentityProvider}, but this type is not public API and intended for internal use.
 *
 * @author bjorncs
 */
public interface ServiceIdentityProvider {
    AthenzIdentity identity();
    SSLContext getIdentitySslContext();
    X509CertificateWithKey getIdentityCertificateWithKey();
    Path certificatePath();
    Path privateKeyPath();
}
