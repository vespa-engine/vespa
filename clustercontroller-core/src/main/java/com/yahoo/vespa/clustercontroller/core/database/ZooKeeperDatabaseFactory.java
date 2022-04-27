// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

import com.yahoo.vespa.clustercontroller.core.FleetControllerContext;

public class ZooKeeperDatabaseFactory implements DatabaseFactory {

    private final FleetControllerContext context;

    public ZooKeeperDatabaseFactory(FleetControllerContext context) {
        this.context = context;
    }

    @Override
    public Database create(Params params) throws Exception {
        return new ZooKeeperDatabase(context, params.dbAddress, params.dbSessionTimeout, params.listener);
    }

}
