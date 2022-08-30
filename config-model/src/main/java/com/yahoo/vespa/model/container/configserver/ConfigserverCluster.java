// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.configserver;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions;
import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions.ConfigServer;

import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Represents a config server cluster.
 *
 * @author Ulf Lilleengen
 */
public class ConfigserverCluster extends AbstractConfigProducer
        implements
        ConfigserverConfig.Producer,
        CuratorConfig.Producer,
        HealthMonitorConfig.Producer,
        VipStatusConfig.Producer,
        ZookeeperServerConfig.Producer {

    private final CloudConfigOptions options;
    private ContainerCluster<?> containerCluster;

    public ConfigserverCluster(AbstractConfigProducer<?> parent, String subId, CloudConfigOptions options) {
        super(parent, subId);
        this.options = options;
    }

    public void setContainerCluster(ContainerCluster<?> containerCluster) {
        this.containerCluster = containerCluster;

        // If we are in a config server cluster the correct zone is propagated through cloud config options,
        // not through config to deployment options (see StandaloneContainerApplication.java),
        // so we need to propagate the zone options into the container from here
        Environment environment = options.environment().isPresent() ? Environment.from(options.environment().get()) : Environment.defaultEnvironment();
        RegionName region = options.region().isPresent() ? RegionName.from(options.region().get()) : RegionName.defaultName();
        SystemName system = options.system().isPresent() ? SystemName.from(options.system().get()) : SystemName.defaultSystem();
        containerCluster.setZone(new Zone(system, environment, region));
    }

    @Override
    public void getConfig(ZookeeperServerConfig.Builder builder) {
        ConfigServer[] configServers = getConfigServers();
        int[] zookeeperIds = getConfigServerZookeeperIds();

        if (configServers.length != zookeeperIds.length) {
            throw new IllegalArgumentException(String.format("Number of provided config server hosts (%d) must be the " +
                    "same as number of provided config server zookeeper ids (%d)",
                    configServers.length, zookeeperIds.length));
        }

        String myhostname = HostName.getLocalhost();
        // TODO: Server index should be in interval [1, 254] according to doc,
        // however, we cannot change this id for an existing server
        for (int i = 0; i < configServers.length; i++) {
            if (zookeeperIds[i] < 0) {
                throw new IllegalArgumentException(String.format("Zookeeper ids cannot be negative, was %d for %s",
                        zookeeperIds[i], configServers[i].hostName));
            }
            if (configServers[i].hostName.equals(myhostname)) {
                builder.myid(zookeeperIds[i]);
            }
            builder.server(getZkServer(configServers[i], zookeeperIds[i]));
        }

        if (options.zookeeperClientPort().isPresent()) {
            builder.clientPort(options.zookeeperClientPort().get());
        }
        if (options.hostedVespa().orElse(false)) {
            builder.vespaTlsConfigFile(Defaults.getDefaults().underVespaHome("conf/zookeeper/tls.conf.json"));
        }
    }

    @Override
    public void getConfig(ConfigserverConfig.Builder builder) {
        for (String pluginDir : getConfigModelPluginDirs()) {
            builder.configModelPluginDir(pluginDir);
        }
        if (options.sessionLifeTimeSecs().isPresent()) {
            builder.sessionLifetime(options.sessionLifeTimeSecs().get());
        }
        if (options.zookeeperBarrierTimeout().isPresent()) {
            builder.zookeeper(new ConfigserverConfig.Zookeeper.Builder().barrierTimeout(options.zookeeperBarrierTimeout().get()));
        }
        if (options.rpcPort().isPresent()) {
            builder.rpcport(options.rpcPort().get());
        }
        if (options.multiTenant().isPresent()) {
            builder.multitenant(options.multiTenant().get());
        }
        for (ConfigServer server : getConfigServers()) {
            ConfigserverConfig.Zookeeperserver.Builder zkBuilder = new ConfigserverConfig.Zookeeperserver.Builder();
            zkBuilder.hostname(server.hostName);
            if (options.zookeeperClientPort().isPresent()) {
                zkBuilder.port(options.zookeeperClientPort().get());
            }
            builder.zookeeperserver(zkBuilder);
        }
        if (options.environment().isPresent()) {
            builder.environment(options.environment().get());
        }
        if (options.region().isPresent()) {
            builder.region(options.region().get());
        }
        if (options.system().isPresent()) {
            builder.environment(options.system().get());
        }

        builder.serverId(HostName.getLocalhost());
        if (!containerCluster.getHttp().getHttpServer().get().getConnectorFactories().isEmpty()) {
            builder.httpport(containerCluster.getHttp().getHttpServer().get().getConnectorFactories().get(0).getListenPort());
        }
        if (options.useVespaVersionInRequest().isPresent()) {
            builder.useVespaVersionInRequest(options.useVespaVersionInRequest().get());
        } else if (options.multiTenant().isPresent()) {
            builder.useVespaVersionInRequest(options.multiTenant().get());
        }
        if (options.hostedVespa().isPresent()) {
            builder.hostedVespa(options.hostedVespa().get());
        }
        if (options.loadBalancerAddress().isPresent()) {
            builder.loadBalancerAddress(options.loadBalancerAddress().get());
        }
        options.athenzDnsSuffix().ifPresent(builder::athenzDnsSuffix);
        options.ztsUrl().ifPresent(builder::ztsUrl);
    }

    private String[] getConfigModelPluginDirs() {
        if (options.configModelPluginDirs().length > 0) {
            return options.configModelPluginDirs();
        } else {
            return new String[]{Defaults.getDefaults().underVespaHome("lib/jars/config-models")};
        }
    }

    private ConfigServer[] getConfigServers() {
        return Optional.of(options.allConfigServers())
                .filter(configServers -> configServers.length > 0)
                .orElseGet(() -> new ConfigServer[]{new ConfigServer(HostName.getLocalhost(), Optional.empty())});
    }

    private int[] getConfigServerZookeeperIds() {
        return Optional.of(options.configServerZookeeperIds())
                .filter(ids -> ids.length > 0)
                .orElseGet(() -> IntStream.range(0, getConfigServers().length).toArray());
    }

    private ZookeeperServerConfig.Server.Builder getZkServer(ConfigServer server, int id) {
        ZookeeperServerConfig.Server.Builder builder = new ZookeeperServerConfig.Server.Builder();
        if (options.zookeeperElectionPort().isPresent()) {
            builder.electionPort(options.zookeeperElectionPort().get());
        }
        if (options.zookeeperQuorumPort().isPresent()) {
            builder.quorumPort(options.zookeeperQuorumPort().get());
        }
        builder.hostname(server.hostName);
        builder.id(id);
        return builder;
    }

    @Override
    public void getConfig(HealthMonitorConfig.Builder builder) {
        builder.snapshot_interval(60.0);
    }

    @Override
    public void getConfig(VipStatusConfig.Builder builder) {
        builder.initiallyInRotation(false);
    }

    @Override
    public void getConfig(CuratorConfig.Builder builder) {
        for (ConfigServer server : getConfigServers()) {
            CuratorConfig.Server.Builder curatorBuilder = new CuratorConfig.Server.Builder();
            curatorBuilder.hostname(server.hostName);
            if (options.zookeeperClientPort().isPresent()) {
                curatorBuilder.port(options.zookeeperClientPort().get());
            }
            builder.server(curatorBuilder);
        }
        builder.zookeeperLocalhostAffinity(true);
    }

}
