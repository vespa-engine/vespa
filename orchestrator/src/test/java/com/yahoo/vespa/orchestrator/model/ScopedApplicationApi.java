// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.model;

import com.yahoo.vespa.orchestrator.status.ApplicationLock;

/**
 * @author hakon
 */
class ScopedApplicationApi implements AutoCloseable {
    private final ApplicationApi applicationApi;
    private final ApplicationLock lock;

    ScopedApplicationApi(ApplicationApi applicationApi, ApplicationLock lock) {
        this.applicationApi = applicationApi;
        this.lock = lock;
    }

    ApplicationApi applicationApi() {
        return applicationApi;
    }

    @Override
    public void close() {
        lock.close();
    }
}
