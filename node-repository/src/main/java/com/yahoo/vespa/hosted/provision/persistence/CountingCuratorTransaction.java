package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.curator.transaction.CuratorOperation;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;
import com.yahoo.vespa.curator.transaction.TransactionChanges;

/**
 * CuratorTransaction wrapper which increments a counter, to signal invalidation of node repository caches.
 *
 * This class ensures a CuratorTransaction against the cached data (the node repository data) is
 * accompanied by an increment of the data generation counter. This increment must occur <em>after</em>
 * the write has completed, successfully or not. It is therefore placed in a {@code finally} block,
 * wrapping the super class' {@link #commit()}.
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
        counter.get();
        super.prepare();
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
