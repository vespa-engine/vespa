// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server;

import com.yahoo.path.Path;
import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.config.GenerationCounter;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.curator.Curator;

import java.util.logging.Logger;

/**
 * Distributed global generation counter for the super model.
 *
 * @author lulf
 * @since 5.9
 */
public class SuperModelGenerationCounter implements GenerationCounter {

    private static final Path counterPath = Path.fromString("/config/v2/RPC/superModelGeneration");
    private final CuratorCounter counter;

    public SuperModelGenerationCounter(Curator curator) {
        this.counter =  new CuratorCounter(curator, counterPath.getAbsolute());
    }

    /**
     * Increment counter and return next value. This method is thread safe and provides an atomic value
     * across zookeeper clusters.
     *
     * @return incremented counter value.
     */
    public synchronized long increment() {
        return counter.next();
    }

    /**
     * @return current counter value.
     */
    public synchronized long get() {
        return counter.get();
    }

    /** Returns a transaction which increments this */
    public IncrementTransaction incrementTransaction() {
        return new IncrementTransaction(counter);
    }
    
    /** An increment transaction which supports rollback */
    public static class IncrementTransaction extends AbstractTransaction<IncrementTransaction.Operation> {

        /** Creates a counting curator transaction containing a single increment operation */
        public IncrementTransaction(CuratorCounter counter) {
            add(new Operation(counter));
        }

        @Override
        public void prepare() { }

        @Override
        public void commit() {
            for (Operation operation : operations())
                operation.commit();
        }

        @Override
        public void rollbackOrLog() {
            for (Operation operation : operations())
                operation.rollback();
        }

        public static class Operation implements Transaction.Operation {

            private static final Logger log = Logger.getLogger(Operation.class.getName());
            private final CuratorCounter counter;

            public Operation(CuratorCounter counter) {
                this.counter = counter;
            }

            public void commit() {
                counter.next();
            }

            public void rollback() {
                try {
                    counter.previous();
                }
                catch (IllegalStateException e) {
                    log.severe("Could not roll back " + this);
                }
            }

            public String toString() { return "increment " + counterPath + " operation"; }

        }

    }
}
