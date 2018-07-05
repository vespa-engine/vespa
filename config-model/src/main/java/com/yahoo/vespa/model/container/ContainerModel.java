// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.search.AbstractSearchCluster;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;


/**
 * A model of a container cluster.
 *
 * @author Tony Vaagenes
 */
public class ContainerModel extends ConfigModel {

    // TODO: Move to referer
    public static final String DOCPROC_RESERVED_NAME = "docproc";

    private ContainerCluster containerCluster;

    public ContainerModel(ConfigModelContext context) {
        super(context);
    }

    public void setCluster(ContainerCluster containerCluster) { this.containerCluster = containerCluster; }

    public ContainerCluster getCluster() { return containerCluster; }

    @Override
    public void prepare(ConfigModelRepo plugins) {
        assert (getCluster() != null) : "Null container cluster!";
        getCluster().prepare();
    }

    @Override
    @Deprecated
    public void initialize(ConfigModelRepo configModelRepo) {
        List<AbstractSearchCluster> searchClusters = Content.getSearchClusters(configModelRepo);

        Map<String, AbstractSearchCluster> searchClustersByName = new TreeMap<>();
        for (AbstractSearchCluster c : searchClusters) {
            searchClustersByName.put(c.getClusterName(), c);
        }

        getCluster().initialize(searchClustersByName);
    }

    public static Collection<ContainerCluster> containerClusters(ConfigModelRepo models) {
        List<ContainerCluster> containerClusters = new ArrayList<>();

        for (ContainerModel model: models.getModels(ContainerModel.class))
            containerClusters.add(model.getCluster());

        return containerClusters;
    }

}
