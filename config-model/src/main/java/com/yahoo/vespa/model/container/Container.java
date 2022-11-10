// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.api.container.ContainerServiceType;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.QrConfig;
import com.yahoo.container.core.ContainerHttpConfig;
import com.yahoo.container.jdisc.ContainerMbusConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.PortAllocBridge;
import com.yahoo.vespa.model.application.validation.RestartConfigs;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.ComponentGroup;
import com.yahoo.vespa.model.container.component.ComponentsConfigGenerator;
import com.yahoo.vespa.model.container.component.DiscBindingsConfigGenerator;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.http.JettyHttpServer;
import com.yahoo.vespa.model.filedistribution.FileDistributionConfigProducer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Objects;
import java.util.Optional;

import static com.yahoo.container.QrConfig.Filedistributor;
import static com.yahoo.container.QrConfig.Rpc;

/**
 * Note about components: In general, all components should belong to the cluster and not the container. However,
 * components that need node specific config must be added at the container level, along with the node-specific
 * parts of the config generation (getConfig).
 *
 * @author gjoranv
 * @author Einar M R Rosenvinge
 * @author Tony Vaagenes
 */
// qr is restart because it is handled by ConfiguredApplication.start
@RestartConfigs({QrStartConfig.class, QrConfig.class})
public abstract class Container extends AbstractService implements
        QrConfig.Producer,
        ComponentsConfig.Producer,
        JdiscBindingsConfig.Producer,
        ContainerHttpConfig.Producer,
        ContainerMbusConfig.Producer {

    public static final int BASEPORT = Defaults.getDefaults().vespaWebServicePort();
    public static final String SINGLENODE_CONTAINER_SERVICESPEC = "default_singlenode_container";

    /** The cluster this container belongs to, or null if it is not added to any cluster */
    private ContainerCluster<?> owner = null;

    protected final AbstractConfigProducer<?> parent;
    private final String name;
    private boolean requireSpecificPorts = true;

    private String clusterName = null;
    private Optional<String> hostResponseHeaderKey = Optional.empty();

    /** Whether this node has been marked as retired (e.g, will be removed) */
    private final boolean retired;
    /** The unique index of this node */
    private final int index;
    private final boolean useOldStartupScript;
    private final boolean dumpHeapOnShutdownTimeout;
    private final double shutdownTimeoutS;

    private final ComponentGroup<Handler> handlers = new ComponentGroup<>(this, "handler");
    private final ComponentGroup<Component<?, ?>> components = new ComponentGroup<>(this, "components");

    private final JettyHttpServer defaultHttpServer;

    protected Container(AbstractConfigProducer<?> parent, String name, int index, DeployState deployState) {
        this(parent, name, false, index, deployState);
    }

    protected Container(AbstractConfigProducer<?> parent, String name, boolean retired, int index, DeployState deployState) {
        super(parent, name);
        this.name = name;
        this.parent = parent;
        this.retired = retired;
        this.index = index;
        useOldStartupScript = deployState.featureFlags().useOldJdiscContainerStartup();
        dumpHeapOnShutdownTimeout = deployState.featureFlags().containerDumpHeapOnShutdownTimeout();
        shutdownTimeoutS = deployState.featureFlags().containerShutdownTimeout();
        this.defaultHttpServer = new JettyHttpServer("DefaultHttpServer", containerClusterOrNull(parent), deployState);
        if (getHttp() == null) {
            addChild(defaultHttpServer);
        }
        addBuiltinHandlers();

        addChild(new SimpleComponent("com.yahoo.container.jdisc.ConfiguredApplication$ApplicationContext"));

        appendJvmOptions(jvmOmitStackTraceInFastThrowOption(deployState.featureFlags()));
    }

    protected String jvmOmitStackTraceInFastThrowOption(ModelContext.FeatureFlags featureFlags) {
        return featureFlags.jvmOmitStackTraceInFastThrowOption(ClusterSpec.Type.container);
    }

    void setOwner(ContainerCluster<?> owner) { this.owner = owner; }

    /** True if this container is retired (slated for removal) */
    public boolean isRetired() { return retired; }

    public ComponentGroup<Handler> getHandlers() {
        return handlers;
    }

    public ComponentGroup<?> getComponents() {
        return components;
    }

    public final void addComponent(Component c) {
        components.addComponent(c);
    }

    public final void addSimpleComponent(String idSpec, String classSpec, String bundleSpec) {
        addComponent(new SimpleComponent(new ComponentModel(idSpec, classSpec, bundleSpec)));
    }

    public final void addHandler(Handler h) {
        handlers.addComponent(h);
    }
    
    /** 
     * If present, this container should emit this header key with the value set to the local hostname 
     * in HTTP responses
     */
    @SuppressWarnings("unused") // used by amenders
    public void setHostResponseHeaderKey(Optional<String> hostResponseheaderKey) {
        Objects.requireNonNull(hostResponseheaderKey, "HostResponseheaderKey cannot be null");
        this.hostResponseHeaderKey = hostResponseheaderKey;
    }

    public Http getHttp() {
        return (parent instanceof ContainerCluster) ? ((ContainerCluster<?>) parent).getHttp() : null;
    }

    @SuppressWarnings("unused") // used by amenders
    public JettyHttpServer getDefaultHttpServer() {
        return defaultHttpServer;
    }

    /** Returns the index of this node. The index of a given node is stable through changes with best effort. */
    public final int index() { return index; }

    // We cannot set bindings yet, as baseport is not initialized
    public void addBuiltinHandlers() { }

    @Override
    public void initService(DeployState deployState) {
        if (isInitialized()) return;

        // XXX: Must be called first, to set the baseport
        super.initService(deployState);

        if (getHttp() == null) {
            initDefaultJettyConnector();
        }
    }

    private int getPort(ConnectorFactory connectorFactory) {
        return connectorFactory.getListenPort();
    }

    private void initDefaultJettyConnector() {
        defaultHttpServer.addConnector(new ConnectorFactory.Builder("SearchServer", getSearchPort()).build());
    }

    private ContainerServiceType myServiceType = null;

    /** Subclasses must implement {@link #myServiceType()} for a custom service name. */
    @Override
    public final String getServiceType() {
        if (myServiceType == null) {
            myServiceType = myServiceType();
        }
        return myServiceType.serviceName;
    }

    /** Subclasses must implement this for a custom service name. */
    protected abstract ContainerServiceType myServiceType();

    public void setClusterName(String name) {
        this.clusterName = name;
    }

    @Override
    public int getWantedPort() {
        return requiresWantedPort() ? BASEPORT: 0;
    }

    /** instance can use any port number for its default HTTP server */
    public void useDynamicPorts() {
        requireSpecificPorts = false;
    }

    /**
     * First container must run on ports familiar to the user.
     */
    @Override
    public boolean requiresWantedPort() {
        return requireSpecificPorts && (getHttp() == null);
    }

    /**
     * @return the number of ports needed by the Container
     */
    public int getPortCount() {
        int httpPorts = (getHttp() != null) ? 0 : 2;
        return httpPorts + numMessageBusPorts() + numRpcPorts();
    }

    @Override
    public void allocatePorts(int start, PortAllocBridge from) {
        if (start == 0) start = BASEPORT;
        int offset = 0;
        if (getHttp() == null) {
            if (requireSpecificPorts) {
                allocatedSearchPort = from.requirePort(start, "http");
            } else {
                allocatedSearchPort = from.allocatePort("http");
            }
            portsMeta.on(offset++).tag("http").tag("query").tag("external").tag("state");
            // XXX unused - remove:
            from.allocatePort("http/1");
            portsMeta.on(offset++).tag("http").tag("external");
        } else if (getHttp().getHttpServer().isEmpty()) {
            // no http server ports
        } else {
            for (ConnectorFactory connectorFactory : getHttp().getHttpServer().get().getConnectorFactories()) {
                int port = getPort(connectorFactory);
                String name = "http/" + connectorFactory.getName();
                from.requirePort(port, name);
                if (offset == 0) {
                    portsMeta.on(offset++).tag("http").tag("query").tag("external").tag("state");
                } else {
                    portsMeta.on(offset++).tag("http").tag("external");
                }
            }
        }
        if (messageBusEnabled()) {
            allocatedMessagingPort = from.allocatePort("messaging");
            portsMeta.on(offset++).tag("rpc").tag("messaging");
        }
        if (rpcServerEnabled()) {
            allocatedRpcPort = from.allocatePort("rpc/admin");
            portsMeta.on(offset++).tag("rpc").tag("admin");
        }
    }

    protected int allocatedSearchPort = 0;
    /**
     * @return the actual search port
     * TODO: Remove. Use {@link #getPortsMeta()} and check tags in conjunction with {@link #getRelativePort(int)}.
     */
    public int getSearchPort() {
        if (getHttp() != null)
            throw new AssertionError("getSearchPort must not be used when http section is present.");
        return allocatedSearchPort;
    }

    protected int allocatedRpcPort = 0;
    protected int getRpcPort() {
        return allocatedRpcPort;
    }
    protected int numRpcPorts() { return rpcServerEnabled() ? 1 : 0; }

    protected int allocatedMessagingPort = 0;
    private int getMessagingPort() {
        return allocatedMessagingPort;
    }
    protected int numMessageBusPorts() { return messageBusEnabled() ? 1 : 0; }

    @Override
    public int getHealthPort()  {
        final Http http = getHttp();
        if (http != null) {
            // TODO: allow the user to specify health port manually
            if (http.getHttpServer().isEmpty()) {
                return -1;
            } else {
                return getRelativePort(0);
            }
        } else {
            return httpServerEnabled() ? getSearchPort() : -1;
        }
    }

    public Optional<String> getStartupCommand() {
        if (useOldStartupScript) {
            return Optional.of("PRELOAD=" + getPreLoad() + " exec vespa-start-container-daemon " + getJvmOptions() + " ");
        }
        return Optional.of("PRELOAD=" + getPreLoad() + " exec ${VESPA_HOME}/libexec/vespa/script-utils vespa-start-container-daemon " + getJvmOptions() + " ");
    }

    @Override
    public void getConfig(QrConfig.Builder builder) {
        builder.rpc(new Rpc.Builder()
                            .enabled(rpcServerEnabled())
                            .port(getRpcPort())
                            .slobrokId(serviceSlobrokId()))
                .filedistributor(filedistributorConfig())
                .discriminator((clusterName != null ? clusterName + "." : "" ) + name)
                .clustername(clusterName != null ? clusterName : "")
                .nodeIndex(index)
                .shutdown.dumpHeapOnTimeout(dumpHeapOnShutdownTimeout)
                         .timeout(shutdownTimeoutS);
    }

    /** Returns the jvm args set explicitly for this node */
    public String getAssignedJvmOptions() { return super.getJvmOptions(); }
    
    private String serviceSlobrokId() {
        return "vespa/service/" + getConfigId();
    }

    private Filedistributor.Builder filedistributorConfig() {
        Filedistributor.Builder builder = new Filedistributor.Builder();

        FileDistributionConfigProducer fileDistribution = getRoot().getFileDistributionConfigProducer();
        if (fileDistribution != null) {
            builder.configid(fileDistribution.getConfigProducer(getHost().getHost()).getConfigId());
        }
        return builder;
    }

    @Override
    public void getConfig(ComponentsConfig.Builder builder) {
        builder.setApplyOnRestart(owner.getDeferChangesUntilRestart()); //  Sufficient to set on one config
        builder.components.addAll(ComponentsConfigGenerator.generate(allEnabledComponents()));
    }

    private Collection<Component<?, ?>> allEnabledComponents() {
        Collection<Component<?, ?>> allComponents = new ArrayList<>();
        addAllEnabledComponents(allComponents, this);
        return Collections.unmodifiableCollection(allComponents);
    }

    private void addAllEnabledComponents(Collection<Component<?, ?>> allComponents, AbstractConfigProducer<?> current) {
        for (AbstractConfigProducer<?> child: current.getChildren().values()) {
            if ( ! httpServerEnabled() && isHttpServer(child)) continue;

            if (child instanceof Component)
                allComponents.add((Component<?, ?>) child);

            addAllEnabledComponents(allComponents, child);
        }
    }

    private boolean isHttpServer(AbstractConfigProducer<?> component) {
        return component instanceof JettyHttpServer;
    }

    @Override
    public final void getConfig(JdiscBindingsConfig.Builder builder) {
        builder.handlers(DiscBindingsConfigGenerator.generate(handlers.getComponents()));
    }

    @Override
    public void getConfig(ContainerHttpConfig.Builder builder) {
        if (hostResponseHeaderKey.isPresent())
            builder.hostResponseHeaderKey(hostResponseHeaderKey.get());
    }

    @Override
    public void getConfig(ContainerMbusConfig.Builder builder) {
        builder.port(getMessagingPort());
    }

    @Override
    public HashMap<String,String> getDefaultMetricDimensions(){
        HashMap<String, String> dimensions = new HashMap<>();
        if (clusterName != null)
            dimensions.put("clustername", clusterName);
        return dimensions;
    }

    private boolean messageBusEnabled() {
        return containerCluster().isPresent() && containerCluster().get().messageBusEnabled();
    }

    private boolean httpServerEnabled() {
        return containerCluster().isPresent() && containerCluster().get().httpServerEnabled();
    }

    private boolean rpcServerEnabled() {
        return containerCluster().isPresent() && containerCluster().get().rpcServerEnabled();
    }

    protected Optional<ContainerCluster> containerCluster() {
        return Optional.ofNullable(containerClusterOrNull(parent));
    }

    private static ContainerCluster containerClusterOrNull(AbstractConfigProducer producer) {
        return producer instanceof ContainerCluster<?> ? (ContainerCluster<?>) producer : null;
    }

}
