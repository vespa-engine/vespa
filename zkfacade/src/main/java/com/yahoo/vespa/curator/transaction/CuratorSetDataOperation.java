// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.api.transaction.CuratorTransaction;

/**
 * ZooKeeper setData operation.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
class CuratorSetDataOperation implements CuratorOperation {

    private final String path;
    private final byte[] data;

    public CuratorSetDataOperation(String path, byte[] data) {
        this.path = path;
        this.data = data;
    }

    @Override
    public void check(Curator curator, TransactionChanges changes) {
        if ( ! curator.exists(Path.fromString(path)) && ! changes.create(path) )
            throw new IllegalStateException("Cannot perform " + this + ": Path does not exist");
    }

    @Override
    public CuratorTransaction and(CuratorTransaction transaction) throws Exception {
        return transaction.setData().forPath(path, data).and();
    }

    @Override
    public String toString() {
        return "SET " + path;
    }

}
