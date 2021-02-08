// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

import com.yahoo.vespa.clustercontroller.core.ContentCluster;

/**
 * Database factory to enable test mocking of DB features. In practice, this
 * will always be {@link ZooKeeperDatabase} due to rather heavy ZK feature
 * dependencies and leaky abstractions built on top of them.
 */
public interface DatabaseFactory {

    class Params {
        ContentCluster cluster;
        int nodeIndex;
        String dbAddress;
        int dbSessionTimeout;
        Database.DatabaseListener listener;

        Params cluster(ContentCluster c) { this.cluster = c; return this; }
        Params nodeIndex(int i) { this.nodeIndex = i; return this; }
        Params databaseAddress(String address) { this.dbAddress = address; return this; }
        Params databaseSessionTimeout(int timeout) { this.dbSessionTimeout = timeout; return this; }
        Params databaseListener(Database.DatabaseListener listener) { this.listener = listener; return this; }
    }

    Database create(Params params) throws Exception;

}
