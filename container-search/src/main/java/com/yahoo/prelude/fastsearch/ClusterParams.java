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

    public final String searcherName;
    public final LegacyEmulationConfig emulation;

    /**
     * For testcases only
     */
    public ClusterParams(String name) {
        this(name, new LegacyEmulationConfig(new LegacyEmulationConfig.Builder()));
    }

    /**
     * Make up full ClusterParams
     */
    public ClusterParams(String name, LegacyEmulationConfig cfg) {
        this.searcherName = name;
        this.emulation = cfg;
    }

}
