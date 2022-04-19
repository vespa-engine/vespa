// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

/**
 * @author hakonhall
 */
public class ZooKeeperPaths {
    private final String root;
    private final int nodeIndex;

    public ZooKeeperPaths(String clusterName, int nodeIndex) {
        this.root = "/vespa/fleetcontroller/" + clusterName;
        this.nodeIndex = nodeIndex;
    }

    public String root() { return root; }
    public String indexesRoot() { return root + "/indexes"; }
    public String indexesOf(int index) { return indexesRoot() + "/" + index; }
    public String indexesOfMe() { return indexesOf(nodeIndex); }
    public String wantedStates() { return root + "/wantedstates"; }
    public String publishedStateBundle() { return root + "/published_state_bundle"; }
    public String latestVersion() { return root + "/latestversion"; }
    public String startTimestamps() { return root + "/starttimestamps"; }
}
