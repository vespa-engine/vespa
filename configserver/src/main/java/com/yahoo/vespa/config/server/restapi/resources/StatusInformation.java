// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.restapi.resources;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Status information of config server. Currently needs to convert generated configserver config to a POJO that can
 * be serialized to JSON.
 *
 * @author lulf
 * @since 5.21
 */
public class StatusInformation {

    public ConfigserverConfig configserverConfig;
    public List<String> modelVersions;

    public StatusInformation(com.yahoo.cloud.config.ConfigserverConfig configserverConfig, List<String> modelVersions) {
        this.configserverConfig = new ConfigserverConfig(configserverConfig);
        this.modelVersions = modelVersions;
    }

    public static class ConfigserverConfig {
        public final int rpcport;
        public final int numthreads;
        public final String zookeepercfg;
        public final Collection<ZooKeeperServer> zookeeeperserver;
        public final long zookeeperBarrierTimeout;
        public final Collection<String> configModelPluginDir;
        public final String configServerDBDir;
        public final int maxgetconfigclients;
        public final long sessionLifetime;
        public final String applicationDirectory;
        public final long masterGeneration;
        public final boolean multitenant;
        public final int numDelayedResponseThreads;
        public final com.yahoo.cloud.config.ConfigserverConfig.PayloadCompressionType.Enum payloadCompressionType;
        public final boolean useVespaVersionInRequest;
        public final String serverId;
        public final String region;
        public final String environment;


        public ConfigserverConfig(com.yahoo.cloud.config.ConfigserverConfig configserverConfig) {
            this.rpcport = configserverConfig.rpcport();
            this.numthreads = configserverConfig.numthreads();
            this.zookeepercfg = getDefaults().underVespaHome(configserverConfig.zookeepercfg());
            this.zookeeeperserver = configserverConfig.zookeeperserver().stream()
                    .map(zks -> new ZooKeeperServer(zks.hostname(), zks.port()))
                    .collect(Collectors.toList());
            this.zookeeperBarrierTimeout = configserverConfig.zookeeper().barrierTimeout();
            this.configModelPluginDir = configserverConfig.configModelPluginDir();
            this.configServerDBDir = getDefaults().underVespaHome(configserverConfig.configServerDBDir());
            this.maxgetconfigclients = configserverConfig.maxgetconfigclients();
            this.sessionLifetime = configserverConfig.sessionLifetime();
            this.applicationDirectory = getDefaults().underVespaHome(configserverConfig.applicationDirectory());
            this.masterGeneration = configserverConfig.masterGeneration();
            this.multitenant = configserverConfig.multitenant();
            this.numDelayedResponseThreads = configserverConfig.numDelayedResponseThreads();
            this.payloadCompressionType = configserverConfig.payloadCompressionType();
            this.useVespaVersionInRequest = configserverConfig.useVespaVersionInRequest();
            this.serverId = configserverConfig.serverId();
            this.region = configserverConfig.region();
            this.environment = configserverConfig.environment();
        }
    }

    public static class ZooKeeperServer {
        public final String hostname;
        public final int port;

        public ZooKeeperServer(String hostname, int port) {
            this.hostname = hostname;
            this.port = port;
        }
    }
}
