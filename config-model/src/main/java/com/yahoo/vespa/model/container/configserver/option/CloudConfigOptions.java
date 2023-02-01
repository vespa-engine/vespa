// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
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
    int[] configServerZookeeperIds();
    Optional<Integer> zookeeperClientPort();
    String[] configModelPluginDirs();
    Optional<Long> sessionLifeTimeSecs();
    Optional<Long> zookeeperBarrierTimeout(); //in seconds
    Optional<Integer> zookeeperElectionPort();
    Optional<Integer> zookeeperQuorumPort();
    Optional<String> environment();
    Optional<String> region();
    Optional<String> system();
    default Optional<String> cloud() { return Optional.empty(); }
    Optional<Boolean> useVespaVersionInRequest();
    Optional<String> loadBalancerAddress();
    Optional<String> athenzDnsSuffix();
    Optional<String> ztsUrl();
    String zooKeeperSnapshotMethod();

}
