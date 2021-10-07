// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import java.util.concurrent.Semaphore;

/**
 * A semaphore that can be re-sized.
 *
 * @author dybis
 */
final public class ConcurrentDocumentOperationBlocker {

    private static final int INITIAL_SIZE = 0;
    private final ReducableSemaphore semaphore = new ReducableSemaphore();
    private int maxConcurrency = INITIAL_SIZE;
    private final Object monitor = new Object();

    /*
     * Resizes the semaphore. It does not wait for threads that are in the queue when downsizing.
     */
    void setMaxConcurrency(int maxConcurrency) {
        synchronized (monitor) {
            int deltaConcurrency = maxConcurrency - this.maxConcurrency;

            if (deltaConcurrency > 0) {
                semaphore.release(deltaConcurrency);
            }
            if (deltaConcurrency < 0) {
                semaphore.reducePermits(-1 * deltaConcurrency);
            }
            this.maxConcurrency = maxConcurrency;
        }
    }

    /**
     * Release a permit.
     */
    void operationDone() {
        semaphore.release();
    }

    /**
     * Acquire a permit. Blocking if no permits available.
     */
    void startOperation() throws InterruptedException {
        semaphore.acquire();
    }

    int availablePermits() {
        return semaphore.availablePermits();
    }

    /**
     * We need to extend Semaphore to get access to protected reducePermit() method.
     */
    @SuppressWarnings("serial")
    private static final class ReducableSemaphore extends Semaphore {

        ReducableSemaphore() {
            super(INITIAL_SIZE, true /* FIFO */);
        }

        @Override
        protected void reducePermits(int reduction) {
            super.reducePermits(reduction);
        }
    }

}
