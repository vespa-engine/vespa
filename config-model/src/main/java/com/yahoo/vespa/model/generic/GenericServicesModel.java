// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.generic;

import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.vespa.model.generic.service.ServiceCluster;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Ulf Lilleengen
 * @since 5.1
 */
public class GenericServicesModel extends ConfigModel {
    private final List<ServiceCluster> clusters = new ArrayList<>();
    public GenericServicesModel(ConfigModelContext modelContext) {
        super(modelContext);
    }

    public void addCluster(ServiceCluster cluster) {
        clusters.add(cluster);
    }

    public List<ServiceCluster> serviceClusters() {
        return clusters;
    }
}
