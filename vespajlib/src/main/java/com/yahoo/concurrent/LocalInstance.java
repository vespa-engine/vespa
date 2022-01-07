// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import com.yahoo.concurrent.ThreadLocalDirectory.ObservableUpdater;
import com.yahoo.concurrent.ThreadLocalDirectory.Updater;

/**
 * Only for use along with ThreadLocalDirectory. A thread local data container
 * instance. The class is visible to avoid indirection through the internal
 * {@link ThreadLocal} in ThreadLocalDirectory if possible, but has no user
 * available methods.
 *
 * @param <AGGREGATOR> the structure to insert produced data into
 * @param <SAMPLE> type of produced data to insert from each participating thread
 * @author Steinar Knutsen
 */
public final class LocalInstance<AGGREGATOR, SAMPLE> {

    /**
     * The current generation of data produced from a single thread, where
     * generation is the period between two subsequent calls to
     * ThreadLocalDirectory.fetch().
     */
    private AGGREGATOR current;

    // see comment on setRegistered(boolean) for locking explanation
    private boolean isRegistered = false;
    private final Object lock = new Object();

    LocalInstance(Updater<AGGREGATOR, SAMPLE> updater) {
        current = updater.createGenerationInstance(null);
    }

    boolean update(SAMPLE x, Updater<AGGREGATOR, SAMPLE> updater) {
        synchronized (lock) {
            current = updater.update(current, x);
            return isRegistered;
        }
    }

    AGGREGATOR getAndReset(Updater<AGGREGATOR, SAMPLE> updater) {
        AGGREGATOR previous;
        synchronized (lock) {
            previous = current;
            current = updater.createGenerationInstance(previous);
            setRegistered(false);
        }
        return previous;
    }

    AGGREGATOR copyCurrent(ObservableUpdater<AGGREGATOR, SAMPLE> updater) {
        AGGREGATOR view;
        synchronized (lock) {
            view = updater.copy(current);
        }
        return view;
    }

    // This is either set by the putting thread or the fetching thread. If
    // it is set by the putting thread, then there is no memory barrier,
    // because it is only _read_ in the putting thread. If it is set by the
    // fetching thread, then the memory barrier is this.lock. This
    // roundabout way is to avoid creating many-to-many memory barrier and
    // locking relationships.
    void setRegistered(boolean isRegistered) {
        this.isRegistered = isRegistered;
    }

}
