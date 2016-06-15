// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.clients;

import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.documentapi.messagebus.loadtypes.LoadType;
import com.yahoo.documentapi.messagebus.loadtypes.LoadTypeSet;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.search.ContainerHttpGateway;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * This is the clients plugin for the Vespa model. It is responsible for creating
 * all clients services.
 *
 * @author <a href="mailto:gunnarga@yahoo-inc.com">Gunnar Gauslaa Bergem</a>
 */
public class Clients extends ConfigModel {

    private static final long serialVersionUID = 1L;
    private ContainerCluster containerHttpGateways = null;
    private List<VespaSpoolerService> vespaSpoolers = new LinkedList<>();
    private LoadTypeSet loadTypes = new LoadTypeSet();
    private final AbstractConfigProducer parent;

    public Clients(ConfigModelContext modelContext) {
        super(modelContext);
        this.parent = modelContext.getParentProducer();
    }

    public AbstractConfigProducer getConfigProducer() {
        return parent;
    }

    @Override
    public void prepare(ConfigModelRepo configModelRepo) {
        if (containerHttpGateways != null) {
            containerHttpGateways.prepare();
        }
    }

    public void setContainerHttpGateways(ContainerCluster containerHttpGateways) {
        this.containerHttpGateways = containerHttpGateways;
    }

    public List<VespaSpoolerService> getVespaSpoolers() {
        return vespaSpoolers;
    }

    public LoadTypeSet getLoadTypes() {
        return loadTypes;
    }

    public void getConfig(LoadTypeConfig.Builder builder) {
        for (LoadType t : loadTypes.getNameMap().values()) {
            if (t != LoadType.DEFAULT) {
                builder.type(getLoadTypeConfig(t));
            }
        }
    }

    private LoadTypeConfig.Type.Builder getLoadTypeConfig(LoadType loadType) {
        LoadTypeConfig.Type.Builder builder = new LoadTypeConfig.Type.Builder();
        builder.name(loadType.getName());
        builder.id(loadType.getId());
        builder.priority(loadType.getPriority().toString());
        return builder;
    }

}
