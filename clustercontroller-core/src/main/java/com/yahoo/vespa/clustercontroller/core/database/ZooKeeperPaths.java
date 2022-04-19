// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.clustercontroller.core.database;

import com.yahoo.vespa.clustercontroller.core.FleetControllerId;

/**
 * @author hakonhall
 */
public class ZooKeeperPaths {
    private final String root;
    private final int myIndex;

    public ZooKeeperPaths(FleetControllerId id) {
        this.root = "/vespa/fleetcontroller/" + id.clusterName();
        this.myIndex = id.index();
    }

    public String root() { return root; }
    public String indexesRoot() { return root + "/indexes"; }
    public String indexOf(int index) { return indexesRoot() + "/" + index; }
    public String indexOfMe() { return indexOf(myIndex); }
    public String wantedStates() { return root + "/wantedstates"; }
    public String publishedStateBundle() { return root + "/published_state_bundle"; }
    public String latestVersion() { return root + "/latestversion"; }
    public String startTimestamps() { return root + "/starttimestamps"; }
}
