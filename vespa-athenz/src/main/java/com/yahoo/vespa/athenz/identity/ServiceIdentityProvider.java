// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    /**
     *
     * @return The Athenz identity of the environment
     */
    AthenzIdentity identity();

    /**
     * @return {@link SSLContext} that is automatically updated.  The trust store is guaranteed to include
     * the Athenz CA cert that signed this identity.
     */
    SSLContext getIdentitySslContext();

    /**
     * @return {@link SSLContext} that is automatically updated.  The trust store is guaranteed to include
     * publicly issued CA certificates, e.g. DigiCert, and may not include the CA cert used to sign this identity.
     */
    default SSLContext getIdentitySslContextForPublicPeer() { throw new UnsupportedOperationException("Not yet implemented"); }

    /**
     * @return Current certificate and private key. Unlike {@link #getIdentitySslContext()} underlying credentials are not automatically updated.
     */
    X509CertificateWithKey getIdentityCertificateWithKey();

    /**
     * @return Path to X.509 certificate in PEM format
     */
    Path certificatePath();

    /**
     * @return Path to private key in PEM format
     */
    Path privateKeyPath();

}
