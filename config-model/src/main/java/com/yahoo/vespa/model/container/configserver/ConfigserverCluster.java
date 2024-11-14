// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.configserver;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;

import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.core.VipStatusConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.configserver.option.ConfigOptions;
import com.yahoo.vespa.model.container.configserver.option.ConfigOptions.ConfigServer;

import java.util.Optional;
import java.util.stream.IntStream;

import static com.yahoo.config.model.api.ModelContext.FeatureFlags;

/**
 * Represents a config server cluster.
 *
 * @author Ulf Lilleengen
 */
public class ConfigserverCluster extends TreeConfigProducer
        implements
        ConfigserverConfig.Producer,
        CuratorConfig.Producer,
        HealthMonitorConfig.Producer,
        VipStatusConfig.Producer,
        ZookeeperServerConfig.Producer {

    private final ConfigOptions options;
    private final FeatureFlags featureFlags;
    private ContainerCluster<?> containerCluster;

    public ConfigserverCluster(TreeConfigProducer<?> parent, String subId, ConfigOptions options, FeatureFlags featureFlags) {
        super(parent, subId);
        this.options = options;
        this.featureFlags = featureFlags;
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

        if (options.hostedVespa().orElse(false)) {
            builder.vespaTlsConfigFile(Defaults.getDefaults().underVespaHome("var/zookeeper/conf/tls.conf.json"));
        }

        boolean isHostedVespa = options.hostedVespa().orElse(false);
        builder.dynamicReconfiguration(isHostedVespa);
        builder.reconfigureEnsemble(!isHostedVespa);
        builder.snapshotMethod(options.zooKeeperSnapshotMethod());
        builder.juteMaxBuffer(options.zookeeperJuteMaxBuffer());
        builder.preAllocSizeKb(featureFlags.zookeeperPreAllocSize());
    }

    @Override
    public void getConfig(ConfigserverConfig.Builder builder) {
        for (String pluginDir : getConfigModelPluginDirs()) {
            builder.configModelPluginDir(pluginDir);
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
    }

    private String[] getConfigModelPluginDirs() {
        return new String[]{Defaults.getDefaults().underVespaHome("lib/jars/config-models")};
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
            builder.server(curatorBuilder);
        }
        builder.zookeeperLocalhostAffinity(true);
        builder.juteMaxBuffer(options.zookeeperJuteMaxBuffer());
    }

}
