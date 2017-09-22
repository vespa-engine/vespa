// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;

/**
 * @author Haakon Humberset
 */
public interface RunDataExtractor {

    ClusterState getLatestClusterState();
    FleetControllerOptions getOptions();
    long getConfigGeneration();
    ContentCluster getCluster();

}
