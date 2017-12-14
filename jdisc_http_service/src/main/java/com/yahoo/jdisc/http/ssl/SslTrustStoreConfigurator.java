// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

/**
 * An interface for an component that can configure an {@link SslTrustStoreContext}. The implementor can assume that
 * the {@link SslTrustStoreContext} instance is thread-safe and be updated at any time
 * during and after the call to{@link #configure(SslTrustStoreContext)}.
 * Modifying the {@link SslKeyStoreContext} instance will trigger a hot reload of the truststore in JDisc.
 *
 * @author bjorncs
 */
public interface SslTrustStoreConfigurator {
    void configure(SslTrustStoreContext context);
}
