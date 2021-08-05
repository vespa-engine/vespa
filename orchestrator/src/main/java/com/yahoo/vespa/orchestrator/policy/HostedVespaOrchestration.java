// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.ServiceType;
import com.yahoo.vespa.service.duper.ConfigServerApplication;
import com.yahoo.vespa.service.duper.ConfigServerHostApplication;
import com.yahoo.vespa.service.duper.ControllerApplication;
import com.yahoo.vespa.service.duper.ControllerHostApplication;
import com.yahoo.vespa.service.duper.ProxyApplication;
import com.yahoo.vespa.service.duper.ProxyHostApplication;

/**
 * Creates orchestration parameters for hosted Vespa.
 *
 * @author hakonhall
 */
public class HostedVespaOrchestration {
    public static OrchestrationParams create(int numConfigServers, int numProxies) {
        // We'll create parameters for both the controller and config server applications, even though
        // only one of them is present, as (a) no harm is done by having the extra parameters, and
        // (b) it leads to simpler code below.

        return new OrchestrationParams.Builder()

                // Controller host
                .addApplicationParams(new ControllerHostApplication().getApplicationId(),
                                      new ApplicationParams
                                              .Builder()
                                              .add(ClusterId.CONTROLLER,
                                                   ServiceType.HOST_ADMIN,
                                                   new ClusterParams
                                                           .Builder()
                                                           .setSize(numConfigServers)
                                                           .build())
                                              .build())

                // Controller
                .addApplicationParams(new ControllerApplication().getApplicationId(),
                                      new ApplicationParams
                                              .Builder()
                                              .add(ClusterId.CONTROLLER,
                                                   ServiceType.CONTROLLER,
                                                   new ClusterParams
                                                           .Builder()
                                                           .setSize(numConfigServers)
                                                           .build())
                                              .build())

                // Config server host
                .addApplicationParams(new ConfigServerHostApplication().getApplicationId(),
                                      new ApplicationParams
                                              .Builder()
                                              .add(ClusterId.CONFIG_SERVER_HOST,
                                                   ServiceType.HOST_ADMIN,
                                                   new ClusterParams
                                                           .Builder()
                                                           .setSize(numConfigServers)
                                                           .build())
                                              .build())

                // Config server
                .addApplicationParams(new ConfigServerApplication().getApplicationId(),
                                      new ApplicationParams
                                              .Builder()
                                              .add(ClusterId.CONFIG_SERVER,
                                                   ServiceType.CONFIG_SERVER,
                                                   new ClusterParams
                                                           .Builder()
                                                           .setSize(numConfigServers)
                                                           .build())
                                              .build())

                // Proxy host
                .addApplicationParams(new ProxyHostApplication().getApplicationId(),
                                      new ApplicationParams
                                              .Builder()
                                              .add(ClusterId.PROXY_HOST,
                                                   ServiceType.HOST_ADMIN,
                                                   new ClusterParams
                                                           .Builder()
                                                           .setSize(numProxies)
                                                           .build())
                                              .build())

                // Proxy
                .addApplicationParams(new ProxyApplication().getApplicationId(),
                                      new ApplicationParams
                                              .Builder()
                                              .add(ClusterId.ROUTING,
                                                   ServiceType.CONTAINER,
                                                   new ClusterParams
                                                           .Builder()
                                                           .setSize(numProxies)
                                                           .build())
                                              .build())

                .build();
    }
}
