// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.conscrypt.OpenSSLProvider;

/**
 * @author bjorncs
 */
class ConscryptProviderHolder {
    private static OpenSSLProvider conscryptProvider;

    synchronized static OpenSSLProvider getInstance() {
        if (conscryptProvider == null) {
            conscryptProvider = new OpenSSLProvider(); // Only load provider when actually used as it depends on JNI
        }
        return conscryptProvider;
    }
}
