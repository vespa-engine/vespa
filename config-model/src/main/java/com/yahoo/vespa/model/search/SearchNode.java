// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.cloud.config.filedistribution.FiledistributorrpcConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.searchlib.TranslogserverConfig;
import com.yahoo.vespa.config.content.LoadTypeConfig;
import com.yahoo.vespa.config.content.StorFilestorConfig;
import com.yahoo.vespa.config.content.core.StorBucketmoverConfig;
import com.yahoo.vespa.config.content.core.StorCommunicationmanagerConfig;
import com.yahoo.vespa.config.content.core.StorServerConfig;
import com.yahoo.vespa.config.content.core.StorStatusConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.config.storage.StorDevicesConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.admin.monitoring.MonitoringSystem;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.ContentNode;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;
import com.yahoo.vespa.model.filedistribution.FileDistributorService;
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
                 StorDevicesConfig.class, StorFilestorConfig.class, StorBucketmoverConfig.class,
                 StorCommunicationmanagerConfig.class, StorStatusConfig.class,
                 StorServerConfig.class, LoadTypeConfig.class})
public class SearchNode extends AbstractService implements
        SearchInterface,
        ProtonConfig.Producer,
        FiledistributorrpcConfig.Producer,
        MetricsmanagerConfig.Producer,
        TranslogserverConfig.Producer {

    private static final long serialVersionUID = 1L;
    private final boolean flushOnShutdown;
    private NodeSpec nodeSpec;
    private int distributionKey;
    private final String clusterName;
    private TransactionLogServer tls;
    private AbstractService serviceLayerService;

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilder<SearchNode> {

        private final String name;
        private final NodeSpec nodeSpec;
        private final String clusterName;
        private final ContentNode contentNode;
        private final boolean flushOnShutdown;
        public Builder(String name, NodeSpec nodeSpec, String clusterName, ContentNode node, boolean flushOnShutdown) {
            this.name = name;
            this.nodeSpec = nodeSpec;
            this.clusterName = clusterName;
            this.contentNode = node;
            this.flushOnShutdown = flushOnShutdown;
        }

        @Override
        protected SearchNode doBuild(AbstractConfigProducer ancestor, Element producerSpec) {
            return new SearchNode(ancestor, name, contentNode.getDistributionKey(), nodeSpec, clusterName, contentNode, flushOnShutdown);
        }
    }

    /**
     * Creates a SearchNode in elastic mode.
     */
    public static SearchNode create(AbstractConfigProducer parent, String name, int distributionKey, NodeSpec nodeSpec,
                                    String clusterName, AbstractService serviceLayerService, boolean flushOnShutdown) {
        return new SearchNode(parent, name, distributionKey, nodeSpec, clusterName, serviceLayerService, flushOnShutdown);
    }

    private SearchNode(AbstractConfigProducer parent, String name, int distributionKey, NodeSpec nodeSpec,
                       String clusterName, AbstractService serviceLayerService, boolean flushOnShutdown) {
        this(parent, name, nodeSpec, clusterName, flushOnShutdown);
        this.distributionKey = distributionKey;
        this.serviceLayerService = serviceLayerService;
        setPropertiesElastic(clusterName, distributionKey);
    }

    private SearchNode(AbstractConfigProducer parent, String name, NodeSpec nodeSpec, String clusterName, boolean flushOnShutdown) {
        super(parent, name);
        this.nodeSpec = nodeSpec;
        this.clusterName = clusterName;
        this.flushOnShutdown = flushOnShutdown;
        portsMeta.on(0).tag("rpc").tag("rtc").tag("admin").tag("status");
        portsMeta.on(1).tag("fs4");
        portsMeta.on(2).tag("srmp").tag("hack").tag("test");
        portsMeta.on(3).tag("rpc").tag("engines-provider");
        portsMeta.on(4).tag("http").tag("json").tag("health").tag("state");
        // Properties are set in DomSearchBuilder
        monitorService();
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
        return Defaults.getDefaults().vespaHome() + "var/db/vespa/search/cluster." + getClusterName() + "/n" + distributionKey;
    }

    public void updatePartition(int partitionId) {
        nodeSpec = new NodeSpec(nodeSpec.groupIndex(), partitionId);
    }

    @Override
    public NodeSpec getNodeSpec() {
        return nodeSpec;
    }

    /**
     * Returns the connection spec string that resolves to this search node.
     *
     * @return The connection string.
     */
    public String getConnectSpec() {
        return "tcp/" + getHost().getHostName() + ":" + getRpcPort();
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
        return getRelativePort(0);
    }

    protected int getSlimeMessagingPort() {
        return getRelativePort(2);
    }

    /*
     * Returns the rpc port used for the engines provider interface.
     * @return The port
     */

    public int getPersistenceProviderRpcPort() {
        return getRelativePort(3);
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

    /**
     * Returns the connection spec string that resolves to the dispatcher service
     * on this node.
     *
     * @return The connection string.
     */
    public String getDispatcherConnectSpec() {
        return "tcp/" + getHost().getHostName() + ":" + getDispatchPort();
    }

    public int getDispatchPort() {
        return getRelativePort(1);
    }

    public int getHttpPort() {
        return getRelativePort(4);
    }

    @Override
    public void getConfig(TranslogserverConfig.Builder builder) {
        tls.getConfig(builder);
    }

    @Override
    public String toString() {
        return getHostName();
    }

    public TransactionLogServer getTransactionLogServer() {
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
        String startup = getEnvVariables() + "exec $ROOT/sbin/proton " + "--identity " + getConfigId();
        if (serviceLayerService != null) {
            startup = startup + " --serviceidentity " + serviceLayerService.getConfigId();
        }
        return startup;
    }

    @Override
    public void getConfig(FiledistributorrpcConfig.Builder builder) {
        FileDistributionConfigProducer fileDistribution = getRoot().getFileDistributionConfigProducer();
        if (fileDistribution != null) {
            FileDistributorService fds = fileDistribution.getFileDistributorService(getHost());
            if (fds != null) {
                fds.getConfig(builder);
            }
        }
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        builder.
            ptport(getDispatchPort()).
            rpcport(getRpcPort()).
            slime_messaging_port(getSlimeMessagingPort()).
            rtcspec(getConnectSpec()).
            httpport(getHttpPort()).
            partition(getNodeSpec().partitionId()).
            persistenceprovider(new ProtonConfig.Persistenceprovider.Builder().port(getPersistenceProviderRpcPort())).
            clustername(getClusterName()).
            basedir(getBaseDir()).
            tlsspec("tcp/" + getHost().getHostName() + ":" + getTransactionLogServer().getTlsPort()).
            tlsconfigid(getConfigId()).
            slobrokconfigid(getClusterConfigId()).
            routingconfigid(getClusterConfigId()).
            distributionkey(getDistributionKey());
        if (isHostedVespa()) {
            // 4 days, 1 hour, 1 minute due to failed nodes can be in failed for 4 days and we want at least one hour more
            // to make sure the node failer has done its work
            builder.pruneremoveddocumentsage(4 * 24 * 3600 + 3600 + 60);
        }
        if (getHostResource() != null && getHostResource().getFlavor().isPresent()) {
            new NodeFlavorTuning(getHostResource().getFlavor().get()).getConfig(builder);
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
        MonitoringSystem point = getMonitoringService();
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
        return Optional.ofNullable(flushOnShutdown ? Defaults.getDefaults().vespaHome() + "bin/vespa-proton-cmd " + getRpcPort() + " prepareRestart" : null);
    }

}
