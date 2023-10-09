// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

/**
 * A convenience base transaction class for multi-operation transactions
 * which maintains the ordered list of operations to commit and provides a default
 * implementation of rollbackOrLog which logs a SEVERE message.
 * 
 * @author bratseth
 */
public abstract class AbstractTransaction implements Transaction {

    private static final Logger log = Logger.getLogger(AbstractTransaction.class.getName());
    private final List<Operation> operations = new ArrayList<>();

    protected AbstractTransaction() { }

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

    /** Default implementations which logs a severe message. Operations should implement toString to use this. */
    @Override
    public void rollbackOrLog() {
        log.severe("The following operations were incorrectly committed and probably require " +
                   "manual correction: " + operations());
    }

    /** Default implementation which only clears operations */
    @Override
    public void close() {
        operations.clear();
    }

}
