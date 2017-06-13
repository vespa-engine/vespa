// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.curator.recipes.CuratorCounter;

/**
 * A curator transaction of curator counting operations which increments during prepare
 * such that the counter is also increased if there is a commit error.
 */
class EagerCountingCuratorTransaction extends AbstractTransaction {

    /** Creates a counting curator transaction containing a single increment operation */
    public EagerCountingCuratorTransaction(CuratorCounter counter) {
        add(new CountingCuratorOperation(counter));
    }
    
    @Override
    public void prepare() {
        for (Operation operation : operations())
            ((CountingCuratorOperation)operation).next();
    }

    @Override
    public void commit() { }

    @Override
    public void rollbackOrLog() { }
    
    static class CountingCuratorOperation implements Transaction.Operation {
        
        private final CuratorCounter counter;
        
        public CountingCuratorOperation(CuratorCounter counter) {
            this.counter = counter;
        }
        
        public void next() {
            counter.next();
        }
        
    }

}
