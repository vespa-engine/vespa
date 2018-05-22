// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.configserver.option;

import java.util.Optional;

/**
 * @author Tony Vaagenes
 */
public interface CloudConfigOptions {

    class ConfigServer {
        public final String hostName;
        public final Optional<Integer> port;

        public ConfigServer(String hostName, Optional<Integer> port) {
            this.hostName = hostName;
            this.port = port;
        }
    }


    Optional<Integer> rpcPort();
    Optional<Boolean> multiTenant();
    Optional<Boolean> hostedVespa();

    ConfigServer[] allConfigServers();
    Optional<Integer> zookeeperClientPort();
    String[] configModelPluginDirs();
    Optional<Long> sessionLifeTimeSecs();

    Optional<Long> zookeeperBarrierTimeout(); //in seconds
    Optional<Integer> zookeeperElectionPort();
    Optional<Integer> zookeeperQuorumPort();
    Optional<String> payloadCompressionType();
    Optional<String> environment();
    Optional<String> region();
    Optional<String> system();
    Optional<String> defaultFlavor();
    Optional<String> defaultAdminFlavor();
    Optional<String> defaultContainerFlavor();
    Optional<String> defaultContentFlavor();
    Optional<Boolean> useVespaVersionInRequest();
    Optional<Integer> numParallelTenantLoaders();
    Optional<String> loadBalancerAddress();
    Optional<String> athenzDnsSuffix();
    Optional<String> ztsUrl();
}
