// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.docproc.DocprocConfig;
import com.yahoo.config.docproc.SchemamappingConfig;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.core.document.ContainerDocumentConfig;
import com.yahoo.container.di.config.PlatformBundlesConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.container.jdisc.state.StateHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.usability.BindingsOverviewHandler;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.jdisc.http.server.jetty.VoidRequestLog;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.search.pagetemplates.PageTemplatesConfig;
import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.PortsMeta;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.VespaModel;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.clients.ContainerDocumentApi;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.ComponentGroup;
import com.yahoo.vespa.model.container.component.ComponentsConfigGenerator;
import com.yahoo.vespa.model.container.component.DiscBindingsConfigGenerator;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;
import com.yahoo.vespa.model.container.configserver.ConfigserverCluster;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.docproc.DocprocChains;
import com.yahoo.vespa.model.container.http.Client;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.processing.ProcessingChains;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.search.SearchCluster;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;
import java.util.TreeSet;

import static com.yahoo.vespa.model.container.component.AccessLogComponent.AccessLogType.jsonAccessLog;
import static com.yahoo.vespa.model.container.component.chain.ProcessingHandler.PROCESSING_HANDLER_CLASS;

/**
 * Parent class for all container cluster types.
 *
 * @author gjoranv
 * @author Einar M R Rosenvinge
 * @author Tony Vaagenes
 */
public abstract class ContainerCluster<CONTAINER extends Container>
        extends TreeConfigProducer<AnyConfigProducer>
        implements
        ComponentsConfig.Producer,
        JdiscBindingsConfig.Producer,
        DocumentmanagerConfig.Producer,
        ContainerDocumentConfig.Producer,
        HealthMonitorConfig.Producer,
        ApplicationMetadataConfig.Producer,
        PlatformBundlesConfig.Producer,
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer,
        SchemamappingConfig.Producer,
        QrSearchersConfig.Producer,
        QrStartConfig.Producer,
        QueryProfilesConfig.Producer,
        PageTemplatesConfig.Producer,
        SemanticRulesConfig.Producer,
        DocprocConfig.Producer,
        ClusterInfoConfig.Producer,
        ConfigserverConfig.Producer,
        CuratorConfig.Producer,
        SchemaInfoConfig.Producer
{

    /**
     * URI prefix used for internal, usually programmatic, APIs. URIs using this
     * prefix should never be considered available for direct use by customers, and
     * normal compatibility concerns only applies to libraries using the URIs in
     * question, not contents served from the URIs themselves.
     */
    public static final String RESERVED_URI_PREFIX = "/reserved-for-internal-use";

    public static final String APPLICATION_STATUS_HANDLER_CLASS = "com.yahoo.container.handler.observability.ApplicationStatusHandler";
    public static final String BINDINGS_OVERVIEW_HANDLER_CLASS = BindingsOverviewHandler.class.getName();
    public static final String LOG_HANDLER_CLASS = com.yahoo.container.handler.LogHandler.class.getName();
    public static final String G1GC = "-XX:+UseG1GC -XX:MaxTenuringThreshold=15";
    public static final String PARALLEL_GC = "-XX:+UseParallelGC -XX:MaxTenuringThreshold=15 -XX:NewRatio=1";

    public static final String STATE_HANDLER_CLASS = "com.yahoo.container.jdisc.state.StateHandler";
    public static final BindingPattern STATE_HANDLER_BINDING_1 = SystemBindingPattern.fromHttpPath(StateHandler.STATE_API_ROOT);
    public static final BindingPattern STATE_HANDLER_BINDING_2 = SystemBindingPattern.fromHttpPath(StateHandler.STATE_API_ROOT + "/*");

    public static final String ROOT_HANDLER_PATH = "/";
    public static final BindingPattern ROOT_HANDLER_BINDING = SystemBindingPattern.fromHttpPath(ROOT_HANDLER_PATH);

    public static final BindingPattern VIP_HANDLER_BINDING = SystemBindingPattern.fromHttpPath("/status.html");

    private final String name;

    protected List<CONTAINER> containers = new ArrayList<>();

    private Http http;
    private ProcessingChains processingChains;
    private ContainerSearch containerSearch;
    private ContainerDocproc containerDocproc;
    private ContainerDocumentApi containerDocumentApi;
    private SecretStore secretStore;
    private final ContainerThreadpool defaultHandlerThreadpool;

    private boolean rpcServerEnabled = true;
    private boolean httpServerEnabled = true;

    private final Set<Path> platformBundles = new TreeSet<>(); // Ensure stable ordering

    private final ComponentGroup<Component<?, ?>> componentGroup;
    protected final boolean isHostedVespa;
    private final boolean zooKeeperLocalhostAffinity;
    private final String compressionType;

    private final Map<String, String> concreteDocumentTypes = new LinkedHashMap<>();

    private ApplicationMetaData applicationMetaData = null;

    /** The zone this is deployed in, or the default zone if not on hosted Vespa */
    private Zone zone;

    private String jvmGCOptions = null;

    private volatile boolean deferChangesUntilRestart = false;
    private final boolean applyOnRestartForApplicationMetadataConfigEnabled;
    private boolean clientsLegacyMode;
    private List<Client> clients = List.of();

    public ContainerCluster(TreeConfigProducer<?> parent, String configSubId, String clusterId, DeployState deployState, boolean zooKeeperLocalhostAffinity) {
        super(parent, configSubId);
        this.name = clusterId;
        this.isHostedVespa = stateIsHosted(deployState);
        this.zone = (deployState != null) ? deployState.zone() : Zone.defaultZone();
        this.zooKeeperLocalhostAffinity = zooKeeperLocalhostAffinity;
        this.compressionType = "zstd";
        applyOnRestartForApplicationMetadataConfigEnabled = deployState.featureFlags().applyOnRestartForApplicationMetadataConfig();
        
        componentGroup = new ComponentGroup<>(this, "component");

        addCommonVespaBundles();
        addSimpleComponent(VoidRequestLog.class);
        defaultHandlerThreadpool = new Handler.DefaultHandlerThreadpool(deployState, null);
        addComponent(defaultHandlerThreadpool);
        addSimpleComponent(com.yahoo.concurrent.classlock.ClassLocking.class);
        addSimpleComponent("com.yahoo.container.jdisc.metric.MetricConsumerProviderProvider");
        addSimpleComponent("com.yahoo.container.jdisc.metric.MetricProvider");
        addSimpleComponent("com.yahoo.container.jdisc.metric.MetricUpdater");
        addSimpleComponent(com.yahoo.container.jdisc.ThreadedHttpRequestHandler.Context.class);
        addSimpleComponent(com.yahoo.metrics.simple.MetricManager.class.getName());
        addSimpleComponent(com.yahoo.metrics.simple.jdisc.JdiscMetricsFactory.class.getName());
        addSimpleComponent("com.yahoo.container.jdisc.state.StateMonitor");
        addSimpleComponent("com.yahoo.container.jdisc.ContainerThreadFactory");
        addSimpleComponent("com.yahoo.container.handler.VipStatus");
        addSimpleComponent(com.yahoo.container.handler.ClustersStatus.class.getName());
        addSimpleComponent("com.yahoo.container.jdisc.DisabledConnectionLogProvider");
        addSimpleComponent(com.yahoo.jdisc.http.server.jetty.Janitor.class);
    }

    protected abstract boolean messageBusEnabled();

    public ClusterSpec.Id id() { return ClusterSpec.Id.from(getName()); }

    public void setZone(Zone zone) {
        this.zone = zone;
    }
    public Zone getZone() {
        return zone;
    }

    protected Optional<Admin> getAdmin() {
        var parent = getParent();
        if (parent != null) {
            var r = parent.getRoot();
            if (r instanceof VespaModel model) {
                return Optional.ofNullable(model.getAdmin());
            }
        }
        return Optional.empty();
    }

    public void addDefaultHandlersWithVip() {
        addDefaultHandlersExceptStatus();
        addVipHandler();
    }

    public final void addDefaultHandlersExceptStatus() {
        addDefaultRootHandler();
        addMetricStateHandler();
        addApplicationStatusHandler();
    }

    public void addMetricStateHandler() {
        Handler stateHandler = new Handler(
                new ComponentModel(STATE_HANDLER_CLASS, null, null, null));
        stateHandler.addServerBindings(STATE_HANDLER_BINDING_1, STATE_HANDLER_BINDING_2);
        addComponent(stateHandler);
    }

    public void addDefaultRootHandler() {
        Handler handler = new Handler(
                new ComponentModel(BundleInstantiationSpecification.fromStrings(
                        BINDINGS_OVERVIEW_HANDLER_CLASS, null, null), null));  // null bundle, as the handler is in container-disc
        handler.addServerBindings(ROOT_HANDLER_BINDING);
        addComponent(handler);
    }

    public void addApplicationStatusHandler() {
        Handler statusHandler = new Handler(
                new ComponentModel(BundleInstantiationSpecification.fromStrings(
                        APPLICATION_STATUS_HANDLER_CLASS, null, null), null));  // null bundle, as the handler is in container-disc
        statusHandler.addServerBindings(SystemBindingPattern.fromHttpPath("/ApplicationStatus"));
        addComponent(statusHandler);
    }

    public void addVipHandler() {
        Handler vipHandler = Handler.fromClassName(FileStatusHandlerComponent.CLASS);
        vipHandler.addServerBindings(VIP_HANDLER_BINDING);
        addComponent(vipHandler);
    }

    public final void addComponent(Component<?, ?> component) {
        componentGroup.addComponent(component);
        if (component instanceof Handler handler) {
            ensureHandlerHasThreadpool(handler);
        }
    }

    private void ensureHandlerHasThreadpool(Handler handler) {
        if (! handler.hasCustomThreadPool) {
            handler.inject(defaultHandlerThreadpool);
        }
    }

    public final void addSimpleComponent(String idSpec, String classSpec, String bundleSpec) {
        addComponent(new SimpleComponent(new ComponentModel(idSpec, classSpec, bundleSpec)));
    }

    /**
     * Removes a component by id
     *
     * @return the removed component, or null if it was not present
     */
    @SuppressWarnings("unused") // Used from other repositories
    public Component<?, ?> removeComponent(ComponentId componentId) {
        return componentGroup.removeComponent(componentId);
    }

    public void removeSimpleComponent(Class<?> clazz) {
        removeComponent(new SimpleComponent(clazz.getName()).getComponentId());
    }

    public void addSimpleComponent(Class<?> clazz) {
        addSimpleComponent(clazz.getName());
    }

    protected void addSimpleComponent(String className) {
        addComponent(new SimpleComponent(className));
    }

    protected void addSimpleComponent(String id, String className) {
        addComponent(new SimpleComponent(id, className));
    }

    public void prepare(DeployState deployState) {
        applicationMetaData = deployState.getApplicationPackage().getMetaData();
        doPrepare(deployState);
    }

    protected void doPrepare(DeployState deployState) {
        wireLogctlSpecs();
    }

    private void wireLogctlSpecs() {
        getAdmin().ifPresent(admin -> {
            for (var c : getContainers()) {
                c.setLogctlSpecs(admin.getLogctlSpecs());
            }});
    }

    public String getName() {
        return name;
    }

    public List<CONTAINER> getContainers() {
        return Collections.unmodifiableList(containers);
    }

    public void addContainer(CONTAINER container) {
        container.setOwner(this);
        container.setClusterName(name);
        container.setProp("clustername", name)
                 .setProp("index", this.containers.size())
                 .setProp("clustertype", "container");
        containers.add(container);
    }

    public void addContainers(Collection<CONTAINER> containers) {
        containers.forEach(this::addContainer);
    }

    public void setProcessingChains(ProcessingChains processingChains, BindingPattern... serverBindings) {
        if (this.processingChains != null)
            throw new IllegalStateException("ProcessingChains should only be set once.");

        this.processingChains = processingChains;

        ProcessingHandler<?> processingHandler = new ProcessingHandler<>(
                processingChains,
                BundleInstantiationSpecification.fromStrings(PROCESSING_HANDLER_CLASS, null, null));

        for (BindingPattern binding: serverBindings)
            processingHandler.addServerBindings(binding);

        addComponent(processingHandler);
    }

    ProcessingChains getProcessingChains() {
        return processingChains;
    }

    public SearchChains getSearchChains() {
        if (containerSearch == null)
            throw new IllegalArgumentException("Search components not found in container cluster '" + getSubId() +
                                               "': Add <search/> to the cluster in services.xml");
        return containerSearch.getChains();
    }

    public ContainerSearch getSearch() {
        return containerSearch;
    }

    public void setSearch(ContainerSearch containerSearch) {
        this.containerSearch = containerSearch;
    }

    public void setHttp(Http http) {
        this.http = http;
        addChild(http);
    }

    public Http getHttp() {
        return http;
    }

    public void setClients(boolean legacyMode, List<Client> clients) {
        clientsLegacyMode = legacyMode;
        this.clients = clients;
    }

    public List<Client> getClients() {
        return clients;
    }

    public boolean clientsLegacyMode() { return clientsLegacyMode; }

    public ContainerDocproc getDocproc() {
        return containerDocproc;
    }

    public void setDocproc(ContainerDocproc containerDocproc) {
        this.containerDocproc = containerDocproc;
    }

    public void setDocumentApi(ContainerDocumentApi containerDocumentApi) {
        this.containerDocumentApi = containerDocumentApi;
    }

    public DocprocChains getDocprocChains() {
        if (containerDocproc == null)
            throw new IllegalArgumentException("Document processing components not found in container cluster '" +
                                               getSubId() +
                                               "': Add <document-processing/> to the cluster in services.xml");
        return containerDocproc.getChains();
    }

    public Collection<Handler> getHandlers() {
        return componentGroup.getComponents(Handler.class);
    }

    public void setSecretStore(SecretStore secretStore) {
        this.secretStore = secretStore;
    }

    public Optional<SecretStore> getSecretStore() {
        return Optional.ofNullable(secretStore);
    }

    public void setDefaultThreadpoolProvider(DefaultThreadpoolProvider defaultThreadpoolProvider) {
        addComponent(defaultThreadpoolProvider);
    }

    public Map<ComponentId, Component<?, ?>> getComponentsMap() {
        return componentGroup.getComponentMap();
    }

    /** Returns all components in this cluster (generic, handlers, chained) */
    public Collection<Component<?, ?>> getAllComponents() {
        List<Component<?, ?>> allComponents = new ArrayList<>();
        recursivelyFindAllComponents(allComponents, this);
        // We need consistent ordering
        Collections.sort(allComponents);
        return Collections.unmodifiableCollection(allComponents);
    }

    private void recursivelyFindAllComponents(Collection<Component<?, ?>> allComponents, TreeConfigProducer<?> current) {
        for (var child: current.getChildren().values()) {
            if (child instanceof Component)
                allComponents.add((Component<?, ?>) child);

            if (child instanceof TreeConfigProducer t && !(child instanceof Container))
                recursivelyFindAllComponents(allComponents, t);
        }
    }

    @Override
    public void getConfig(ComponentsConfig.Builder builder) {
        builder.setApplyOnRestart(getDeferChangesUntilRestart());
        builder.components.addAll(ComponentsConfigGenerator.generate(getAllComponents()));
        builder.components(new ComponentsConfig.Components.Builder().id("com.yahoo.container.core.config.HandlersConfigurerDi$RegistriesHack"));
    }

    @Override
    public void getConfig(JdiscBindingsConfig.Builder builder) {
        builder.handlers.putAll(DiscBindingsConfigGenerator.generate(getHandlers()));
    }

    @Override
    public void getConfig(DocumentmanagerConfig.Builder builder) {
        if (containerDocumentApi != null)
            builder.ignoreundefinedfields(containerDocumentApi.ignoreUndefinedFields());
    }

    @Override
    public void getConfig(ContainerDocumentConfig.Builder builder) {
        for (Map.Entry<String, String> e : concreteDocumentTypes.entrySet()) {
            ContainerDocumentConfig.Doctype.Builder dtb = new ContainerDocumentConfig.Doctype.Builder();
            dtb.type(e.getKey());
            dtb.factorycomponent(e.getValue());
            builder.doctype(dtb);
        }
    }

    @Override
    public void getConfig(HealthMonitorConfig.Builder builder) {
        Monitoring monitoring = getMonitoringService();
        if (monitoring != null) {
            builder.snapshot_interval(monitoring.getIntervalSeconds());
        }
    }

    @Override
    public void getConfig(ApplicationMetadataConfig.Builder builder) {
        // Setting this for the ComponentsConfig only is not sufficient due to a workaround in ConfigRetriever for an unknown bug.
        // It's assumed that this config is always used by container clusters (StateHandler)
        // Enabled by feature flag for testing.
        if (applyOnRestartForApplicationMetadataConfigEnabled) {
            builder.setApplyOnRestart(getDeferChangesUntilRestart());
        }
        
        if (applicationMetaData != null)
            builder.name(applicationMetaData.getApplicationId().application().value()).
                    timestamp(applicationMetaData.getDeployTimestamp()).
                    checksum(applicationMetaData.getChecksum()).
                    generation(applicationMetaData.getGeneration());
    }

    /**
     * Adds the Vespa bundles that are necessary for most container types.
     * Note that some of these can be removed later by the individual cluster types.
     */
    public void addCommonVespaBundles() {
        PlatformBundles.COMMON_VESPA_BUNDLES.forEach(this::addPlatformBundle);
        if (isHostedVespa) {
            PlatformBundles.VESPA_SECURITY_BUNDLES.forEach(this::addPlatformBundle);
        }
        PlatformBundles.VESPA_ZK_BUNDLES.forEach(this::addPlatformBundle);
    }

    /**
     * Add all search/docproc/feed related platform bundles.
     * These are only required for application configured containers as the platform bundle set is not allowed to change
     * between config generations. For standalone container platform bundles can be added on features enabled as an
     * update of application package requires restart.
     */
    public void addAllPlatformBundles() {
        ContainerDocumentApi.addVespaClientContainerBundle(this);
        addSearchAndDocprocBundles();
    }

    public void addSearchAndDocprocBundles() { PlatformBundles.SEARCH_AND_DOCPROC_BUNDLES.forEach(this::addPlatformBundle); }

    /**
     * Adds a bundle present at a known location at the target container nodes.
     * Note that the set of platform bundles cannot change during the jdisc container's lifetime.
     *
     * @param bundlePath usually an absolute path, e.g. '$VESPA_HOME/lib/jars/foo.jar'
     */
    public final void addPlatformBundle(Path bundlePath) {
        if (! unnecessaryPlatformBundles().contains(bundlePath)) {
            platformBundles.add(bundlePath);
        } else {
            log.fine(() -> "Not installing bundle " + bundlePath + " for cluster " + getName());
        }
    }

    /**
     * Implement in subclasses to avoid installing unnecessary bundles, see {@link PlatformBundles}
     * Should only return constant values, as there is no guarantee for when this is called.
     */
    protected Set<Path> unnecessaryPlatformBundles() { return Set.of(); }

    @Override
    public void getConfig(PlatformBundlesConfig.Builder builder) {
        platformBundles.stream()
                .map(Path::toString)
                .forEach(builder::bundlePaths);
    }

    @Override
    public void getConfig(QrSearchersConfig.Builder builder) {
        if (containerSearch != null) containerSearch.getConfig(builder);
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        builder.jvm
                .verbosegc(false)
                .availableProcessors(1)
                .compressedClassSpaceSize(32)
                .minHeapsize(32)
                .heapsize(256)
                .heapSizeAsPercentageOfPhysicalMemory(0)
                .stacksize(512)
                .gcopts(Objects.requireNonNullElse(jvmGCOptions, G1GC));
    }

    @Override
    public void getConfig(DocprocConfig.Builder builder) {
        if (containerDocproc != null) containerDocproc.getConfig(builder);
    }

    @Override
    public void getConfig(PageTemplatesConfig.Builder builder) {
        if (containerSearch != null) containerSearch.getConfig(builder);
    }

    @Override
    public void getConfig(SemanticRulesConfig.Builder builder) {
        if (containerSearch != null) containerSearch.getConfig(builder);
    }

    @Override
    public void getConfig(QueryProfilesConfig.Builder builder) {
        if (containerSearch != null) containerSearch.getConfig(builder);
    }

    @Override
    public void getConfig(SchemamappingConfig.Builder builder) {
        if (containerDocproc != null) containerDocproc.getConfig(builder);
    }

    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        if (containerSearch != null) containerSearch.getConfig(builder);
    }

    @Override
    public void getConfig(SchemaInfoConfig.Builder builder) {
        if (containerSearch != null) containerSearch.getConfig(builder);
    }

    public void initialize(Map<String, SearchCluster> clusterMap) {
        if (containerSearch != null) containerSearch.connectSearchClusters(clusterMap);
    }

    public void addAccessLog(String clusterName) {
        addAccessLog(Optional.ofNullable(clusterName));
    }

    public void addAccessLog(String fileNamePattern, String symlinkName) {
        removeSimpleComponent(VoidRequestLog.class);
        addSimpleComponent(AccessLog.class);
        addComponent(new AccessLogComponent(jsonAccessLog, compressionType, fileNamePattern, null, true, true, symlinkName, 1024, 256 * 1024));
    }

    protected void addAccessLog(Optional<String> clusterName) {
        removeSimpleComponent(VoidRequestLog.class);
        addSimpleComponent(AccessLog.class);
        addComponent(new AccessLogComponent(this, jsonAccessLog, compressionType, clusterName, isHostedVespa));
    }


    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        for (SearchCluster searchCluster : Content.getSearchClusters(getRoot().configModelRepo())) {
            searchCluster.getConfig(builder);
        }
    }

    @Override
    public void getConfig(ClusterInfoConfig.Builder builder) {
        builder.clusterId(name);
        builder.nodeCount(containers.size());
        containers.forEach(c -> builder.nodeIndices(c.index()));

        for (Service service : getDescendantServices()) {
            builder.services.add(new ClusterInfoConfig.Services.Builder()
                                         .index(Integer.parseInt(service.getServicePropertyString("index", "99999")))
                                         .hostname(service.getHostName())
                                         .ports(getPorts(service)));
        }
    }

    /**
     * Returns a config server config containing the right zone settings (and defaults for the rest).
     * This is useful to allow applications to find out in which zone they are running by having the Zone
     * object (which is constructed from this config) injected.
     */
    @Override
    public void getConfig(ConfigserverConfig.Builder builder) {
        builder.system(zone.system().value());
        builder.environment(zone.environment().value());
        builder.region(zone.region().value());
        builder.cloud(zone.cloud().name().value());
    }

    @Override
    public void getConfig(CuratorConfig.Builder builder) {
        if (getParent() instanceof ConfigserverCluster) return; // Produces its own config
        for (var container : containers) {
            builder.server(new CuratorConfig.Server.Builder().hostname(container.getHostResource().getHostname()));
        }
        builder.zookeeperLocalhostAffinity(zooKeeperLocalhostAffinity);
    }

    private List<ClusterInfoConfig.Services.Ports.Builder> getPorts(Service service) {
        List<ClusterInfoConfig.Services.Ports.Builder> builders = new ArrayList<>();
        PortsMeta portsMeta = service.getPortsMeta();
        for (int i = 0; i < portsMeta.getNumPorts(); i++) {
            builders.add(new ClusterInfoConfig.Services.Ports.Builder()
                                 .number(service.getRelativePort(i))
                                 .tags(ApplicationConfigProducerRoot.getPortTags(portsMeta, i))
            );
        }
        return builders;
    }

    public boolean isHostedVespa() {
        return isHostedVespa;
    }

    public Map<String, String> concreteDocumentTypes() { return concreteDocumentTypes; }

    public void setJvmGCOptions(String opts) { this.jvmGCOptions = opts; }

    public Optional<String> getJvmGCOptions() { return Optional.ofNullable(jvmGCOptions); }

    public final void setRpcServerEnabled(boolean rpcServerEnabled) { this.rpcServerEnabled = rpcServerEnabled; }

    boolean rpcServerEnabled() { return rpcServerEnabled; }

    boolean httpServerEnabled() { return httpServerEnabled; }

    public void setHttpServerEnabled(boolean httpServerEnabled) { this.httpServerEnabled = httpServerEnabled; }

    @Override
    public String toString() {
        return "container cluster '" + getName() + "'";
    }

    /**
     * Mark whether the config emitted by this cluster currently should be applied by clients already running with
     * a previous generation of it only by restarting the consuming processes.
     */
    public void setDeferChangesUntilRestart(boolean deferChangesUntilRestart) {
        this.deferChangesUntilRestart = deferChangesUntilRestart;
    }

    public boolean getDeferChangesUntilRestart() { return deferChangesUntilRestart; }

    /**
     * Returns the percentage of host physical memory this application has specified for nodes in this cluster,
     * or empty if this is not specified by the application.
     */
    public record JvmMemoryPercentage(int ofContainerAvailable, OptionalInt ofContainerTotal, OptionalDouble asAbsoluteGb) { // optionalInt pctOfTotal < int pctOfAvailable
        static JvmMemoryPercentage of(int percentageOfAvailable) { return new JvmMemoryPercentage(percentageOfAvailable, OptionalInt.empty(), OptionalDouble.empty()); }
        static JvmMemoryPercentage of(int percentageOfAvailable, int percentageOfTotal, double absoluteMemoryGb) {
            return new JvmMemoryPercentage(percentageOfAvailable, OptionalInt.of(percentageOfTotal), OptionalDouble.of(absoluteMemoryGb));
        }
    }
    public Optional<JvmMemoryPercentage> getMemoryPercentage() { return Optional.empty(); }
}
