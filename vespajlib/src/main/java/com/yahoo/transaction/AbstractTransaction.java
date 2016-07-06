// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
public abstract class AbstractTransaction<OPERATION extends Transaction.Operation> implements Transaction<OPERATION> {

    private static final Logger log = Logger.getLogger(AbstractTransaction.class.getName());
    private final List<OPERATION> operations = new ArrayList<>();

    protected AbstractTransaction() { }

    @Override
    public Transaction add(OPERATION operation) {
        this.operations.add(operation);
        return this;
    }

    @Override
    public Transaction add(List<OPERATION> operations) {
        this.operations.addAll(operations);
        return this;
    }

    @Override
    public List<OPERATION> operations() { return operations; }

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
