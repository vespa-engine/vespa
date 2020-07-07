// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.RoutingProviderConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.docproc.DocprocConfig;
import com.yahoo.config.docproc.SchemamappingConfig;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.BundlesConfig;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.core.document.ContainerDocumentConfig;
import com.yahoo.container.di.PlatformBundlesConfig;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.container.jdisc.state.StateHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.usability.BindingsOverviewHandler;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.jdisc.http.filter.SecurityFilterInvoker;
import com.yahoo.metrics.simple.runtime.MetricProperties;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.prelude.semantics.SemanticRulesConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.search.pagetemplates.PageTemplatesConfig;
import com.yahoo.search.query.profile.config.QueryProfilesConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.PortsMeta;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.clients.ContainerDocumentApi;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.ComponentGroup;
import com.yahoo.vespa.model.container.component.ComponentsConfigGenerator;
import com.yahoo.vespa.model.container.component.DiscBindingsConfigGenerator;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.component.StatisticsComponent;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.docproc.DocprocChains;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.processing.ProcessingChains;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.search.AbstractSearchCluster;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static com.yahoo.container.core.BundleLoaderProperties.DISK_BUNDLE_PREFIX;

/**
 * Parent class for all container cluster types.
 *
 * @author gjoranv
 * @author Einar M R Rosenvinge
 * @author Tony Vaagenes
 */
public abstract class ContainerCluster<CONTAINER extends Container>
        extends AbstractConfigProducer<AbstractConfigProducer<?>>
        implements
        ComponentsConfig.Producer,
        JdiscBindingsConfig.Producer,
        DocumentmanagerConfig.Producer,
        ContainerDocumentConfig.Producer,
        HealthMonitorConfig.Producer,
        ApplicationMetadataConfig.Producer,
        BundlesConfig.Producer,
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
        RoutingProviderConfig.Producer,
        ConfigserverConfig.Producer,
        ThreadpoolConfig.Producer
{

    /**
     * URI prefix used for internal, usually programmatic, APIs. URIs using this
     * prefix should never considered available for direct use by customers, and
     * normal compatibility concerns only applies to libraries using the URIs in
     * question, not contents served from the URIs themselves.
     */
    public static final String RESERVED_URI_PREFIX = "reserved-for-internal-use";

    public static final String APPLICATION_STATUS_HANDLER_CLASS = "com.yahoo.container.handler.observability.ApplicationStatusHandler";
    public static final String BINDINGS_OVERVIEW_HANDLER_CLASS = BindingsOverviewHandler.class.getName();
    public static final String LOG_HANDLER_CLASS = com.yahoo.container.handler.LogHandler.class.getName();
    public static final String DEFAULT_LINGUISTICS_PROVIDER = "com.yahoo.language.provider.DefaultLinguisticsProvider";
    public static final String CMS = "-XX:+UseConcMarkSweepGC -XX:MaxTenuringThreshold=15 -XX:NewRatio=1";
    public static final String G1GC = "-XX:+UseG1GC -XX:MaxTenuringThreshold=15";

    public static final String STATE_HANDLER_CLASS = "com.yahoo.container.jdisc.state.StateHandler";
    public static final String STATE_HANDLER_BINDING_1 = "http://*" + StateHandler.STATE_API_ROOT;
    public static final String STATE_HANDLER_BINDING_2 = STATE_HANDLER_BINDING_1 + "/*";

    public static final String ROOT_HANDLER_PATH = "/";
    public static final String ROOT_HANDLER_BINDING = "http://*" + ROOT_HANDLER_PATH;

    public static final String VIP_HANDLER_BINDING = "http://*/status.html";

    private final String name;

    protected List<CONTAINER> containers = new ArrayList<>();

    private Http http;
    private ProcessingChains processingChains;
    private ContainerSearch containerSearch;
    private ContainerDocproc containerDocproc;
    private ContainerDocumentApi containerDocumentApi;
    private SecretStore secretStore;

    private boolean rpcServerEnabled = true;
    private boolean httpServerEnabled = true;

    private final Set<Path> platformBundles = new LinkedHashSet<>();

    private final List<String> serviceAliases = new ArrayList<>();
    private final List<String> endpointAliases = new ArrayList<>();
    private final ComponentGroup<Component<?, ?>> componentGroup;
    private final boolean isHostedVespa;

    private Map<String, String> concreteDocumentTypes = new LinkedHashMap<>();

    private ApplicationMetaData applicationMetaData = null;

    /** The zone this is deployed in, or the default zone if not on hosted Vespa */
    private Zone zone;

    private String hostClusterId = null;
    private String jvmGCOptions = null;
    private String environmentVars = null;

    private final double threadPoolSizeFactor;
    private final double queueSizeFactor;


    public ContainerCluster(AbstractConfigProducer<?> parent, String subId, String name, DeployState deployState) {
        super(parent, subId);
        this.name = name;
        this.isHostedVespa = stateIsHosted(deployState);
        this.zone = (deployState != null) ? deployState.zone() : Zone.defaultZone();
        this.threadPoolSizeFactor = deployState.getProperties().threadPoolSizeFactor();
        this.queueSizeFactor = deployState.getProperties().queueSizeFactor();

        componentGroup = new ComponentGroup<>(this, "component");

        addComponent(new StatisticsComponent());
        addSimpleComponent(AccessLog.class);
        addComponent(new ThreadPoolExecutorComponent.Builder("default-pool").build());
        addSimpleComponent(com.yahoo.concurrent.classlock.ClassLocking.class);
        addSimpleComponent(SecurityFilterInvoker.class);
        addSimpleComponent("com.yahoo.container.jdisc.metric.MetricConsumerProviderProvider");
        addSimpleComponent("com.yahoo.container.jdisc.metric.MetricProvider");
        addSimpleComponent("com.yahoo.container.jdisc.metric.MetricUpdater");
        addSimpleComponent(com.yahoo.container.jdisc.LoggingRequestHandler.Context.class);
        addSimpleComponent(com.yahoo.metrics.simple.MetricManager.class.getName(), null, MetricProperties.BUNDLE_SYMBOLIC_NAME);
        addSimpleComponent(com.yahoo.metrics.simple.jdisc.JdiscMetricsFactory.class.getName(), null, MetricProperties.BUNDLE_SYMBOLIC_NAME);
        addSimpleComponent("com.yahoo.container.jdisc.state.StateMonitor");
        addSimpleComponent("com.yahoo.container.jdisc.ContainerThreadFactory");
        addSimpleComponent("com.yahoo.container.handler.VipStatus");
        addSimpleComponent(com.yahoo.container.handler.ClustersStatus.class.getName());
        addJaxProviders();
    }

    public double getThreadPoolSizeFactor() {
        return threadPoolSizeFactor;
    }

    public double getQueueSizeFactor() {
        return queueSizeFactor;
    }

    public void setZone(Zone zone) {
        this.zone = zone;
    }
    public Zone getZone() {
        return zone;
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
        Handler<AbstractConfigProducer<?>> stateHandler = new Handler<>(
                new ComponentModel(STATE_HANDLER_CLASS, null, null, null));
        stateHandler.addServerBindings(STATE_HANDLER_BINDING_1, STATE_HANDLER_BINDING_2);
        addComponent(stateHandler);
    }

    public void addDefaultRootHandler() {
        Handler<AbstractConfigProducer<?>> handler = new Handler<>(
                new ComponentModel(BundleInstantiationSpecification.getFromStrings(
                        BINDINGS_OVERVIEW_HANDLER_CLASS, null, null), null));  // null bundle, as the handler is in container-disc
        handler.addServerBindings(ROOT_HANDLER_BINDING);
        addComponent(handler);
    }

    public void addApplicationStatusHandler() {
        Handler<AbstractConfigProducer<?>> statusHandler = new Handler<>(
                new ComponentModel(BundleInstantiationSpecification.getInternalHandlerSpecificationFromStrings(
                        APPLICATION_STATUS_HANDLER_CLASS, null), null));
        statusHandler.addServerBindings("http://*/ApplicationStatus");
        addComponent(statusHandler);
    }

    public void addVipHandler() {
        Handler<?> vipHandler = Handler.fromClassName(FileStatusHandlerComponent.CLASS);
        vipHandler.addServerBindings(VIP_HANDLER_BINDING);
        addComponent(vipHandler);
    }

    @SuppressWarnings("deprecation")
    private void addJaxProviders() {
        addSimpleComponent(com.yahoo.container.xml.providers.DatatypeFactoryProvider.class);
        addSimpleComponent(com.yahoo.container.xml.providers.DocumentBuilderFactoryProvider.class);
        addSimpleComponent(com.yahoo.container.xml.providers.SAXParserFactoryProvider.class);
        addSimpleComponent(com.yahoo.container.xml.providers.SchemaFactoryProvider.class);
        addSimpleComponent(com.yahoo.container.xml.providers.TransformerFactoryProvider.class);
        addSimpleComponent(com.yahoo.container.xml.providers.XMLEventFactoryProvider.class);
        addSimpleComponent(com.yahoo.container.xml.providers.XMLInputFactoryProvider.class);
        addSimpleComponent(com.yahoo.container.xml.providers.XMLOutputFactoryProvider.class);
        addSimpleComponent(com.yahoo.container.xml.providers.XPathFactoryProvider.class);
    }

    public final void addComponent(Component<?, ?> component) {
        componentGroup.addComponent(component);
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
    public Component removeComponent(ComponentId componentId) {
        return componentGroup.removeComponent(componentId);
    }

    private void addSimpleComponent(Class<?> clazz) {
        addSimpleComponent(clazz.getName());
    }

    protected void addSimpleComponent(String className) {
        addComponent(new SimpleComponent(className));
    }

    public void prepare(DeployState deployState) {
        applicationMetaData = deployState.getApplicationPackage().getMetaData();
        doPrepare(deployState);
    }

    protected abstract void doPrepare(DeployState deployState);

    public String getName() {
        return name;
    }

    public List<CONTAINER> getContainers() {
        return Collections.unmodifiableList(containers);
    }

    public void addContainer(CONTAINER container) {
        container.setClusterName(name);
        container.setProp("clustername", name)
                 .setProp("index", this.containers.size());
        containers.add(container);
    }

    public void addContainers(Collection<CONTAINER> containers) {
        containers.forEach(this::addContainer);
    }

    public void setProcessingChains(ProcessingChains processingChains, String... serverBindings) {
        if (this.processingChains != null)
            throw new IllegalStateException("ProcessingChains should only be set once.");

        this.processingChains = processingChains;

        // Cannot use the class object for ProcessingHandler, because its superclass is not accessible
        ProcessingHandler<?> processingHandler = new ProcessingHandler<>(
                processingChains,
                "com.yahoo.processing.handler.ProcessingHandler");

        for (String binding: serverBindings)
            processingHandler.addServerBindings(binding);

        addComponent(processingHandler);
    }

    ProcessingChains getProcessingChains() {
        return processingChains;
    }

    public SearchChains getSearchChains() {
        if (containerSearch == null)
            throw new IllegalStateException("Search components not found in container cluster '" + getSubId() +
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

    public ContainerDocproc getDocproc() {
        return containerDocproc;
    }

    public void setDocproc(ContainerDocproc containerDocproc) {
        this.containerDocproc = containerDocproc;
    }

    public ContainerDocumentApi getDocumentApi() {
        return containerDocumentApi;
    }

    public void setDocumentApi(ContainerDocumentApi containerDocumentApi) {
        this.containerDocumentApi = containerDocumentApi;
    }

    public DocprocChains getDocprocChains() {
        if (containerDocproc == null)
            throw new IllegalStateException("Document processing components not found in container cluster '" + getSubId() +
                                                    "': Add <document-processing/> to the cluster in services.xml");
        return containerDocproc.getChains();
    }

    @SuppressWarnings("unchecked")
    public Collection<Handler<?>> getHandlers() {
        return (Collection<Handler<?>>)(Collection)componentGroup.getComponents(Handler.class);
    }

    public void setSecretStore(SecretStore secretStore) {
        this.secretStore = secretStore;
    }

    public Optional<SecretStore> getSecretStore() {
        return Optional.ofNullable(secretStore);
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

    private void recursivelyFindAllComponents(Collection<Component<?, ?>> allComponents, AbstractConfigProducer<?> current) {
        for (AbstractConfigProducer<?> child: current.getChildren().values()) {
            if (child instanceof Component)
                allComponents.add((Component<?, ?>) child);

            if (!(child instanceof Container))
                recursivelyFindAllComponents(allComponents, child);
        }
    }

    @Override
    public void getConfig(ComponentsConfig.Builder builder) {
        builder.components.addAll(ComponentsConfigGenerator.generate(getAllComponents()));
        builder.components(new ComponentsConfig.Components.Builder().id("com.yahoo.container.core.config.HandlersConfigurerDi$RegistriesHack"));
    }

    @Override
    public void getConfig(JdiscBindingsConfig.Builder builder) {
        builder.handlers.putAll(DiscBindingsConfigGenerator.generate(getHandlers()));
    }

    @Override
    public void getConfig(DocumentmanagerConfig.Builder builder) {
        if (containerDocproc != null && containerDocproc.isCompressDocuments())
            builder.enablecompression(true);
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
        if (applicationMetaData != null) {
            builder.name(applicationMetaData.getApplicationId().application().value()).
                    user(applicationMetaData.getDeployedByUser()).
                    path(applicationMetaData.getDeployPath()).
                    timestamp(applicationMetaData.getDeployTimestamp()).
                    checksum(applicationMetaData.getChecksum()).
                    generation(applicationMetaData.getGeneration());
        }
    }

    /**
     * Adds a bundle present at a known location at the target container nodes.
     *
     * @param bundlePath usually an absolute path, e.g. '$VESPA_HOME/lib/jars/foo.jar'
     */
    public final void addPlatformBundle(Path bundlePath) {
        platformBundles.add(bundlePath);
    }

    @Override
    public void getConfig(BundlesConfig.Builder builder) {
        platformBundles.stream() .map(ContainerCluster::toFileReferenceString)
                .forEach(builder::bundle);
    }

    @Override
    public void getConfig(PlatformBundlesConfig.Builder builder) {
        platformBundles.stream() .map(ContainerCluster::toFileReferenceString)
                .forEach(builder::bundles);
    }

    private static String toFileReferenceString(Path path) {
        return DISK_BUNDLE_PREFIX + path.toString();
    }

    @Override
    public void getConfig(QrSearchersConfig.Builder builder) {
        if (containerSearch != null) containerSearch.getConfig(builder);
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        builder.jvm
                .verbosegc(false)
                .availableProcessors(2)
                .compressedClassSpaceSize(32)
                .minHeapsize(32)
                .heapsize(512)
                .heapSizeAsPercentageOfPhysicalMemory(0)
                .gcopts(Objects.requireNonNullElse(jvmGCOptions, G1GC));
        if (environmentVars != null) {
            builder.qrs.env(environmentVars);
        }
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

    public void initialize(Map<String, AbstractSearchCluster> clusterMap) {
        if (containerSearch != null) containerSearch.connectSearchClusters(clusterMap);
    }

    public void addDefaultSearchAccessLog() {
        addComponent(new AccessLogComponent(AccessLogComponent.AccessLogType.jsonAccessLog, getName(), isHostedVespa));
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        List<AbstractSearchCluster> searchClusters = new ArrayList<>();
        searchClusters.addAll(Content.getSearchClusters(getRoot().configModelRepo()));
        for (AbstractSearchCluster searchCluster : searchClusters) {
            searchCluster.getConfig(builder);
        }
    }

    @Override
    public void getConfig(ClusterInfoConfig.Builder builder) {
        builder.clusterId(name);
        builder.nodeCount(containers.size());

        for (Service service : getDescendantServices()) {
            builder.services.add(new ClusterInfoConfig.Services.Builder()
                                         .index(Integer.parseInt(service.getServicePropertyString("index", "99999")))
                                         .hostname(service.getHostName())
                                         .ports(getPorts(service)));
        }
    }

    /**
     * Returns a config server config containing the right zone settings (and defaults for the rest).
     * This is useful to allow applications to find out in which zone they are runnung by having the Zone
     * object (which is constructed from this config) injected.
     */
    @Override
    public void getConfig(ConfigserverConfig.Builder builder) {
        builder.system(zone.system().value());
        builder.environment(zone.environment().value());
        builder.region(zone.region().value());
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

    @Override
    public void getConfig(RoutingProviderConfig.Builder builder) {
        builder.enabled(isHostedVespa);
    }

    public Map<String, String> concreteDocumentTypes() { return concreteDocumentTypes; }

    /** The configured service aliases for the service in this cluster */
    public List<String> serviceAliases() { return serviceAliases; }

    /** The configured endpoint aliases (fqdn) for the service in this cluster */
    public List<String> endpointAliases() { return endpointAliases; }

    public void setHostClusterId(String clusterId) { hostClusterId = clusterId; }

    /**
     * Returns the id of the content cluster which hosts this container cluster, if any.
     * This is only set with hosted clusters where this container cluster is set up to run on the nodes
     * of a content cluster.
     */
    public Optional<String> getHostClusterId() { return Optional.ofNullable(hostClusterId); }

    public void setJvmGCOptions(String opts) { this.jvmGCOptions = opts; }

    public void setEnvironmentVars(String environmentVars) { this.environmentVars = environmentVars; }

    public Optional<String> getJvmGCOptions() { return Optional.ofNullable(jvmGCOptions); }

    public final void setRpcServerEnabled(boolean rpcServerEnabled) { this.rpcServerEnabled = rpcServerEnabled; }

    boolean rpcServerEnabled() { return rpcServerEnabled; }

    boolean httpServerEnabled() { return httpServerEnabled; }

    public void setHttpServerEnabled(boolean httpServerEnabled) { this.httpServerEnabled = httpServerEnabled; }

    @Override
    public String toString() {
        return "container cluster '" + getName() + "'";
    }

    protected abstract boolean messageBusEnabled();

}
