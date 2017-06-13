// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vdslib.state.ClusterState;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;
import com.yahoo.vespa.clustercontroller.core.ContentCluster;

/**
 * @author <a href="mailto:humbe@yahoo-inc.com">Haakon Humberset</a>
 */
public interface RunDataExtractor {

    public ClusterState getLatestClusterState();
    public FleetControllerOptions getOptions();
    public long getConfigGeneration();
    public ContentCluster getCluster();

}
