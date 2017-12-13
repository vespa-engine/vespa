// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import java.security.KeyStore;

/**
 * An interface to update the truststore in JDisc. Any update will trigger a hot reload and new connections will
 * authenticated using the update truststore.
 *
 * @author bjorncs
 */
public interface SslTrustStoreContext {
    void updateTrustStore(KeyStore trustStore);
    void updateTrustStore(KeyStore trustStore, String password);
    void updateTrustStore(String trustStorePath, String trustStoreType, String trustStorePassword);
}
