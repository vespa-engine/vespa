// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.fastsearch;

/**
 * Helper class for carrying around cluster-related
 * config parameters to the FastSearcher class.
 *
 * @author arnej27959
 */
public class ClusterParams {

    public final String searcherName;

    /**
     * Make up full ClusterParams
     */
    public ClusterParams(String name) {
        this.searcherName = name;
    }

}
