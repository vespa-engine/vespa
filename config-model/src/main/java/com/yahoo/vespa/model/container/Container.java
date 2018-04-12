// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.QrConfig;
import com.yahoo.container.core.ContainerHttpConfig;
import com.yahoo.container.jdisc.ContainerMbusConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.AbstractService;
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
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static com.yahoo.container.QrConfig.Filedistributor;
import static com.yahoo.container.QrConfig.Rpc;


/**
 * @author gjoranv
 * @author einarmr
 * @author tonytv
 */
//qr is restart because it is handled by ConfiguredApplication.start
@RestartConfigs({QrStartConfig.class, QrConfig.class})
public class Container extends AbstractService implements
        QrConfig.Producer,
        ComponentsConfig.Producer,
        JdiscBindingsConfig.Producer,
        ContainerHttpConfig.Producer,
        ContainerMbusConfig.Producer {

    public static final int BASEPORT = Defaults.getDefaults().vespaWebServicePort();
    public static final String SINGLENODE_CONTAINER_SERVICESPEC = "default_singlenode_container";

    private final AbstractConfigProducer parent;
    private final String name;
    private final boolean isHostedVespa;

    private String clusterName = null;
    private boolean rpcServerEnabled = true;
    
    private Optional<String> hostResponseHeaderKey = Optional.empty();

    // TODO: move these up to cluster
    private boolean httpServerEnabled = true;
    private boolean messageBusEnabled = true;
    
    /** Whether this node has been marked as retired (e.g, will be removed) */
    private final boolean retired;
    /** The unique index of this node */
    private final int index;

    private final ComponentGroup<Handler<?>> handlers = new ComponentGroup<>(this, "handler");
    private final ComponentGroup<Component<?, ?>> components = new ComponentGroup<>(this, "components");

    private final JettyHttpServer defaultHttpServer = new JettyHttpServer(new ComponentId("DefaultHttpServer"));

    private final List<PortOverride> portOverrides;

    private final int numHttpServerPorts;
    private static final int numRpcServerPorts = 2;
    private static final String defaultHostedJVMArgs = "-XX:+UseOSErrorReporting -XX:+SuppressFatalErrorMessage";

    public Container(AbstractConfigProducer parent, String name, int index) {
        this(parent, name, Collections.<PortOverride>emptyList(), index);
    }
    public Container(AbstractConfigProducer parent, String name, boolean retired, int index) {
        this(parent, name, retired, Collections.<PortOverride>emptyList(), index);
    }
    public Container(AbstractConfigProducer parent, String name, List<PortOverride> portOverrides, int index) {
        this(parent, name, false, portOverrides, index);
    }
    public Container(AbstractConfigProducer parent, String name, boolean retired, List<PortOverride> portOverrides, int index) {
        super(parent, name);
        this.name = name;
        this.parent = parent;
        this.isHostedVespa = stateIsHosted(deployStateFrom(parent));
        this.portOverrides = Collections.unmodifiableList(new ArrayList<>(portOverrides));
        this.retired = retired;
        this.index = index;

        if (getHttp() == null) {
            numHttpServerPorts = 2;
            addChild(defaultHttpServer);
        } else if (getHttp().getHttpServer() == null) {
            numHttpServerPorts = 0;
        } else {
            numHttpServerPorts = getHttp().getHttpServer().getConnectorFactories().size();
        }
        addBuiltinHandlers();

        addChild(new SimpleComponent("com.yahoo.container.jdisc.ConfiguredApplication$ApplicationContext"));
    }

    /** True if this container is retired (slated for removal) */
    public boolean isRetired() { return retired; }

    public ComponentGroup<Handler<?>> getHandlers() {
        return handlers;
    }

    public ComponentGroup getComponents() {
        return components;
    }

    public void addComponent(Component c) {
        components.addComponent(c);
    }

    public void addHandler(Handler h) {
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
        return (parent instanceof ContainerCluster) ? ((ContainerCluster) parent).getHttp() : null;
    }

    public JettyHttpServer getDefaultHttpServer() {
        return defaultHttpServer;
    }
    
    /** Returns the index of this node. The index of a given node is stable through changes with best effort. */
    public int index() { return index; }

    // We cannot set bindings yet, as baseport is not initialized
    public void addBuiltinHandlers() { }

    @Override
    public void initService() {
        // XXX: Must be called first, to set the baseport
        super.initService();

        if (getHttp() == null) {
            initDefaultJettyConnector();
        } else {
            reserveHttpPortsPrepended();
        }

        tagServers();
    }

    private void tagServers() {
        if (numHttpServerPorts > 0) {
            portsMeta.on(0).tag("http").tag("query").tag("external").tag("state");
        }

        for (int i = 1; i < numHttpServerPorts; i++)
            portsMeta.on(i).tag("http").tag("external");

        if (rpcServerEnabled) {
            portsMeta.on(numHttpServerPorts + 0).tag("rpc").tag("messaging");
            portsMeta.on(numHttpServerPorts + 1).tag("rpc").tag("admin");
        }
    }

    private void reserveHttpPortsPrepended() {
        if (getHttp().getHttpServer() != null) {
            for (ConnectorFactory connectorFactory : getHttp().getHttpServer().getConnectorFactories()) {
                reservePortPrepended(getPort(connectorFactory, portOverrides));
            }
        }
    }

    private int getPort(ConnectorFactory connectorFactory, List<PortOverride> portOverrides) {
        ComponentId id = ComponentId.fromString(connectorFactory.getName());
        for (PortOverride override : portOverrides) {
            if (override.serverId.matches(id)) {
                return override.port;
            }
        }
        return connectorFactory.getListenPort();
    }

    private void initDefaultJettyConnector() {
        defaultHttpServer.addConnector(new ConnectorFactory("SearchServer", getSearchPort()));
    }

    private boolean hasDocproc() {
        return (parent instanceof ContainerCluster) && (((ContainerCluster)parent).getDocproc() != null);
    }

    // TODO: hack to retain old service names, e.g. in monitoring config, vespa.log etc.
    @Override
    public String getServiceType() {
        if (parent instanceof ContainerCluster) {
            ContainerCluster cluster = (ContainerCluster)parent;
            if (cluster.getSearch() != null && cluster.getDocproc() == null && cluster.getDocumentApi() == null) {
                return "qrserver";
            }
            if (cluster.getSearch() == null && cluster.getDocproc() != null) {
                return "docprocservice";
            }
        }
        return super.getServiceType();
    }

    public void setClusterName(String name) {
        this.clusterName = name;
    }

    @Override
    public int getWantedPort() {
        return getHttp() == null ? BASEPORT: 0;
    }

    /**
     * First Qrserver or container must run on ports familiar to the user.
     */
    @Override
    public boolean requiresWantedPort() {
        return getHttp() == null;
    }

    public boolean requiresConsecutivePorts() {
        return false;
    }

    /**
     * @return the number of ports needed by the Container - those reserved manually(reservePortPrepended)
     */
    public int getPortCount() {
        int httpPorts = (getHttp() != null) ? 0 : numHttpServerPorts + 2; // TODO remove +2, only here to keep irrelevant unit tests from failing.
        int rpcPorts = (isRpcServerEnabled()) ? numRpcServerPorts : 0;
        return httpPorts + rpcPorts;
    }

    /**
     * @return the actual search port
     * TODO: Remove. Use {@link #getPortsMeta()} and check tags in conjunction with {@link #getRelativePort(int)}.
     */
    public int getSearchPort(){
        if (getHttp() != null)
            throw new AssertionError("getSearchPort must not be used when http section is present.");

        return getRelativePort(0);
    }

    private int getRpcPort() {
        return isRpcServerEnabled() ? getRelativePort(numHttpServerPorts + 1) : 0;
    }

    private int getMessagingPort() {
        return getRelativePort(numHttpServerPorts);
    }

    @Override
    public int getHealthPort()  {
        final Http http = getHttp();
        if (http != null) {
            // TODO: allow the user to specify health port manually
            if (http.getHttpServer() == null) {
                return -1;
            } else {
                return getRelativePort(0);
            }
        } else {
            return httpServerEnabled ? getSearchPort() : -1;
        }
    }

    public String getStartupCommand() {
        return "PRELOAD=" + getPreLoad() + " exec vespa-start-container-daemon " + getJvmArgs() + " ";
    }

    public boolean isRpcServerEnabled() {
        return rpcServerEnabled;
    }

    @Override
    public void getConfig(QrConfig.Builder builder) {
        builder.
                rpc(new Rpc.Builder()
                        .enabled(isRpcServerEnabled())
                        .port(getRpcPort())
                        .slobrokId(serviceSlobrokId())).
                filedistributor(filedistributorConfig());
        if (clusterName != null) {
            builder.discriminator(clusterName+"."+name);
        } else {
            builder.discriminator(name);
        }
    }

    /** Returns the jvm arguments this should start with */
    @Override
    public String getJvmArgs() {
        String jvmArgs = super.getJvmArgs();
        return isHostedVespa && hasDocproc()
                ? ("".equals(jvmArgs) ? defaultHostedJVMArgs : defaultHostedJVMArgs + " " + jvmArgs)
                : jvmArgs;
    }

    /** Returns the jvm args set explicitly for this node */
    public String getAssignedJvmArgs() { return super.getJvmArgs(); }
    
    private String serviceSlobrokId() {
        return "vespa/service/" + getConfigId();
    }

    private Filedistributor.Builder filedistributorConfig() {
        Filedistributor.Builder builder = new Filedistributor.Builder();

        FileDistributionConfigProducer fileDistribution = getRoot().getFileDistributionConfigProducer();
        if (fileDistribution != null) {
            builder.configid(fileDistribution.getConfigProducer(getHost()).getConfigId());
        }
        return builder;
    }

    @Override
    public void getConfig(ComponentsConfig.Builder builder) {
        builder.components.addAll(ComponentsConfigGenerator.generate(allEnabledComponents()));
    }

    private Collection<Component<?, ?>> allEnabledComponents() {
        Collection<Component<?, ?>> allComponents = new ArrayList<>();
        addAllEnabledComponents(allComponents, this);
        return Collections.unmodifiableCollection(allComponents);
    }

    private void addAllEnabledComponents(Collection<Component<?, ?>> allComponents, AbstractConfigProducer<?> current) {
        for (AbstractConfigProducer<?> child: current.getChildren().values()) {
            if ( ! httpServerEnabled && isHttpServer(child)) continue;

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
        builder.enabled(messageBusEnabled).port(getMessagingPort());
    }

    @Override
    public HashMap<String,String> getDefaultMetricDimensions(){
        HashMap<String, String> dimensions = new HashMap<>();
        if (clusterName != null)
            dimensions.put("clustername", clusterName);
        return dimensions;
    }

    public void setRpcServerEnabled(boolean rpcServerEnabled) {
        this.rpcServerEnabled = rpcServerEnabled;
    }

    public void setHttpServerEnabled(boolean httpServerEnabled) {
        this.httpServerEnabled = httpServerEnabled;
    }


    public static final class PortOverride {
        public final ComponentSpecification serverId;
        public final int port;

        public PortOverride(ComponentSpecification serverId, int port) {
            this.serverId = serverId;
            this.port = port;
        }
    }

}
