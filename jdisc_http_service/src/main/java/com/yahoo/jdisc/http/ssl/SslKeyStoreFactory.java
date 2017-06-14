// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.ssl;

import java.nio.file.Paths;

/**
 * A factory for SSL key stores.
 *
 * @author bratseth
 */
public interface SslKeyStoreFactory {

    SslKeyStore createKeyStore(ReaderForPath certificateFile, ReaderForPath keyFile);

    SslKeyStore createTrustStore(ReaderForPath certificateFile);

}
