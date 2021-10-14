// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.generic.service;

import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.PortAllocBridge;

/**
 * An application specific generic service
 * @author vegardh
 *
 */
public class Service extends AbstractService {
    private static final long serialVersionUID = 1L;

    public Service(ServiceCluster parent, String id) {
        super(parent, id);
        setProp("clustertype", parent.getName());
        setProp("clustername", parent.getName());
    }

    @Override
    public int getPortCount() {
        return 0;
    }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) { }

    @Override
    public String getStartupCommand() {
        return ((ServiceCluster) getParent()).getCommand();
    }

    private String getClusterName() {
        return ((ServiceCluster) getParent()).getName();
    }

    /**
     * Different services are represented using same class here, so we must take service name into account too
     *
     * @param host a host
     * @return the index of the host
     */
    protected int getIndex(HostResource host) {
        int i = 0;
        for (com.yahoo.vespa.model.Service s : host.getServices()) {
            if (!s.getClass().equals(getClass())) continue;
            Service other = (Service)s;
            if (s!=this && other.getClusterName().equals(getClusterName())) {
                i++;
            }
        }
        return i + 1;
    }

    @Override
    public String getServiceType() {
        return getClusterName();
    }
}
