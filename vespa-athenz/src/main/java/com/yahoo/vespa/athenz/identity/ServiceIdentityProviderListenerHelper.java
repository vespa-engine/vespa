// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.identity;

import com.yahoo.vespa.athenz.api.AthenzService;

import javax.net.ssl.SSLContext;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * A helper class managing {@link ServiceIdentityProvider.Listener} instances for implementations of {@link ServiceIdentityProvider}.
 *
 * @author bjorncs
 */
public class ServiceIdentityProviderListenerHelper {

    private final Set<ServiceIdentityProvider.Listener> listeners = new CopyOnWriteArraySet<>();
    private final AthenzService identity;

    public ServiceIdentityProviderListenerHelper(AthenzService identity) {
        this.identity = identity;
    }

    public void addIdentityListener(ServiceIdentityProvider.Listener listener) {
        listeners.add(listener);
    }

    public void removeIdentityListener(ServiceIdentityProvider.Listener listener) {
        listeners.remove(listener);
    }

    public void onCredentialsUpdate(SSLContext sslContext) {
        listeners.forEach(l -> l.onCredentialsUpdate(sslContext, identity));
    }

    public void clearListeners() {
        listeners.clear();
    }

}
