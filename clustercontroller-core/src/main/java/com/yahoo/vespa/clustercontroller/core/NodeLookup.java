// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

import com.yahoo.vespa.clustercontroller.core.listeners.SlobrokListener;

/**
 * Interface for a node lookup service, such as slobrok, config, or tier controller.
 */
public interface NodeLookup {

    void shutdown();

    boolean updateCluster(ContentCluster cluster, SlobrokListener listener);

}
