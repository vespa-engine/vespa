// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import javax.net.ssl.SSLContext;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;

/**
 * @author bjorncs
 */
public enum JsseProvider {
    DEFAULT {
        @Override
        SSLContext createSSLContext() throws NoSuchAlgorithmException {
            return SSLContext.getInstance("TLSv1.2"); // TODO Vespa 7: Use TLSv1.3 on Java 11 to allow TLSv1.3 support
        }
    },
    CONSCRYPT {
        @Override
        SSLContext createSSLContext() throws GeneralSecurityException {
            return SSLContext.getInstance("TLSv1.3", ConscryptProviderHolder.getInstance());
        }
    };

    abstract SSLContext createSSLContext() throws GeneralSecurityException;

}
