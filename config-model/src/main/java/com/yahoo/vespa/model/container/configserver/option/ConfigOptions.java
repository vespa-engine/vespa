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
    Optional<Long> zookeeperBarrierTimeout(); //in seconds
    Optional<String> environment();
    Optional<String> region();
    Optional<String> system();
    default Optional<String> cloud() { return Optional.empty(); }
    Optional<Boolean> useVespaVersionInRequest();
    default Optional<String> loadBalancerAddress() { return Optional.empty(); } // TODO: Remove when 8.409 is oldest version in use
    default Optional<String> athenzDnsSuffix() { return Optional.empty(); } // TODO: Remove when 8.409 is oldest version in use
    default Optional<String> ztsUrl() { return Optional.empty(); } // TODO: Remove when 8.409 is oldest version in use
    String zooKeeperSnapshotMethod();
    Integer zookeeperJuteMaxBuffer(); // in bytes

}
