// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import com.yahoo.transaction.Transaction;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.api.transaction.CuratorTransaction;

import java.util.Optional;

/**
 * The ZooKeeper operations that we support doing transactional.
 *
 * @author lulf
 * @author bratseth
 */
public interface CuratorOperation extends Transaction.Operation {

    /**
     * Implementations must support adding this operation to a curator transaction.
     *
     * @param transaction {@link CuratorTransaction} to append this operation to.
     * @return the transaction, for chaining.
     * @throws Exception if unable to create transaction for this operation.
     */
    CuratorTransaction and(CuratorTransaction transaction) throws Exception;

    /**
     * Check if this operation can be performed without making any changes.
     *
     * @throws IllegalStateException if it cannot
     */
    void check(Curator curator);

}
