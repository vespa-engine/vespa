// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import com.yahoo.transaction.AbstractTransaction;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Transaction implementation against ZooKeeper.
 *
 * @author lulf
 */
public class CuratorTransaction extends AbstractTransaction {

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

    /** Returns a curator transaction having a list of operations */
    public static CuratorTransaction from(List<CuratorOperation> operations, Curator curator) {
        CuratorTransaction transaction = new CuratorTransaction(curator);
        for (Operation operation : operations)
            transaction.add(operation);
        return transaction;
    }

    @Override
    public void prepare() {
        TransactionChanges changes = new TransactionChanges();
        for (Operation operation : operations())
            ((CuratorOperation)operation).check(curator, changes);
        prepared = true;
    }

    /** Commits this transaction. If it is not already prepared this will prepare it first */
    @Override
    public void commit() {
        try {
            if ( ! prepared)
                prepare();
            org.apache.curator.framework.api.transaction.CuratorTransaction transaction = curator.framework().inTransaction();
            for (Operation operation : operations()) {
                transaction = ((CuratorOperation)operation).and(transaction);
            }
            ((CuratorTransactionFinal) transaction).commit();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String toString() {
        return String.join(",", operations().stream().map(operation -> operation.toString()).collect(Collectors.toList()));
    }

}
