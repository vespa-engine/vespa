// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.curator.recipes.CuratorCounter;
import com.yahoo.vespa.curator.transaction.CuratorTransaction;

import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A curator transaction which increases a change counter on commit.
 * As this only ever does a single thing it needs no operations.
 */
class CountingCuratorTransaction implements Transaction {

    private final CuratorCounter counter;

    public CountingCuratorTransaction(CuratorCounter counter) {
        this.counter = counter;
    }

    @Override
    public Transaction add(Operation operation) { return this; }

    @Override
    public Transaction add(List<Operation> operation) { return this; }

    @Override
    public List<Operation> operations() { return Collections.emptyList(); }

    @Override
    public void prepare() {
        // Increase the counter also if there are prepare errors to throw away the cached state
        // in case that state leads to the rollback
        counter.next();
    }

    @Override
    public void rollbackOrLog() { }

    @Override
    public void close() { }

    @Override
    public void commit() { }

}
