// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin;

import ai.vespa.metrics.set.MetricSet;
import com.yahoo.cloud.config.SlobroksConfig;
import com.yahoo.cloud.config.ZookeepersConfig;
import com.yahoo.cloud.config.log.LogdConfig;
import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.api.ModelContext.FeatureFlags;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.container.logging.LevelsModSpec;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.ConfigProxy;
import com.yahoo.vespa.model.ConfigSentinel;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.LogctlSpec;
import com.yahoo.vespa.model.Logd;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainerCluster;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainer;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainerCluster;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.admin.monitoring.builder.Metrics;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static ai.vespa.metrics.set.MetricSet.empty;


/**
 * This is the admin pseudo-plugin of the Vespa model, responsible for
 * creating all admin services.
 *
 * @author gjoranv
 */
public class Admin extends TreeConfigProducer<AnyConfigProducer> implements Serializable {

    private final boolean isHostedVespa;
    private final Monitoring monitoring;
    private final List<Configserver> configservers = new ArrayList<>();

    private final Metrics metrics;
    private MetricsProxyContainerCluster metricsProxyCluster;
    private MetricSet additionalDefaultMetrics = empty();
    private Set<MetricsConsumer> amendedMetricsConsumers = new HashSet<>();

    private final List<Slobrok> slobroks = new ArrayList<>();
    private Configserver defaultConfigserver;

    /** The log server, or null if none */
    private Logserver logserver;

    private LogForwarder.Config logForwarderConfig = null;
    private boolean logForwarderIncludeAdmin = false;

    private final ApplicationType applicationType;

    public void setLogForwarderConfig(LogForwarder.Config cfg, boolean includeAdmin) {
        this.logForwarderConfig = cfg;
        this.logForwarderIncludeAdmin = includeAdmin;
    }

    private final List<LogctlSpec> logctlSpecs = new ArrayList<>();

    /**
     * The single cluster controller cluster shared by all content clusters by default when not multitenant.
     * If multitenant, this is null.
     */
    private ClusterControllerContainerCluster clusterControllers;

     // Cluster of logserver containers. If enabled, exactly one container is running on each logserver host.
    private Optional<LogserverContainerCluster> logServerContainerCluster = Optional.empty();

    private ZooKeepersConfigProvider zooKeepersConfigProvider;
    private final boolean multitenant;
    private final FeatureFlags featureFlags;

    public Admin(TreeConfigProducer<AnyConfigProducer> parent,
                 Monitoring monitoring,
                 Metrics metrics,
                 boolean multitenant,
                 boolean isHostedVespa,
                 ApplicationType applicationType,
                 FeatureFlags featureFlags) {
        super(parent, "admin");
        this.isHostedVespa = isHostedVespa;
        this.monitoring = monitoring;
        this.metrics = metrics;
        this.multitenant = multitenant;
        this.applicationType = applicationType;
        this.logctlSpecs.addAll(defaultLogctlSpecs());
        this.featureFlags = featureFlags;
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

    public void setAmendedMetricsConsumers(Set<MetricsConsumer> amendedMetricsConsumers) {
        if (amendedMetricsConsumers == null) return;
        this.amendedMetricsConsumers = Set.copyOf(amendedMetricsConsumers);
    }

    public Set<MetricsConsumer> getAmendedMetricsConsumers() {
        return amendedMetricsConsumers;
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
        if ( ! this.configservers.isEmpty()) {
            this.defaultConfigserver = configservers.get(0);
        }
        this.zooKeepersConfigProvider = new ZooKeepersConfigProvider(configservers);
    }

    public void addSlobroks(List<Slobrok> slobroks) {
        this.slobroks.addAll(slobroks);
    }

    public ClusterControllerContainerCluster getClusterControllers() { return clusterControllers; }

    public void setClusterControllers(ClusterControllerContainerCluster clusterControllers, DeployState deployState) {
        this.clusterControllers = clusterControllers;
        if (isHostedVespa) {
            // Prefer to put Slobroks on the admin cluster running cluster controllers to avoid unnecessary
            // movement of the slobroks when there are changes to the content cluster nodes
            removeSlobroks();
            addSlobroks(createSlobroksOn(clusterControllers, deployState));
        }
    }

    private void removeSlobroks() {
        slobroks.forEach(Slobrok::remove);
        slobroks.clear();
    }

    private List<Slobrok> createSlobroksOn(ClusterControllerContainerCluster clusterControllers, DeployState deployState) {
        List<Slobrok> slobroks = new ArrayList<>();
        for (ClusterControllerContainer clusterController : clusterControllers.getContainers()) {
            Slobrok slobrok = new Slobrok(this, clusterController.index(), deployState.featureFlags());
            slobrok.setHostResource(clusterController.getHostResource());
            slobroks.add(slobrok);
            slobrok.initService(deployState);
        }
        return slobroks;
    }

    public Optional<LogserverContainerCluster> getLogServerContainerCluster() { return logServerContainerCluster; }

    public void setLogserverContainerCluster(LogserverContainerCluster logServerContainerCluster) {
        this.logServerContainerCluster = Optional.of(logServerContainerCluster);
    }

    public ZooKeepersConfigProvider getZooKeepersConfigProvider() {
        return zooKeepersConfigProvider;
    }

    public void getConfig(LogdConfig.Builder builder) {
        var forwardAllLogLevels = isHostedVespa && featureFlags.forwardAllLogLevels();
        if (logserver == null) {
            builder.logserver(new LogdConfig.Logserver.Builder().use(false));
        }
        else {
            builder.logserver(new LogdConfig.Logserver.Builder().
                        use(logServerContainerCluster.isPresent() || !isHostedVespa).
                        host(logserver.getHostName()).
                        rpcport(logserver.getRelativePort(0)))
                    .loglevel(new LogdConfig.Loglevel.Builder().
                            debug(new LogdConfig.Loglevel.Debug.Builder().forward(forwardAllLogLevels)).
                            spam(new LogdConfig.Loglevel.Spam.Builder().forward(forwardAllLogLevels)));
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

    /**
     * Adds services to all hosts in the system.
     */
    public void addPerHostServices(List<HostResource> hosts, DeployState deployState) {
        if (slobroks.isEmpty()) // TODO: Move to caller
            slobroks.addAll(createDefaultSlobrokSetup(deployState));

        if (! deployState.isHosted() || ! deployState.getProperties().applicationId().instance().isTester())
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
            addAndInitializeService(deployState, host, container);
            metricsProxyCluster.addContainer(container);
        }
    }

    private void addCommonServices(HostResource host, DeployState deployState) {
        addConfigSentinel(deployState, host);
        addLogd(deployState, host);
        addConfigProxy(deployState, host);
        if (logForwarderConfig != null) {
            boolean actuallyAdd = true;
            var membership = host.spec().membership();
            if (membership.isPresent()) {
                var clustertype = membership.get().cluster().type();
                // XXX should skip only if this.isHostedVespa is true?
                if (clustertype == ClusterSpec.Type.admin) {
                    actuallyAdd = logForwarderIncludeAdmin;
                }
            }
            if (actuallyAdd) {
                addLogForwarder(deployState, host);
            }
        }
    }

    private void addConfigSentinel(DeployState deployState, HostResource host)
    {
        ConfigSentinel configSentinel = new ConfigSentinel(host.getHost(), deployState);
        addAndInitializeService(deployState, host, configSentinel);
        host.getHost().setConfigSentinel(configSentinel);
    }

    private void addLogForwarder(DeployState deployState, HostResource host) {
        addAndInitializeService(deployState, host, new LogForwarder(host.getHost(), logForwarderConfig));
    }

    private void addLogd(DeployState deployState, HostResource host) {
        addAndInitializeService(deployState, host, new Logd(host.getHost()));
    }

    private void addConfigProxy(DeployState deployState, HostResource host) {
        addAndInitializeService(deployState, host, new ConfigProxy(host.getHost()));
    }

    public void addAndInitializeService(DeployState deployState, HostResource host, AbstractService service) {
        service.setHostResource(host);
        service.initService(deployState);
    }

    // If not configured by user: Use default setup: max 3 slobroks, 1 on the default configserver host
    private List<Slobrok> createDefaultSlobrokSetup(DeployState deployState) {
        List<HostResource> hosts = hostSystem().getHosts();
        List<Slobrok> slobs = new ArrayList<>();
        if (logserver != null) {
            Slobrok slobrok = new Slobrok(this, 0, deployState.featureFlags());
            addAndInitializeService(deployState, logserver.getHostResource(), slobrok);
            slobs.add(slobrok);
        }

        int n = 0;
        while ((n < hosts.size()) && (slobs.size() < 3)) {
            HostResource host = hosts.get(n);
            if ((logserver== null || host != logserver.getHostResource()) && ! host.getHost().runsConfigServer()) {
                Slobrok newSlobrok = new Slobrok(this, slobs.size(), deployState.featureFlags());
                addAndInitializeService(deployState, host, newSlobrok);
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

    public ApplicationType getApplicationType() { return applicationType; }

    public List<LogctlSpec> getLogctlSpecs() {
        return logctlSpecs;
    }
    public void addLogctlCommand(String componentSpec, LevelsModSpec levelsModSpec) {
        logctlSpecs.add(new LogctlSpec(componentSpec, levelsModSpec));
    }

    private static List<LogctlSpec> defaultLogctlSpecs() {
        // Turn off info logging for all containers for some classes (unimportant log messages that create noise in vespa log)
        return List.of(new LogctlSpec("com.yahoo.vespa.spifly.repackaged.spifly.BaseActivator", getLevelModSpec("-info")),
                       new LogctlSpec("org.eclipse.jetty.server.Server", getLevelModSpec("-info")),
                       new LogctlSpec("org.eclipse.jetty.server.handler.ContextHandler", getLevelModSpec("-info")),
                       new LogctlSpec("org.eclipse.jetty.server.handler.ErrorHandler", getLevelModSpec("-info -warning")),
                       new LogctlSpec("org.eclipse.jetty.server.AbstractConnector", getLevelModSpec("-info")));
    }

    static LevelsModSpec getLevelModSpec(String levels) {
        var levelSpec = new LevelsModSpec();
        levelSpec.setLevels(levels);
        return levelSpec;
    }

}
