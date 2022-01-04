// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.orchestrator.policy;

import com.yahoo.vespa.applicationmodel.ClusterId;
import com.yahoo.vespa.applicationmodel.InfrastructureApplication;
import com.yahoo.vespa.applicationmodel.ServiceType;

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
                .addApplicationParams(InfrastructureApplication.CONTROLLER_HOST,
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
                .addApplicationParams(InfrastructureApplication.CONTROLLER,
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
                .addApplicationParams(InfrastructureApplication.CONFIG_SERVER_HOST,
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
                .addApplicationParams(InfrastructureApplication.CONFIG_SERVER,
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
                .addApplicationParams(InfrastructureApplication.PROXY_HOST,
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
                .addApplicationParams(InfrastructureApplication.PROXY,
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
