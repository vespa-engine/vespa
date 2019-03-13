// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.ContainerCluster;

/**
 * @author gjoranv
 */
public class LogserverContainerCluster extends ContainerCluster<LogserverContainer> {

    public LogserverContainerCluster(AbstractConfigProducer<?> parent, String subId, String name, DeployState deployState) {
        super(parent, subId, name, deployState);
    }

    @Override
    protected void myPrepare(DeployState deployState) { }

}
