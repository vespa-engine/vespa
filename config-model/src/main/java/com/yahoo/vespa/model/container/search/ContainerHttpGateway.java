// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.search;

import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;

/**
 * @author gjoranv
 */
public class ContainerHttpGateway extends Container {

    public ContainerHttpGateway(ContainerCluster parent, String name, int wantedPort, int index) {
        super(parent, name, index);

        // TODO: when this class is removed, all ports for the gateway will map to standard container ports
        //       this is just a tjuvtriks to keep the old gateway port allocation for now.
        setBasePort(wantedPort);
    }

    @Override
    public String getServiceType() { return "container-httpgateway"; }

}
