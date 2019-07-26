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

    // Switch off verbose:gc, it's very noisy when Xms < Xmx
    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        super.getConfig(builder);
        // This takes effect via vespa-start-container-daemon:configure_gcopts
        builder.jvm.verbosegc(false);
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        builder.maxthreads(10);
    }

    private void addLogHandler() {
        Handler<?> logHandler = Handler.fromClassName(ContainerCluster.LOG_HANDLER_CLASS);
        logHandler.addServerBindings("*://*/logs");
        addComponent(logHandler);
    }

}
