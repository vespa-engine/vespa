// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

import com.yahoo.vespa.clustercontroller.core.ContentCluster;

public class ZooKeeperDatabaseFactory implements DatabaseFactory {

    @Override
    public Database create(Params params) throws Exception {
        return new ZooKeeperDatabase(params.cluster, params.nodeIndex, params.dbAddress,
                params.dbSessionTimeout, params.listener);
    }

}
