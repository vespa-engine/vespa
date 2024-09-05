// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.configserver.option;

import java.util.Optional;

/**
 * @author Tony Vaagenes
 */
public interface ConfigOptions {

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
    default Optional<Integer> zookeeperClientPort() { return Optional.empty(); }  // TODO: Remove when 8.405 is oldest version in use
    default String[] configModelPluginDirs() { return new String[0]; } // TODO: Remove when 8.404 is oldest version in use
    default Optional<Long> sessionLifeTimeSecs() { return Optional.empty(); }  // TODO: Remove when 8.405 is oldest version in use
    Optional<Long> zookeeperBarrierTimeout(); //in seconds
    default Optional<Integer> zookeeperElectionPort() { return Optional.empty(); }  // TODO: Remove when 8.405 is oldest version in use
    default Optional<Integer> zookeeperQuorumPort() { return Optional.empty(); }  // TODO: Remove when 8.405 is oldest version in use
    Optional<String> environment();
    Optional<String> region();
    Optional<String> system();
    default Optional<String> cloud() { return Optional.empty(); }
    Optional<Boolean> useVespaVersionInRequest();
    default Optional<String> loadBalancerAddress() { return Optional.empty(); } // TODO: Remove when 8.406 is oldest version in use
    default Optional<String> athenzDnsSuffix() { return Optional.empty(); } // TODO: Remove when 8.406 is oldest version in use
    default Optional<String> ztsUrl() { return Optional.empty(); } // TODO: Remove when 8.406 is oldest version in use
    String zooKeeperSnapshotMethod();
    Integer zookeeperJuteMaxBuffer(); // in bytes

}
