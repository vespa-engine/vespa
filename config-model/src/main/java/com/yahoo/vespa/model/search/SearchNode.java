// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.cloud.config.filedistribution.FiledistributorrpcConfig;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.Flavor;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.searchlib.TranslogserverConfig;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorBucketmoverConfig;
import com.yahoo.vespa.config.content.core.StorCommunicationmanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.content.core.StorStatusConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import static com.yahoo.vespa.defaults.Defaults.getDefaults;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.ContentNode;
import com.yahoo.vespa.model.content.ResourceLimits;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProvider;
import org.w3c.dom.Element;

import java.util.HashMap;
import java.util.Optional;

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
                 StorFilestorConfig.class, StorBucketmoverConfig.class,
                 StorCommunicationmanagerConfig.class, StorStatusConfig.class,
                 StorServerConfig.class, LoadTypeConfig.class})
public class SearchNode extends AbstractService implements
        SearchInterface,
        ProtonConfig.Producer,
        FiledistributorrpcConfig.Producer,
        MetricsmanagerConfig.Producer,
        TranslogserverConfig.Producer {

    private static final long serialVersionUID = 1L;
    private final boolean isHostedVespa;
    private final boolean flushOnShutdown;
    private NodeSpec nodeSpec;
    private int distributionKey;
    private int redundancy = 1;
    private int searchableCopies = 1;
    private final String clusterName;
    private TransactionLogServer tls;
    private AbstractService serviceLayerService;
    private final Optional<Tuning> tuning;
    private final Optional<ResourceLimits> resourceLimits;
    private static final int RPC_PORT = 0;
    private static final int UNUSED_1 = 1;
    private static final int UNUSED_2 = 2;
    private static final int UNUSED_3 = 3;
    private static final int HEALTH_PORT = 4;

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<SearchNode> {

        private final String name;
        private final NodeSpec nodeSpec;
        private final String clusterName;
        private final ContentNode contentNode;
        private final boolean flushOnShutdown;
        private final Optional<Tuning> tuning;
        private final Optional<ResourceLimits> resourceLimits;
        public Builder(String name, NodeSpec nodeSpec, String clusterName, ContentNode node,
                       boolean flushOnShutdown, Optional<Tuning> tuning, Optional<ResourceLimits> resourceLimits) {
            this.name = name;
            this.nodeSpec = nodeSpec;
            this.clusterName = clusterName;
            this.contentNode = node;
            this.flushOnShutdown = flushOnShutdown;
            this.tuning = tuning;
            this.resourceLimits = resourceLimits;
        }

        @Override
        protected SearchNode doBuild(DeployState deployState, AbstractConfigProducer ancestor, Element producerSpec) {
            return new SearchNode(ancestor, name, contentNode.getDistributionKey(), nodeSpec, clusterName, contentNode,
                                  flushOnShutdown, tuning, resourceLimits, deployState.isHosted());
        }
    }

    /**
     * Creates a SearchNode in elastic mode.
     */
    public static SearchNode create(AbstractConfigProducer parent, String name, int distributionKey, NodeSpec nodeSpec,
                                    String clusterName, AbstractService serviceLayerService, boolean flushOnShutdown,
                                    Optional<Tuning> tuning, Optional<ResourceLimits> resourceLimits, boolean isHostedVespa) {
        return new SearchNode(parent, name, distributionKey, nodeSpec, clusterName, serviceLayerService,
                              flushOnShutdown, tuning, resourceLimits, isHostedVespa);
    }

    private SearchNode(AbstractConfigProducer parent, String name, int distributionKey, NodeSpec nodeSpec,
                       String clusterName, AbstractService serviceLayerService, boolean flushOnShutdown,
                       Optional<Tuning> tuning, Optional<ResourceLimits> resourceLimits, boolean isHostedVespa) {
        this(parent, name, nodeSpec, clusterName, flushOnShutdown, tuning, resourceLimits, isHostedVespa);
        this.distributionKey = distributionKey;
        this.serviceLayerService = serviceLayerService;
        setPropertiesElastic(clusterName, distributionKey);
    }

    private SearchNode(AbstractConfigProducer parent, String name, NodeSpec nodeSpec, String clusterName,
                       boolean flushOnShutdown, Optional<Tuning> tuning, Optional<ResourceLimits> resourceLimits, boolean isHostedVespa) {
        super(parent, name);
        this.isHostedVespa = isHostedVespa;
        this.nodeSpec = nodeSpec;
        this.clusterName = clusterName;
        this.flushOnShutdown = flushOnShutdown;
        portsMeta.on(RPC_PORT).tag("rpc").tag("rtc").tag("admin").tag("status");
        portsMeta.on(UNUSED_1).tag("unused");
        portsMeta.on(UNUSED_2).tag("unused");
        portsMeta.on(UNUSED_3).tag("unused");
        portsMeta.on(HEALTH_PORT).tag("http").tag("json").tag("health").tag("state");
        // Properties are set in DomSearchBuilder
        this.tuning = tuning;
        this.resourceLimits = resourceLimits;
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
    public void setSearchableCopies(int searchableCopies) {
        this.searchableCopies = searchableCopies;
    }
    public void setRedundancy(int redundancy) {
        this.redundancy = redundancy;
    }

    void updatePartition(int partitionId) {
        nodeSpec = new NodeSpec(nodeSpec.groupIndex(), partitionId);
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
    }

    /**
     * Returns the number of ports needed by this service.
     *
     * @return The number of ports.
     */
    @Override
    public int getPortCount() {
        return 5;
    }

    /**
     * Returns the RPC port used by this searchnode.
     *
     * @return The port.
     */
    public int getRpcPort() {
        return getRelativePort(RPC_PORT);
    }

    @Override
    public int getHealthPort() {
        return getHttpPort();
    }

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
        tls.getConfig(builder);
    }

    @Override
    public String toString() {
        return getHostName();
    }

    private TransactionLogServer getTransactionLogServer() {
        return tls;
    }

    public void setTls(TransactionLogServer tls) {
        this.tls = tls;
    }

    public AbstractService getServiceLayerService() {
        return serviceLayerService;
    }

    @Override
    public String getStartupCommand() {
        String startup = getEnvVariables() + "exec $ROOT/sbin/vespa-proton " + "--identity " + getConfigId();
        if (serviceLayerService != null) {
            startup = startup + " --serviceidentity " + serviceLayerService.getConfigId();
        }
        return startup;
    }

    @Override
    public void getConfig(FiledistributorrpcConfig.Builder builder) {
        FileDistributionConfigProducer fileDistribution = getRoot().getFileDistributionConfigProducer();
        if (fileDistribution != null) {
            FileDistributionConfigProvider configProducer = fileDistribution.getConfigProducer(getHost());
            configProducer.getConfig(builder);
        }
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        builder.
            rpcport(getRpcPort()).
            httpport(getHttpPort()).
            partition(getNodeSpec().partitionId()).
            clustername(getClusterName()).
            basedir(getBaseDir()).
            tlsspec("tcp/" + getHost().getHostname() + ":" + getTransactionLogServer().getTlsPort()).
            tlsconfigid(getConfigId()).
            slobrokconfigid(getClusterConfigId()).
            routingconfigid(getClusterConfigId()).
            distributionkey(getDistributionKey());
        if (isHostedVespa) {
            // 4 days, 1 hour, 1 minute due to failed nodes can be in failed for 4 days and we want at least one hour more
            // to make sure the node failer has done its work
            builder.pruneremoveddocumentsage(4 * 24 * 3600 + 3600 + 60);
        }
        if (getHostResource() != null && getHostResource().getFlavor().isPresent()) {
            Flavor nodeFlavor = getHostResource().getFlavor().get();
            NodeFlavorTuning nodeFlavorTuning = new NodeFlavorTuning(nodeFlavor, redundancy, searchableCopies);
            nodeFlavorTuning.getConfig(builder);

            if (tuning.isPresent()) {
                tuning.get().getConfig(builder);
            }

            if (resourceLimits.isPresent()) {
                resourceLimits.get().getConfig(builder);
            }
        }
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
                new MetricsmanagerConfig.Consumer.Builder().
                        name("log").
                        tags("logdefault"));
    }

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

}
