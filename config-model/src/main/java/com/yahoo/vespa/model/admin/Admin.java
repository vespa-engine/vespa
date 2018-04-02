// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.ConfigProxy;
import com.yahoo.vespa.model.ConfigSentinel;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.Logd;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.admin.monitoring.builder.Metrics;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProvider;
import com.yahoo.vespa.model.filedistribution.FileDistributor;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * This is the admin pseudo-plugin of the Vespa model, responsible for
 * creating all admin services.
 *
 * @author gjoranv
 */
public class Admin extends AbstractConfigProducer implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Monitoring monitoring;
    private final Metrics metrics;
    private final Map<String, MetricsConsumer> legacyMetricsConsumers;
    private final List<Configserver> configservers = new ArrayList<>();

    private final List<Slobrok> slobroks = new ArrayList<>();
    private Configserver defaultConfigserver;
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
    private ContainerCluster clusterControllers;

    private ZooKeepersConfigProvider zooKeepersConfigProvider;
    private FileDistributionConfigProducer fileDistribution;
    private final boolean multitenant;

    public Admin(AbstractConfigProducer parent,
                 Monitoring monitoring,
                 Metrics metrics,
                 Map<String, MetricsConsumer> legacyMetricsConsumers,
                 boolean multitenant,
                 FileDistributionConfigProducer fileDistributionConfigProducer) {
        super(parent, "admin");
        this.monitoring = monitoring;
        this.metrics = metrics;
        this.legacyMetricsConsumers = legacyMetricsConsumers;
        this.multitenant = multitenant;
        this.fileDistribution = fileDistributionConfigProducer;
    }

    public Configserver getConfigserver() { return defaultConfigserver; }

    /** Returns the configured monitoring endpoint, or null if not configured */
    public Monitoring getMonitoring() {
        return monitoring;
    }

    public Metrics getUserMetrics() { return metrics; }

    /** Returns the configured userMetricConsumers. Empty if not configured */
    public Map<String, MetricsConsumer> getLegacyUserMetricsConsumers(){
        return legacyMetricsConsumers;
    }

    /** Returns a list of all config servers */
    public List<Configserver> getConfigservers() {
        return configservers;
    }

    public List<ConfigServerSpec> getConfigServerSpecs() {
        List<ConfigServerSpec> serverSpecs = new ArrayList<>();
        for (Configserver server : getConfigservers()) {
            serverSpecs.add(server.getConfigServerSpec());
        }
        return serverSpecs;
    }

    public void removeSlobroks() { slobroks.clear(); }

    /** Returns an immutable list of the slobroks in this */
    public List<Slobrok> getSlobroks() { return Collections.unmodifiableList(slobroks); }

    public void setLogserver(Logserver logserver) { this.logserver = logserver; }

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

    public ContainerCluster getClusterControllers() { return clusterControllers; }

    public void setClusterControllers(ContainerCluster clusterControllers) {
        if (multitenant) throw new RuntimeException("Should not use admin cluster controller in a multitenant environment");
        this.clusterControllers = clusterControllers;
    }

    public ZooKeepersConfigProvider getZooKeepersConfigProvider() {
        return zooKeepersConfigProvider;
    }

    public void getConfig(LogdConfig.Builder builder) {
        builder.
            logserver(new LogdConfig.Logserver.Builder().
                    use(!isHostedVespa()).
                    host(logserver.getHostName()).
                    port(logserver.getRelativePort(1)));
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
            slobroks.addAll(createDefaultSlobrokSetup());
        for (HostResource host : hosts) {
            if (!host.getHost().runsConfigServer()) {
                addCommonServices(host, deployState);
            }
        }
    }
    private void addCommonServices(HostResource host, DeployState deployState) {
        addConfigSentinel(host, deployState.getProperties().applicationId(), deployState.zone());
        addLogd(host);
        addConfigProxy(host);
        addFileDistribution(host);
        if (logForwarderConfig != null) {
            addLogForwarder(host);
        }
    }

    private void addConfigSentinel(HostResource host, ApplicationId applicationId, Zone zone) {
        ConfigSentinel configSentinel = new ConfigSentinel(host.getHost(), applicationId, zone);
        addAndInitializeService(host, configSentinel);
        host.getHost().setConfigSentinel(configSentinel);
    }

    private void addLogForwarder(HostResource host) {
        addAndInitializeService(host, new LogForwarder(host.getHost(), logForwarderConfig));
    }

    private void addLogd(HostResource host) {
        addAndInitializeService(host, new Logd(host.getHost()));
    }

    private void addConfigProxy(HostResource host) {
        addAndInitializeService(host, new ConfigProxy(host.getHost()));
    }

    public void addAndInitializeService(HostResource host, AbstractService service) {
        service.setHostResource(host);
        service.initService();
    }

    private void addFileDistribution(HostResource host) {
        FileDistributor fileDistributor = fileDistribution.getFileDistributor();
        HostResource deployHost = getHostSystem().getHostByHostname(fileDistributor.fileSourceHost());
        if (deployHostIsMissing(deployHost)) {
            throw new RuntimeException("Could not find host in the application's host system: '" +
                                       fileDistributor.fileSourceHost() + "'. Hostsystem=" + getHostSystem());
        }

        FileDistributionConfigProvider configProvider =
                new FileDistributionConfigProvider(fileDistribution,
                                                   fileDistributor,
                                                   host == deployHost,
                                                   host.getHost());
        fileDistribution.addFileDistributionConfigProducer(host.getHost(), configProvider);
    }

    private boolean deployHostIsMissing(HostResource deployHost) {
        return !multitenant && deployHost == null;
    }

    // If not configured by user: Use default setup: max 3 slobroks, 1 on the default configserver host
    private List<Slobrok> createDefaultSlobrokSetup() {
        List<HostResource> hosts = getHostSystem().getHosts();
        List<Slobrok> slobs = new ArrayList<>();
        if (logserver != null) {
            Slobrok slobrok = new Slobrok(this, 0);
            addAndInitializeService(logserver.getHostResource(), slobrok);
            slobs.add(slobrok);
        }

        int n = 0;
        while ((n < hosts.size()) && (slobs.size() < 3)) {
            HostResource host = hosts.get(n);
            if ((logserver== null || host != logserver.getHostResource()) && ! host.getHost().runsConfigServer()) {
                Slobrok newSlobrok = new Slobrok(this, slobs.size());
                addAndInitializeService(host, newSlobrok);
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
