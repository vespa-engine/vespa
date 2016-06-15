// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    public final int rowBits;
    public final LegacyEmulationConfig emulation;

    /**
     * for compatibility
     **/
    public ClusterParams(int number, String name, int rowbits) {
        this(number, name, rowbits, new LegacyEmulationConfig());
    }

    /**
     * for testcases only
     **/
    public ClusterParams(String name) {
        this(0, name, 0);
    }

    /**
     * make up full ClusterParams
     **/
    public ClusterParams(int number, String name, int rowbits, LegacyEmulationConfig cfg) {
        this.clusterNumber = number;
        this.searcherName = name;
        this.rowBits = rowbits;
        this.emulation = cfg;
    }

}
