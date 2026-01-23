// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http.server.jetty;

import com.yahoo.jdisc.test.ServerProviderConformanceTest;

/**
 * Helper class for implementation hooks required by {@link ServerProviderConformanceTest}.
 * See {@link ServerProviderConformanceTest.ConformanceException#markAsProcessed()} for details.
 *
 * @author bjorncs
 */
class HttpServerConformanceTestHooks {
    private HttpServerConformanceTestHooks() {}

    static void markAsProcessed(Throwable t) {
        if (t instanceof ServerProviderConformanceTest.ConformanceException ce) {
            ce.markAsProcessed();
        }
    }
}
