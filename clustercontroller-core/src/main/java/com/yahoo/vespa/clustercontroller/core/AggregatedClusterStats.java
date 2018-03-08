// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Interface that gives a view over aggregated cluster stats that will change over time.
 */
public interface AggregatedClusterStats {

    boolean hasUpdatesFromAllDistributors();

    ContentClusterStats getStats();

}
