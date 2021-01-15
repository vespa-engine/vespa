// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.generic.service;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.HostSystem;

import java.util.ArrayList;
import java.util.Collection;

/**
 * A cluster of nodes running one application specific generic service. These are defined on the top level in the Vespa config
 * @author vegardh
 *
 */
public class ServiceCluster extends AbstractConfigProducer<ServiceCluster> {

    private static final long serialVersionUID = 1L;
    private final String command;
    private final String name;
    private HostSystem hostSystem; // A generic cluster can resolve hosts for its nodes

    public ServiceCluster(AbstractConfigProducer<?> parent, String name, String command) {
        super(parent, name);
        this.command=command;
        this.name=name;
    }

    public String getName() {
        return name;
    }

    String getCommand() {
        return command;
    }

    public Collection<Service> services() {
        Collection<Service> ret = new ArrayList<>();
        for (Object child : getChildren().values()) {
            if (child instanceof Service) ret.add((Service) child);
        }
        return ret;
    }

    @Override
    public HostSystem hostSystem() {
        if (hostSystem!=null) return hostSystem;
        return super.hostSystem();
    }

    /**
     * Sets the host system for this.
     * @param hostSystem a {@link com.yahoo.vespa.model.HostSystem}
     */
    public void setHostSystem(HostSystem hostSystem) {
        this.hostSystem = hostSystem;
    }

}
