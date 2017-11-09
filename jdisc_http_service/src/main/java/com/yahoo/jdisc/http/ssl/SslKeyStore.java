// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import java.security.KeyStore;

/**
 *
 * @author bjorncs
 */
public interface SslKeyStore {
    KeyStore loadJavaKeyStore() throws Exception;
}
