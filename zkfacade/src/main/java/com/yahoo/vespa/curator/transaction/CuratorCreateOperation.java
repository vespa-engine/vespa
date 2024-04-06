// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.curator.transaction;

import com.yahoo.path.Path;
import com.yahoo.vespa.curator.Curator;
import org.apache.curator.framework.api.transaction.CuratorTransaction;

import java.util.Optional;

/**
 * ZooKeeper create operation.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
class CuratorCreateOperation implements CuratorOperation {

    private final String path;
    private final Optional<byte[]> data;

    CuratorCreateOperation(String path, Optional<byte[]> data) {
        this.path = path;
        this.data = data;
    }

    @Override
    public void check(Curator curator, TransactionChanges changes) {
        int lastSlash = path.lastIndexOf("/");
        if (lastSlash < 0) return; // root; ok
        String parent = path.substring(0, lastSlash);
        if ( ! parent.isEmpty() && ! curator.exists(Path.fromString(parent)) && ! changes.create(parent))
            throw new IllegalStateException("Cannot perform " + this + ": Parent '" + parent + "' does not exist");
        changes.addCreate(path);
    }

    @Override
    public CuratorTransaction and(CuratorTransaction transaction) throws Exception {
        if (data.isPresent()) {
            return transaction.create().forPath(path, data.get()).and();
        } else {
            return transaction.create().forPath(path).and();
        }
    }

    @Override
    public String toString() {
        return "CREATE " + path;
    }

}
