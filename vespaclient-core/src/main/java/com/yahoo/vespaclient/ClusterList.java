// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaclient;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.config.subscription.ConfigGetter;

import java.util.ArrayList;
import java.util.List;

/** A list of content clusters, either obtained from a list, a given config or by self-subscribing */
public class ClusterList {

    List<ClusterDef> storageClusters = new ArrayList<>();

    public ClusterList() {
        this((String)null);
    }

    public ClusterList(String configId) {
        if (configId != null)
            configure(new ConfigGetter<>(ClusterListConfig.class).getConfig(configId));
    }
    
    public ClusterList(ClusterListConfig config) {
        configure(config);
    }

    private void configure(ClusterListConfig config) {
        storageClusters.clear(); // TODO: Create a new
        for (int i = 0; i < config.storage().size(); i++)
            storageClusters.add(new ClusterDef(config.storage(i).name(), config.storage(i).configid()));
    }

    public List<ClusterDef> getStorageClusters() {
        return storageClusters; // TODO: Use immutable list
    }

}
