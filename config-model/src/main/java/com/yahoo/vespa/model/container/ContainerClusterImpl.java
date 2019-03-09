// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;

/**
 * The default container cluster implementation.
 *
 * @author gjoranv
 */
public final class ContainerClusterImpl
        extends ContainerCluster
{
    public ContainerClusterImpl(AbstractConfigProducer<?> parent, String subId, String name, DeployState deployState) {
        super(parent, subId, name, deployState);
    }

    public ContainerClusterImpl(AbstractConfigProducer<?> parent, String subId, String name, ContainerClusterVerifier verifier, DeployState deployState) {
        super(parent, subId, name, verifier, deployState);
    }
}
