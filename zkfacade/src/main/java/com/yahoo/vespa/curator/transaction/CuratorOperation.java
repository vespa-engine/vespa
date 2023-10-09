// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.api.transaction.CuratorTransaction;

/**
 * The ZooKeeper operations that we support doing transactional.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public interface CuratorOperation extends Transaction.Operation {

    /**
     * Returns the transaction resulting from combining this operation with the input transaction
     *
     * @param transaction {@link CuratorTransaction} to append this operation to.
     * @return the transaction, for chaining.
     * @throws Exception if unable to create transaction for this operation.
     */
    CuratorTransaction and(CuratorTransaction transaction) throws Exception;

    /**
     * Check if this operation can be performed by calling check(curator, new TransactionChanges()).
     *
     * @throws IllegalStateException if it cannot
     */
    default void check(Curator curator) {
        check(curator, new TransactionChanges());        
    }

    /**
     * Check if this operation can be performed.
     *
     * @param curator the curator instance to check against
     * @param changes changes which will be done prior to this operation as part of the same transaction.
     *        Operations should use both this and the curator instance to check if they can be performed.
     *        In addition, they are required to add the change(s) they will perform to the set of changes.
     * @throws IllegalStateException if it cannot be performed
     */
    void check(Curator curator, TransactionChanges changes);

}
