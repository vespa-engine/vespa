// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.deploy.DeployProperties;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.model.*;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.Yamas;
import com.yahoo.vespa.model.admin.monitoring.builder.Metrics;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.vespa.model.filedistribution.FileDistributor;
import com.yahoo.vespa.model.filedistribution.FileDistributorService;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.yahoo.vespa.model.HostResource;

/**
 * This is the admin pseudo-plugin of the Vespa model, responsible for
 * creating all admin services.
 *
 * @author gjoranv
 */
public class Admin extends AbstractConfigProducer implements Serializable {

    private static final long serialVersionUID = 1L;

    private final Yamas yamas;
    private final Metrics metrics;
    private final Map<String, MetricsConsumer> legacyMetricsConsumers;
    private final List<Configserver> configservers = new ArrayList<>();

    private final List<Slobrok> slobroks = new ArrayList<>();
    private Configserver defaultConfigserver;
    private Logserver logserver;

    /**
     * The single cluster controller cluster shared by all content clusters by default when not multitenant.
     * If multitenant, this is null.
     */
    private ContainerCluster clusterControllers;

    private ZooKeepersConfigProvider zooKeepersConfigProvider;
    private FileDistributionConfigProducer fileDistribution;
    private final boolean multitenant;

    public Admin(AbstractConfigProducer parent,
                 Yamas yamas,
                 Metrics metrics,
                 Map<String, MetricsConsumer> legacyMetricsConsumers,
                 boolean multitenant) {
        super(parent, "admin");
        this.yamas = yamas;
        this.metrics = metrics;
        this.legacyMetricsConsumers = legacyMetricsConsumers;
        this.multitenant = multitenant;
    }

    public Configserver getConfigserver() {
        return defaultConfigserver;
    }

    /** Returns the configured yamas end point. Is null if yamas is not configured */
    public Yamas getYamas() {
        return yamas;
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

    public void setFileDistribution(FileDistributionConfigProducer fileDistribution) {
        this.fileDistribution = fileDistribution;
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
    public void addPerHostServices(List<HostResource> hosts, DeployProperties properties) {
        if (slobroks.isEmpty()) // TODO: Move to caller
            slobroks.addAll(createDefaultSlobrokSetup());
        for (HostResource host : hosts) {
            if (!host.getHost().isMultitenant()) {
                addCommonServices(host, properties);
            }
        }
    }
    private void addCommonServices(HostResource host, DeployProperties properties) {
        addConfigSentinel(host, properties.applicationId(), properties.zone());
        addLogd(host);
        addConfigProxy(host);
        addFileDistribution(host);
    }

    private void addConfigSentinel(HostResource host, ApplicationId applicationId, Zone zone) {
        ConfigSentinel configSentinel = new ConfigSentinel(host.getHost(), applicationId, zone);
        addAndInitializeService(host, configSentinel);
        host.getHost().setConfigSentinel(configSentinel);
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

        FileDistributorService fds = new FileDistributorService(fileDistribution, host.getHost().getHostName(),
            fileDistribution.getFileDistributor(), fileDistribution.getOptions(), host == deployHost);
        fds.setHostResource(host);
        fds.initService();
        fileDistribution.addFileDistributionService(host.getHost(), fds);
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
            if ((logserver== null || host != logserver.getHostResource()) && ! host.getHost().isMultitenant()) {
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

}
