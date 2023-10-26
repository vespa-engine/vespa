// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.session;

import com.yahoo.transaction.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Dummy transaction implementation that only does stuff in memory and does not adhere to contract.
 * @author Ulf Lilleengen
 */
public class DummyTransaction implements Transaction {

    private final List<Operation> operations = new ArrayList<>();

    public interface RunnableOperation extends Operation, Runnable {
    }

    public DummyTransaction() { }

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
    public List<Operation> operations() { return new ArrayList<>(operations); }

    @Override
    public void prepare() { }

    @Override
    public void commit() {
        for (Operation op : operations) {
            ((RunnableOperation)op).run();
        }
    }

    @Override
    public void rollbackOrLog() {
        throw new IllegalStateException("Unexpected rollback");
    }

    @Override
    public void close() { }
}
