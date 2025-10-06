// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core;

/**
 * Predefined set of dimension names used by various cluster controller metrics.
 *
 * @author vekterli
 */
class MetricDimensionNames {
    static final String CLUSTER = "cluster";
    static final String CLUSTER_ID = "clusterid";
    static final String CONTROLLER_INDEX = "controller-index";
    static final String NODE_TYPE = "node-type";
    static final String DID_WORK = "didWork";
    static final String WORK_ID = "workId";
}
