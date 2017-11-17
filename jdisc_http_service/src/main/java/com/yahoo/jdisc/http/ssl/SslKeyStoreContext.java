// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import java.security.KeyStore;

/**
 * An interface to update the keystore in JDisc. Any update will trigger a hot reload and new connections will
 * immediately see the new certificate chain.
 *
 * @author bjorncs
 */
public interface SslKeyStoreContext {
    void updateKeyStore(KeyStore keyStore);
    void updateKeyStore(KeyStore keyStore, String password);
    void updateKeyStore(String keyStorePath, String keyStoreType, String keyStorePassword);
}
