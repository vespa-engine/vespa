// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;

/**
 * Transaction implementation against ZooKeeper.
 *
 * @author lulf
 */
public class CuratorTransaction extends AbstractTransaction<CuratorOperation> {

    private final Curator curator;
    private boolean prepared = false;

    public CuratorTransaction(Curator curator) {
        this.curator = curator;
    }
    
    /** Returns an empty curator transaction */
    public static CuratorTransaction empty(Curator curator) {
        return new CuratorTransaction(curator);
    }
    
    /** Returns a curator transaction having a single operation */
    public static CuratorTransaction from(CuratorOperation operation, Curator curator) {
        CuratorTransaction transaction = new CuratorTransaction(curator);
        transaction.add(operation);
        return transaction;
    }

    @Override
    public void prepare() {
        TransactionChanges changes = new TransactionChanges();
        for (CuratorOperation operation : operations())
            operation.check(curator, changes);
        prepared = true;
    }

    /** Commits this transaction. If it is not already prepared this will prepare it first */
    @Override
    public void commit() {
        try {
            if ( ! prepared)
                prepare();
            org.apache.curator.framework.api.transaction.CuratorTransaction transaction = curator.framework().inTransaction();
            for (CuratorOperation operation : operations()) {
                transaction = operation.and(transaction);
            }
            ((CuratorTransactionFinal) transaction).commit();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

}
