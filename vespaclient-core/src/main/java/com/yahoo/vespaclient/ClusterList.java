// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespaclient;

import com.yahoo.cloud.config.ClusterListConfig;
import com.yahoo.config.subscription.ConfigGetter;

import java.util.List;
import java.util.stream.Collectors;

/** A list of content clusters, either obtained from a list, a given config or by self-subscribing */
public class ClusterList {

    private final List<ClusterDef> contentClusters;

    public ClusterList() {
        this(List.of());
    }
    
    public ClusterList(List<ClusterDef> contentClusters) {
        this.contentClusters = List.copyOf(contentClusters);
    }

    @SuppressWarnings("deprecation")
    public ClusterList(String configId) {
        this(new ConfigGetter<>(ClusterListConfig.class).getConfig(configId));
    }
    
    public ClusterList(ClusterListConfig config) {
        this(parse(config));
    }

    public List<ClusterDef> getStorageClusters() {
        return contentClusters;
    }

    private static List<ClusterDef> parse(ClusterListConfig config) {
        return config.storage().stream()
                     .map(storage -> new ClusterDef(storage.name()))
                     .collect(Collectors.toUnmodifiableList());
    }

}
