// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.ConfigProxy;
import com.yahoo.vespa.model.ConfigSentinel;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.Logd;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainerCluster;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainer;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster;
import com.yahoo.vespa.model.admin.monitoring.MetricSet;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.admin.monitoring.builder.Metrics;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProvider;
import com.yahoo.vespa.model.filedistribution.FileDistributor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static com.yahoo.vespa.model.admin.monitoring.MetricSet.empty;

/**
 * This is the admin pseudo-plugin of the Vespa model, responsible for
 * creating all admin services.
 *
 * @author gjoranv
 */
public class Admin extends AbstractConfigProducer<Admin> implements Serializable {

    private static final long serialVersionUID = 1L;

    private final boolean isHostedVespa;
    private final Monitoring monitoring;
    private final List<Configserver> configservers = new ArrayList<>();

    private final Metrics metrics;
    private MetricsProxyContainerCluster metricsProxyCluster;
    private MetricSet additionalDefaultMetrics = empty();

    private final List<Slobrok> slobroks = new ArrayList<>();
    private Configserver defaultConfigserver;

    /** The log server, or null if none */
    private Logserver logserver;

    private LogForwarder.Config logForwarderConfig = null;

    private ApplicationType applicationType = ApplicationType.DEFAULT;

    public void setLogForwarderConfig(LogForwarder.Config cfg) {
        this.logForwarderConfig = cfg;
    }

    /**
     * The single cluster controller cluster shared by all content clusters by default when not multitenant.
     * If multitenant, this is null.
     */
    private ClusterControllerContainerCluster clusterControllers;

     // Cluster of logserver containers. If enabled, exactly one container is running on each logserver host.
    private Optional<LogserverContainerCluster> logServerContainerCluster = Optional.empty();

    private ZooKeepersConfigProvider zooKeepersConfigProvider;
    private final FileDistributionConfigProducer fileDistribution;
    private final boolean multitenant;

    public Admin(AbstractConfigProducer parent,
                 Monitoring monitoring,
                 Metrics metrics,
                 boolean multitenant,
                 FileDistributionConfigProducer fileDistributionConfigProducer,
                 boolean isHostedVespa) {
        super(parent, "admin");
        this.isHostedVespa = isHostedVespa;
        this.monitoring = monitoring;
        this.metrics = metrics;
        this.multitenant = multitenant;
        this.fileDistribution = fileDistributionConfigProducer;
    }

    public Configserver getConfigserver() { return defaultConfigserver; }

    /** Returns the configured monitoring endpoint, or null if not configured */
    public Monitoring getMonitoring() {
        return monitoring;
    }

    public Metrics getUserMetrics() { return metrics; }

    public MetricsProxyContainerCluster getMetricsProxyCluster() {
        return metricsProxyCluster;
    }

    /** Used by model amenders */
    public void setAdditionalDefaultMetrics(MetricSet additionalDefaultMetrics) {
        if (additionalDefaultMetrics == null) return;
        this.additionalDefaultMetrics = additionalDefaultMetrics;
    }

    public MetricSet getAdditionalDefaultMetrics() {
        return additionalDefaultMetrics;
    }

    /** Returns a list of all config servers */
    public List<Configserver> getConfigservers() {
        return configservers;
    }

    /** Returns an immutable list of the slobroks in this */
    public List<Slobrok> getSlobroks() { return Collections.unmodifiableList(slobroks); }

    public void setLogserver(Logserver logserver) { this.logserver = logserver; }

    /** Returns the log server for this, or null if none */
    public Logserver getLogserver() { return logserver; }

    public void addConfigservers(List<Configserver> configservers) {
        this.configservers.addAll(configservers);
        if (this.configservers.size() > 0) {
            this.defaultConfigserver = configservers.get(0);
        }
        this.zooKeepersConfigProvider = new ZooKeepersConfigProvider(configservers);
    }

    public void addSlobroks(List<Slobrok> slobroks) {
        this.slobroks.addAll(slobroks);
    }

    public ClusterControllerContainerCluster getClusterControllers() { return clusterControllers; }

    public void setClusterControllers(ClusterControllerContainerCluster clusterControllers) {
        this.clusterControllers = clusterControllers;
    }

    public Optional<LogserverContainerCluster> getLogServerContainerCluster() { return logServerContainerCluster; }

    public void setLogserverContainerCluster(LogserverContainerCluster logServerContainerCluster) {
        this.logServerContainerCluster = Optional.of(logServerContainerCluster);
    }

    public ZooKeepersConfigProvider getZooKeepersConfigProvider() {
        return zooKeepersConfigProvider;
    }

    public void getConfig(LogdConfig.Builder builder) {
        if (logserver == null) {
            builder.logserver(new LogdConfig.Logserver.Builder().use(false));
        }
        else {
            builder.
                logserver(new LogdConfig.Logserver.Builder().
                        userpc(true).
                        use(logServerContainerCluster.isPresent() || !isHostedVespa).
                        host(logserver.getHostName()).
                        rpcport(logserver.getRelativePort(0)));
        }
     }

    public void getConfig(SlobroksConfig.Builder builder) {
        for (Slobrok slob : slobroks) {
            builder.
                slobrok(new SlobroksConfig.Slobrok.Builder().
                        connectionspec(slob.getConnectionSpec()));
        }
    }

    public void getConfig(ZookeepersConfig.Builder builder) {
        zooKeepersConfigProvider.getConfig(builder);
    }

    public FileDistributionConfigProducer getFileDistributionConfigProducer() {
        return fileDistribution;
    }

    public List<HostResource> getClusterControllerHosts() {
        List<HostResource> hosts = new ArrayList<>();
        if (multitenant) {
            if (logserver != null)
                hosts.add(logserver.getHostResource());
        } else {
            for (Configserver configserver : getConfigservers()) {
                hosts.add(configserver.getHostResource());
            }
        }
        return hosts;
    }

    /**
     * Adds services to all hosts in the system.
     */
    public void addPerHostServices(List<HostResource> hosts, DeployState deployState) {
        if (slobroks.isEmpty()) // TODO: Move to caller
            slobroks.addAll(createDefaultSlobrokSetup(deployState.getDeployLogger()));

        addMetricsProxyCluster(hosts, deployState);

        for (HostResource host : hosts) {
            if (!host.getHost().runsConfigServer()) {
                addCommonServices(host, deployState);
            }
        }
    }

    private void addMetricsProxyCluster(List<HostResource> hosts, DeployState deployState) {
        metricsProxyCluster = new MetricsProxyContainerCluster(this, "metrics", deployState);
        int index = 0;
        for (var host : hosts) {
            // Send hostname to be used in configId (instead of index), as the sorting of hosts seems to be unstable
            // between config changes, even when the set of hosts is unchanged.
            var container = new MetricsProxyContainer(metricsProxyCluster, host, index, deployState);
            addAndInitializeService(deployState.getDeployLogger(), host, container);
            metricsProxyCluster.addContainer(container);
        }
    }

    private void addCommonServices(HostResource host, DeployState deployState) {
        addConfigSentinel(deployState.getDeployLogger(), host, deployState.getProperties().applicationId(), deployState.zone());
        addLogd(deployState.getDeployLogger(), host);
        addConfigProxy(deployState.getDeployLogger(), host);
        addFileDistribution(host);
        if (logForwarderConfig != null) {
            addLogForwarder(deployState.getDeployLogger(), host);
        }
    }

    private void addConfigSentinel(DeployLogger deployLogger, HostResource host, ApplicationId applicationId, Zone zone) {
        ConfigSentinel configSentinel = new ConfigSentinel(host.getHost(), applicationId, zone);
        addAndInitializeService(deployLogger, host, configSentinel);
        host.getHost().setConfigSentinel(configSentinel);
    }

    private void addLogForwarder(DeployLogger deployLogger, HostResource host) {
        addAndInitializeService(deployLogger, host, new LogForwarder(host.getHost(), logForwarderConfig));
    }

    private void addLogd(DeployLogger deployLogger, HostResource host) {
        addAndInitializeService(deployLogger, host, new Logd(host.getHost()));
    }

    private void addConfigProxy(DeployLogger deployLogger, HostResource host) {
        addAndInitializeService(deployLogger, host, new ConfigProxy(host.getHost()));
    }

    public void addAndInitializeService(DeployLogger deployLogger, HostResource host, AbstractService service) {
        service.setHostResource(host);
        service.initService(deployLogger);
    }

    private void addFileDistribution(HostResource host) {
        FileDistributor fileDistributor = fileDistribution.getFileDistributor();
        HostResource hostResource = hostSystem().getHostByHostname(fileDistributor.fileSourceHost());
        if (hostResource == null && ! multitenant)
            throw new IllegalArgumentException("Could not find " + fileDistributor.fileSourceHost() +
                                               " in the application's " + hostSystem());

        FileDistributionConfigProvider configProvider =
                new FileDistributionConfigProvider(fileDistribution,
                                                   fileDistributor,
                                                   host == hostResource,
                                                   host.getHost());
        fileDistribution.addFileDistributionConfigProducer(host.getHost(), configProvider);
    }

    // If not configured by user: Use default setup: max 3 slobroks, 1 on the default configserver host
    private List<Slobrok> createDefaultSlobrokSetup(DeployLogger deployLogger) {
        List<HostResource> hosts = hostSystem().getHosts();
        List<Slobrok> slobs = new ArrayList<>();
        if (logserver != null) {
            Slobrok slobrok = new Slobrok(this, 0);
            addAndInitializeService(deployLogger, logserver.getHostResource(), slobrok);
            slobs.add(slobrok);
        }

        int n = 0;
        while ((n < hosts.size()) && (slobs.size() < 3)) {
            HostResource host = hosts.get(n);
            if ((logserver== null || host != logserver.getHostResource()) && ! host.getHost().runsConfigServer()) {
                Slobrok newSlobrok = new Slobrok(this, slobs.size());
                addAndInitializeService(deployLogger, host, newSlobrok);
                slobs.add(newSlobrok);
            }
            n++;
        }
        int j = 0;
        for (Slobrok s : slobs) {
            s.setProp("index", j);
            j++;
        }
        return slobs;
    }

    public boolean multitenant() {
        return multitenant;
    }

    public void setApplicationType(ApplicationType applicationType) {
        this.applicationType = applicationType;
    }

    public ApplicationType getApplicationType() { return applicationType; }

}
