// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.deploy.DeployState;
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
 * TODO: Add type parameter for CLUSTER instead of using wildcard '? extends Container'
 *
 * @author Tony Vaagenes
 */
public class ContainerModel extends ConfigModel {

    // TODO: Move to referer
    public static final String DOCPROC_RESERVED_NAME = "docproc";

    private ContainerCluster<? extends Container> containerCluster;

    public ContainerModel(ConfigModelContext context) {
        super(context);
    }

    public void setCluster(ContainerCluster<? extends Container> containerCluster) { this.containerCluster = containerCluster; }

    public ContainerCluster<? extends Container> getCluster() { return containerCluster; }

    @Override
    public void prepare(ConfigModelRepo plugins, DeployState deployState) {
        assert (getCluster() != null) : "Null container cluster!";
        getCluster().prepare(deployState);
    }

    @Override
    public void initialize(ConfigModelRepo configModelRepo) {
        List<AbstractSearchCluster> searchClusters = Content.getSearchClusters(configModelRepo);

        Map<String, AbstractSearchCluster> searchClustersByName = new TreeMap<>();
        for (AbstractSearchCluster c : searchClusters)
            searchClustersByName.put(c.getClusterName(), c);

        getCluster().initialize(searchClustersByName);
    }

    public static Collection<ContainerCluster<?>> containerClusters(ConfigModelRepo models) {
        List<ContainerCluster<?>> containerClusters = new ArrayList<>();

        for (ContainerModel model: models.getModels(ContainerModel.class))
            containerClusters.add(model.getCluster());

        return containerClusters;
    }

}
