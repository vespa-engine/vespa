// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaclient;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.config.subscription.ConfigGetter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A list of content clusters, either obtained from a list, a given config or by self-subscribing */
public class ClusterList {

    List<ClusterDef> contentClusters = new ArrayList<>();

    public ClusterList() {
        this(new ArrayList<>());
    }
    
    public ClusterList(List<ClusterDef> contentClusters) {
        this.contentClusters = contentClusters;
    }

    public ClusterList(String configId) {
        configure(new ConfigGetter<>(ClusterListConfig.class).getConfig(configId));
    }
    
    public ClusterList(ClusterListConfig config) {
        configure(config);
    }

    private void configure(ClusterListConfig config) {
        contentClusters.clear(); // TODO: Create a new
        for (int i = 0; i < config.storage().size(); i++)
            contentClusters.add(new ClusterDef(config.storage(i).name(), config.storage(i).configid()));
    }

    /** Returns a reference to the mutable list */
    public List<ClusterDef> getStorageClusters() {
        return contentClusters; // TODO: Use immutable list
    }

}
