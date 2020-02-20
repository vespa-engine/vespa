// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.component.Handler;

/**
 * @author hmusum
 */
public class LogserverContainerCluster extends ContainerCluster<LogserverContainer> implements ThreadpoolConfig.Producer {

    public LogserverContainerCluster(AbstractConfigProducer<?> parent, String name, DeployState deployState) {
        super(parent, name, name, deployState);

        addDefaultHandlersWithVip();
        addLogHandler();
    }

    @Override
    protected void doPrepare(DeployState deployState) { }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        builder.maxthreads(10);
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        super.getConfig(builder);
        builder.jvm.heapsize(384);
    }

    protected boolean messageBusEnabled() { return false; }

    private void addLogHandler() {
        Handler<?> logHandler = Handler.fromClassName(ContainerCluster.LOG_HANDLER_CLASS);
        logHandler.addServerBindings("http://*/logs");
        addComponent(logHandler);
    }

}
