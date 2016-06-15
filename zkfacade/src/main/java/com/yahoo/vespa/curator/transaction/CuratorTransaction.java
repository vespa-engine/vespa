// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.transaction.CuratorTransactionFinal;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * Transaction implementation against ZooKeeper.
 *
 * @author lulf
 */
public class CuratorTransaction implements Transaction {

    private static final Logger log = Logger.getLogger(CuratorTransaction.class.getName());
    private final List<Operation> operations = new ArrayList<>();
    private final Curator curator;

    public CuratorTransaction(Curator curator) {
        this.curator = curator;
    }

    @Override
    public Transaction add(Operation operation) {
        this.operations.add(operation);
        return this;
    }

    @Override
    public Transaction add(List<Operation> operations) {
        this.operations.addAll(operations);
        return this;
    }

    @Override
    public List<Operation> operations() { return operations; }

    @Override
    public void prepare() {
        for (Operation operation : operations)
            ((CuratorOperation)operation).check(curator);
    }

    @Override
    public void commit() {
        try {
            org.apache.curator.framework.api.transaction.CuratorTransaction transaction = curator.framework().inTransaction();
            for (Operation operation : operations) {
                CuratorOperation zkOperation = (CuratorOperation) operation;
                transaction = zkOperation.and(transaction);
            }
            ((CuratorTransactionFinal) transaction).commit();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void rollbackOrLog() {
        log.severe("The following ZooKeeper operations were incorrectly committed and probably require " +
                   "manual correction: " + operations);
    }

    @Override
    public void close() {
        operations.clear();
    }

}
