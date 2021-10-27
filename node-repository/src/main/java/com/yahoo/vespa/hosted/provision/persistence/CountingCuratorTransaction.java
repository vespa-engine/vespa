// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

/**
 * CuratorTransaction wrapper which increments a counter, to signal invalidation of node repository caches.
 *
 * This class ensures a CuratorTransaction against the cached data (the node repository data) is
 * accompanied by an increment of the data generation counter. An increment must occur <em>after</em>
 * the write has completed, successfully or not. It is therefore placed in a {@code finally} block,
 * wrapping the super class' {@link #commit()}.
 * Likewise, {@link #prepare()} is also wrapped with an increment, in case it fails due to an inconsistent cache.
 * The cache is invalid whenever the generation counter is higher than what the cache contents were read with.
 * The usual locking for modifications of shared data is then enough to ensure the cache provides a
 * consistent view of the shared data, with one exception: when incrementing the counter fails. This is
 * assumed to be extremely rare, and the consequence is temporary neglect of cache invalidation.
 *
 * @author jonmv
 */
class CountingCuratorTransaction extends CuratorTransaction {

    private final CuratorCounter counter;

    public CountingCuratorTransaction(Curator curator, CuratorCounter counter) {
        super(curator);
        this.counter = counter;
    }

    @Override
    public void prepare() {
        try {
            counter.get();
            super.prepare();
        }
        finally {
            counter.next();
        }
    }

    @Override
    public void commit() {
        try {
            super.commit();
        }
        finally {
            counter.next();
        }
    }

    @Override
    public String toString() {
        return "(" + super.toString() + "), INCREMENT " + counter;
    }

}
