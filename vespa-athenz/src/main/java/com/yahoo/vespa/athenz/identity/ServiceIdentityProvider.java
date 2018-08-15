// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import com.yahoo.container.jdisc.athenz.AthenzIdentityProvider;
import com.yahoo.vespa.athenz.api.AthenzService;

import javax.net.ssl.SSLContext;

/**
 * A interface for types that provides a service identity.
 * Some similarities to {@link AthenzIdentityProvider}, but this type is not public api and intended for internal use.
 *
 * @author bjorncs
 */
public interface ServiceIdentityProvider {
    AthenzService identity();
    SSLContext getIdentitySslContext();
    void addIdentityListener(Listener listener);
    void removeIdentityListener(Listener listener);

    interface Listener {
        void onCredentialsUpdate(SSLContext sslContext, AthenzService identity);
    }
}
