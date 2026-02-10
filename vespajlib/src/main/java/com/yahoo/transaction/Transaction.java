// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.transaction;

import java.util.List;

/**
 * A transaction against a single system, which may include several operations against the system, 
 * to be committed as one.
 * Implementations are required to atomically apply changes
 * in the commit step or throw an exception if it fails.
 * Operations are performed in the order they are added.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public interface Transaction extends AutoCloseable {

    /**
     * Adds an operation to this transaction. Return self for chaining.
     *
     * @param operation {@link Operation} to append
     * @return self, for chaining
     */
    Transaction add(Operation operation);

    /**
     * Adds multiple operations to this transaction. Return self for chaining.
     *
     * @param operation {@link Operation} to append
     * @return self, for chaining
     */
    Transaction add(List<Operation> operation);

    /**
     * Returns the operations of this.
     * Ownership of the returned list is transferred to the caller. The ist may be ready only.
     */
    List<Operation> operations();

    /**
     * Checks whether or not the transaction is able to commit in its current state and do any transient preparatory
     * work to commit.
     *
     * @throws IllegalStateException if the transaction cannot be committed
     */
    void prepare();

    /**
     * Commit this transaction. If this method returns, all operations in this transaction was committed
     * successfully. Implementations of this must be exception safe or log a message of type severe if they partially
     * alter state.
     *
     * @throws IllegalStateException if transaction failed.
     */
    void commit();

    /**
     * This is called if the transaction should be rolled back after commit. If a rollback is not possible or
     * supported. This must log a message of type severe with detailed information about the resulting state.
     */
    void rollbackOrLog();

    /**
     * Closes and frees any resources allocated by this transaction. The transaction instance cannot be reused once
     * closed.
     */
    void close();

    /**
     * Operations that a transaction supports should implement this interface.
     * It does not define any methods because the interface to use is a contract between the
     * specific transaction and operation type used.
     */
    interface Operation { }

}
