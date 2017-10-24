// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.configserver;

import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.RegionName;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.StatisticsConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.jdisc.metrics.yamasconsumer.cloud.ScoreBoardConfig;
import com.yahoo.net.HostName;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions;
import com.yahoo.vespa.model.container.configserver.option.CloudConfigOptions.ConfigServer;

import java.util.Optional;

/**
 * Represents a config server cluster.
 *
 * @author lulf
 * @since 5.15
 */
public class ConfigserverCluster extends AbstractConfigProducer
        implements
        ZookeeperServerConfig.Producer,
        ConfigserverConfig.Producer,
        ScoreBoardConfig.Producer,
        StatisticsConfig.Producer,
        HealthMonitorConfig.Producer {
    private final CloudConfigOptions options;
    private ContainerCluster containerCluster;

    public ConfigserverCluster(AbstractConfigProducer parent, String subId, CloudConfigOptions options) {
        super(parent, subId);
        this.options = options;
    }

    public void setContainerCluster(ContainerCluster containerCluster) {
        this.containerCluster = containerCluster;

        // If we are in a config server cluster the correct zone is propagated through cloud config options,
        // not through config to deployment options (see StandaloneContainerApplication.scala),
        // so we need to propagate the zone options into the container from here
        Environment environment = options.environment().isPresent() ? Environment.from(options.environment().get()) : Environment.defaultEnvironment();
        RegionName region = options.region().isPresent() ? RegionName.from(options.region().get()) : RegionName.defaultName();
        SystemName system = options.system().isPresent() ? SystemName.from(options.system().get()) : SystemName.defaultSystem();
        containerCluster.setZone(new Zone(system, environment, region));
    }

    @Override
    public void getConfig(ZookeeperServerConfig.Builder builder) {
        String myhostname = HostName.getLocalhost();
        int myid = 0;
        int i = 0;
        for (ConfigServer server : getConfigServers()) {
            if (server.hostName.equals(myhostname)) {
                myid = i;
            }
            builder.server(getZkServer(server, i));
            i++;
        }
        builder.myid(myid);
        if (options.zookeeperClientPort().isPresent()) {
            builder.clientPort(options.zookeeperClientPort().get());
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
        if (options.payloadCompressionType().isPresent()) {
            builder.payloadCompressionType(ConfigserverConfig.PayloadCompressionType.Enum.valueOf(options.payloadCompressionType().get()));
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
        if (options.defaultFlavor().isPresent()) {
            builder.defaultFlavor(options.defaultFlavor().get());
        }
        if (options.defaultAdminFlavor().isPresent()) {
            builder.defaultAdminFlavor(options.defaultAdminFlavor().get());
        }
        if (options.defaultContainerFlavor().isPresent()) {
            builder.defaultContainerFlavor(options.defaultContainerFlavor().get());
        }
        if (options.defaultContentFlavor().isPresent()) {
            builder.defaultContentFlavor(options.defaultContentFlavor().get());
        }

        builder.serverId(HostName.getLocalhost());
        if (!containerCluster.getHttp().getHttpServer().getConnectorFactories().isEmpty()) {
            builder.httpport(containerCluster.getHttp().getHttpServer().getConnectorFactories().get(0).getListenPort());
        }
        if (options.useVespaVersionInRequest().isPresent()) {
            builder.useVespaVersionInRequest(options.useVespaVersionInRequest().get());
        } else if (options.multiTenant().isPresent()) {
            builder.useVespaVersionInRequest(options.multiTenant().get());
        }
        if (options.hostedVespa().isPresent()) {
            builder.hostedVespa(options.hostedVespa().get());
        }
        if (options.numParallelTenantLoaders().isPresent()) {
            builder.numParallelTenantLoaders(options.numParallelTenantLoaders().get());
        }
        if (options.dockerRegistry().isPresent()) {
            builder.dockerRegistry(options.dockerRegistry().get());
        }
        if (options.dockerVespaBaseImage().isPresent()) {
            builder.dockerVespaBaseImage(options.dockerVespaBaseImage().get());
        }
        if (options.loadBalancerAddress().isPresent()) {
            builder.loadBalancerAddress(options.loadBalancerAddress().get());
        }
    }

    private String[] getConfigModelPluginDirs() {
        if (options.configModelPluginDirs().length > 0) {
            return options.configModelPluginDirs();
        } else {
            return new String[]{Defaults.getDefaults().underVespaHome("lib/jars/config-models")};
        }
    }

    private ConfigServer[] getConfigServers() {
        if (options.allConfigServers().length > 0) {
            return options.allConfigServers();
        } else {
            return new ConfigServer[]{new ConfigServer(HostName.getLocalhost(), Optional.<Integer>empty()) };
        }
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
    public void getConfig(ScoreBoardConfig.Builder builder) {
        builder.applicationName("configserver");
        builder.flushTime(60);
        builder.step(60);
    }

    @Override
    public void getConfig(StatisticsConfig.Builder builder) {
        builder.collectionintervalsec(60.0);
        builder.loggingintervalsec(60.0);
    }

    @Override
    public void getConfig(HealthMonitorConfig.Builder builder) {
        builder.snapshot_interval(60.0);
    }

}
