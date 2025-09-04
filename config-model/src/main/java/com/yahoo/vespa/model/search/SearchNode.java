// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.cloud.config.filedistribution.FiledistributorrpcConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.searchlib.TranslogserverConfig;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorCommunicationmanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.content.core.StorStatusConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.ConfigProxy;
import com.yahoo.vespa.model.PortAllocBridge;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.ContentNode;
import com.yahoo.vespa.model.content.ResourceLimits;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Optional;

import static com.yahoo.vespa.defaults.Defaults.getDefaults;

/**
 * Represents a search node (proton).
 * <p>
 * Due to the current disconnect between StorageNode and SearchNode, we have to
 * duplicate the set of RestartConfigs classes from StorageNode here, as SearchNode
 * runs in a content/storage node context without this being immediately obvious
 * in the model.
 *
 * @author arnej27959
 * @author hmusum
 */
@RestartConfigs({ProtonConfig.class, MetricsmanagerConfig.class, TranslogserverConfig.class,
                 StorFilestorConfig.class, StorCommunicationmanagerConfig.class, StorStatusConfig.class,
                 StorServerConfig.class})
public class SearchNode extends AbstractService implements
        SearchInterface,
        ProtonConfig.Producer,
        FiledistributorrpcConfig.Producer,
        MetricsmanagerConfig.Producer,
        TranslogserverConfig.Producer {

    private static final int RPC_PORT = 0;
    private static final int UNUSED_1 = 1;
    private static final int UNUSED_2 = 2;
    private static final int UNUSED_3 = 3;
    private static final int HEALTH_PORT = 4;
    private static final int TLS_PORT = 5;

    private final boolean isHostedVespa;
    private final boolean flushOnShutdown;
    private final NodeSpec nodeSpec;
    private final int distributionKey;
    private final String clusterName;
    private final AbstractService serviceLayerService;
    private final Tuning tuning;
    private final Boolean syncTransactionLog;

    private ResourceLimits resourceLimits; // Not final, calculated after nodes have been allocated

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilderBase<SearchNode> {

        private final String name;
        private final NodeSpec nodeSpec;
        private final String clusterName;
        private final ContentNode contentNode;
        private final boolean flushOnShutdown;
        private final Tuning tuning;
        private final Boolean syncTransactionLog;

        public Builder(String name, NodeSpec nodeSpec, String clusterName, ContentNode node,
                       boolean flushOnShutdown, Tuning tuning, Boolean syncTransactionLog) {
            this.name = name;
            this.nodeSpec = nodeSpec;
            this.clusterName = clusterName;
            this.contentNode = node;
            this.flushOnShutdown = flushOnShutdown;
            this.tuning = tuning;
            this.syncTransactionLog = syncTransactionLog;
        }

        @Override
        protected SearchNode doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor,
                                     Element producerSpec) {
            return SearchNode.create(ancestor, name, contentNode.getDistributionKey(), nodeSpec, clusterName,
                                     contentNode, flushOnShutdown, tuning, deployState.isHosted(), syncTransactionLog);
        }

    }

    public static SearchNode create(TreeConfigProducer<?> parent, String name, int distributionKey, NodeSpec nodeSpec,
                                    String clusterName, AbstractService serviceLayerService, boolean flushOnShutdown,
                                    Tuning tuning, boolean isHostedVespa, Boolean syncTransactionLog) {
        return new SearchNode(parent, name, distributionKey, nodeSpec, clusterName, serviceLayerService,
                              flushOnShutdown, tuning, isHostedVespa, syncTransactionLog);
    }

    private SearchNode(TreeConfigProducer<?> parent, String name, int distributionKey, NodeSpec nodeSpec,
                       String clusterName, AbstractService serviceLayerService, boolean flushOnShutdown,
                       Tuning tuning, boolean isHostedVespa, Boolean syncTransactionLog) {
        super(parent, name);
        this.distributionKey = distributionKey;
        this.serviceLayerService = serviceLayerService;
        this.isHostedVespa = isHostedVespa;
        this.nodeSpec = nodeSpec;
        this.clusterName = clusterName;
        this.flushOnShutdown = flushOnShutdown;
        portsMeta.on(RPC_PORT).tag("rpc").tag("rtc").tag("admin").tag("status");
        portsMeta.on(UNUSED_1).tag("unused");
        portsMeta.on(UNUSED_2).tag("unused");
        portsMeta.on(UNUSED_3).tag("unused");
        portsMeta.on(HEALTH_PORT).tag("http").tag("json").tag("health").tag("state");
        portsMeta.on(TLS_PORT).tag("tls");
        // Properties are set in DomSearchBuilder
        this.tuning = tuning;
        this.syncTransactionLog = syncTransactionLog;
        setPropertiesElastic(clusterName, distributionKey);
        addEnvironmentVariable("OMP_NUM_THREADS", 1);
    }

    private void setPropertiesElastic(String clusterName, int distributionKey) {
        setProp("index", distributionKey).
                setProp("clustertype", "search").
                setProp("clustername", clusterName);
    }

    public String getClusterName() {
        return clusterName;
    }

    private String getClusterConfigId() {
        return getParent().getConfigId();
    }

    private String getBaseDir() {
        return getDefaults().underVespaHome("var/db/vespa/search/cluster." + getClusterName()) + "/n" + distributionKey;
    }

    @Override
    public NodeSpec getNodeSpec() {
        return nodeSpec;
    }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        // NB: ignore "start"
        from.allocatePort("rpc");
        from.allocatePort("unused/1");
        from.allocatePort("unused/2");
        from.allocatePort("unused/3");
        from.allocatePort("health");
        from.allocatePort("tls");
    }

    @Override
    public int getPortCount() {
        return 6;
    }

    public int getRpcPort() {
        return getRelativePort(RPC_PORT);
    }

    @Override
    public int getHealthPort() {
        return getHttpPort();
    }

    int getTlsPort() { return getRelativePort(TLS_PORT); }

    @Override
    public String getServiceType() {
        return "searchnode";
    }

    public int getDistributionKey() {
        return distributionKey;
    }

    private int getHttpPort() {
        return getRelativePort(HEALTH_PORT);
    }

    @Override
    public void getConfig(TranslogserverConfig.Builder builder) {
        Optional<NodeResources> nodeResources = getSpecifiedNodeResources();
        if (nodeResources.isPresent()) {
            if (nodeResources.get().storageType() == NodeResources.StorageType.remote) {
                builder.usefsync(false);
            }
        }
        builder.listenport(getTlsPort())
                .basedir(getTlsDir());
        if (syncTransactionLog != null)
            builder.usefsync(syncTransactionLog);
    }

    @Override
    public String toString() {
        return getHostName();
    }

    public AbstractService getServiceLayerService() {
        return serviceLayerService;
    }

    @Override
    public Optional<String> getStartupCommand() {
        String startup = "exec $ROOT/sbin/vespa-proton --identity " + getConfigId();
        if (serviceLayerService != null) {
            startup = startup + " --serviceidentity " + serviceLayerService.getConfigId();
        }
        return Optional.of(startup);
    }

    @Override
    public void getConfig(FiledistributorrpcConfig.Builder builder) {
        builder.connectionspec("tcp/" + getHostName() + ":" + ConfigProxy.BASEPORT);
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        builder.
            rpcport(getRpcPort()).
            httpport(getHttpPort()).
            clustername(getClusterName()).
            basedir(getBaseDir()).
            tlsspec("tcp/" + getHost().getHostname() + ":" + getTlsPort()).
            tlsconfigid(getConfigId()).
            slobrokconfigid(getClusterConfigId()).
            routingconfigid(getClusterConfigId()).
            distributionkey(getDistributionKey());
        if (isHostedVespa) {
            // 4 days, 1 hour, 1 minute due to failed nodes can be in failed for 4 days and we want at least one hour more
            // to make sure the node failer has done its work
            builder.pruneremoveddocumentsage(4 * 24 * 3600 + 3600 + 60);
        }
        Optional<NodeResources> nodeResources = getSpecifiedNodeResources();
        if (nodeResources.isPresent()) {
            int threadsPerSearch = tuning != null ? tuning.threadsPerSearch() : 1;
            var nodeResourcesTuning = new NodeResourcesTuning(nodeResources.get(), threadsPerSearch);
            nodeResourcesTuning.getConfig(builder);

            if (tuning != null) tuning.getConfig(builder);
            if (resourceLimits != null) resourceLimits.getConfig(builder);
        }
    }

    private Optional<NodeResources> getSpecifiedNodeResources() {
        return (getHostResource() != null) ? getHostResource().realResources().asOptional() : Optional.empty();
    }

    @Override
    public HashMap<String, String> getDefaultMetricDimensions() {
        HashMap<String, String> dimensions = new HashMap<>();
        if (clusterName != null) {
            dimensions.put("clustername", clusterName);
        }
        return dimensions;
    }

    @Override
    public void getConfig(MetricsmanagerConfig.Builder builder) {
        Monitoring point = getMonitoringService();
        if (point != null) {
            builder.snapshot(new MetricsmanagerConfig.Snapshot.Builder().
                    periods(point.getIntervalSeconds()).periods(300));
        }
        builder.consumer(
                new MetricsmanagerConfig.Consumer.Builder().name("log").tags("logdefault"));
    }

    private String getTlsDir() { return "tls";}

    @Override
    public Optional<String> getPreShutdownCommand() {
        if (flushOnShutdown) {
            int port = getRpcPort();
            String cmd = getDefaults().underVespaHome("bin/vespa-proton-cmd ") + port + " prepareRestart";
            return Optional.of(cmd);
        } else {
            return Optional.empty();
        }
    }

    public void setResourceLimits(ResourceLimits resourceLimits) {
        this.resourceLimits = resourceLimits;
    }

}
