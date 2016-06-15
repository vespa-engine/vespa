// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaclient;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.config.subscription.ConfigGetter;

import java.util.ArrayList;
import java.util.List;

public class ClusterList {
    List<ClusterDef> storageClusters = new ArrayList<ClusterDef>();

    public ClusterList() {
        this(null);
    }

    public ClusterList(String configId) {
        if (configId != null) {
            configure(new ConfigGetter<>(ClusterListConfig.class).getConfig(configId));
        }
    }

    public List<ClusterDef> getStorageClusters() {
        return storageClusters;
    }

    public void configure(ClusterListConfig cfg) {
        storageClusters.clear();
        for (int i = 0; i < cfg.storage().size(); i++) {
            storageClusters.add(new ClusterDef(cfg.storage(i).name(),
                                cfg.storage(i).configid()));
        }
    }

    public static ClusterList createMockedList(List<ClusterDef> clusters) {
        ClusterList list = new ClusterList(null);
        list.storageClusters = clusters;
        return list;
    }
}
