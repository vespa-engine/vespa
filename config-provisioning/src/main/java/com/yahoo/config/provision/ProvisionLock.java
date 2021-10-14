// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import com.yahoo.transaction.Mutex;

import java.util.Objects;

/**
 * A type-safe wrapper for an application's provision lock.
 *
 * @author mpolden
 */
public class ProvisionLock implements AutoCloseable {

    private final ApplicationId application;
    private final Mutex lock;

    public ProvisionLock(ApplicationId application, Mutex lock) {
        this.application = Objects.requireNonNull(application);
        this.lock = Objects.requireNonNull(lock);
    }

    public ApplicationId application() {
        return application;
    }

    @Override
    public void close() {
        lock.close();
    }

}
