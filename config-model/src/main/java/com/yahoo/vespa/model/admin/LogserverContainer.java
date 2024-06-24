// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.container.Container;

/**
 * Container that should be running on same host as the logserver. Sets up a handler for getting logs from logserver.
 */
public class LogserverContainer extends Container {

    public LogserverContainer(TreeConfigProducer<?> parent, DeployState deployState) {
        super(parent, "" + 0, 0, deployState);
    }

    @Override
    public ContainerServiceType myServiceType() {
        return ContainerServiceType.LOGSERVER_CONTAINER;
    }

    @Override
    public String defaultPreload() {
        return "";
    }

}
