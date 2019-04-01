// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;

/**
 * @author gjoranv
 */
public class LogserverContainerCluster extends ContainerCluster<LogserverContainer> {

    public LogserverContainerCluster(AbstractConfigProducer<?> parent, String name, DeployState deployState) {
        super(parent, name, name, deployState);

        addDefaultHandlersWithVip();
        addLogHandler();
    }

    @Override
    protected void doPrepare(DeployState deployState) { }

    private void addLogHandler() {
        Handler<?> logHandler = Handler.fromClassName(ContainerCluster.LOG_HANDLER_CLASS);
        logHandler.addServerBindings("*://*/logs");
        addComponent(logHandler);
    }

}
