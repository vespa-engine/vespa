// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

import com.yahoo.container.search.LegacyEmulationConfig;

/**
 * Helper class for carrying around cluster-related
 * config parameters to the FastSearcher class.
 *
 * @author arnej27959
 */
public class ClusterParams {

    public final int clusterNumber;
    public final String searcherName;
    public final LegacyEmulationConfig emulation;

    /**
     * For compatibility
     */
    public ClusterParams(int number, String name) {
        this(number, name, new LegacyEmulationConfig(new LegacyEmulationConfig.Builder()));
    }

    /**
     * For testcases only
     */
    public ClusterParams(String name) {
        this(0, name);
    }

    /**
     * Make up full ClusterParams
     */
    public ClusterParams(int number, String name, LegacyEmulationConfig cfg) {
        this.clusterNumber = number;
        this.searcherName = name;
        this.emulation = cfg;
    }

}
