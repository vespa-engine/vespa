// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.status;

import com.yahoo.vespa.clustercontroller.core.ContentCluster;
import com.yahoo.vespa.clustercontroller.core.FleetControllerOptions;

/**
 * @author Haakon Humberset
 */
public interface RunDataExtractor {

    FleetControllerOptions getOptions();
    long getConfigGeneration();
    ContentCluster getCluster();

}
