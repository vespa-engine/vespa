// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import com.yahoo.cloud.config.ClusterInfoConfig;
import com.yahoo.cloud.config.ConfigserverConfig;
import com.yahoo.cloud.config.RoutingProviderConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ApplicationMetaData;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.docproc.DocprocConfig;
import com.yahoo.config.docproc.SchemamappingConfig;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.BundlesConfig;
import com.yahoo.container.ComponentsConfig;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.core.ApplicationMetadataConfig;
import com.yahoo.container.core.document.ContainerDocumentConfig;
import com.yahoo.container.handler.ThreadPoolProvider;
import com.yahoo.container.handler.ThreadpoolConfig;
import com.yahoo.container.jdisc.ContainerMbusConfig;
import com.yahoo.container.jdisc.JdiscBindingsConfig;
import com.yahoo.container.jdisc.config.HealthMonitorConfig;
import com.yahoo.container.jdisc.config.MetricDefaultsConfig;
import com.yahoo.container.jdisc.messagebus.MbusServerProvider;
import com.yahoo.container.jdisc.state.StateHandler;
import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.usability.BindingsOverviewHandler;
import com.yahoo.container.xml.providers.DatatypeFactoryProvider;
import com.yahoo.container.xml.providers.DocumentBuilderFactoryProvider;
import com.yahoo.container.xml.providers.JAXBContextFactoryProvider;
import com.yahoo.container.xml.providers.SAXParserFactoryProvider;
import com.yahoo.container.xml.providers.SchemaFactoryProvider;
import com.yahoo.container.xml.providers.TransformerFactoryProvider;
import com.yahoo.container.xml.providers.XMLEventFactoryProvider;
import com.yahoo.container.xml.providers.XMLInputFactoryProvider;
import com.yahoo.container.xml.providers.XMLOutputFactoryProvider;
import com.yahoo.container.xml.providers.XPathFactoryProvider;
import com.yahoo.document.config.DocumentmanagerConfig;
import com.yahoo.jdisc.http.ServletPathsConfig;
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
import com.yahoo.vespa.model.container.component.ConfigProducerGroup;
import com.yahoo.vespa.model.container.component.DiscBindingsConfigGenerator;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.Servlet;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.component.StatisticsComponent;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.docproc.DocprocChains;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.jersey.Jersey2Servlet;
import com.yahoo.vespa.model.container.jersey.JerseyHandler;
import com.yahoo.vespa.model.container.jersey.RestApi;
import com.yahoo.vespa.model.container.processing.ProcessingChains;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.content.Content;
import com.yahoo.vespa.model.search.AbstractSearchCluster;
import com.yahoo.vespa.model.utils.FileSender;
import com.yahoo.vespaclient.config.FeederConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;


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
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.container.core.BundleLoaderProperties.DISK_BUNDLE_PREFIX;

/**
 * @author gjoranv
 * @author Einar M R Rosenvinge
 * @author Tony Vaagenes
 */
public final class ContainerCluster
        extends AbstractConfigProducer<AbstractConfigProducer<?>>
        implements
        ComponentsConfig.Producer,
        JdiscBindingsConfig.Producer,
        DocumentmanagerConfig.Producer,
        ContainerMbusConfig.Producer,
        ContainerDocumentConfig.Producer,
        HealthMonitorConfig.Producer,
        ApplicationMetadataConfig.Producer,
        BundlesConfig.Producer,
        FeederConfig.Producer,
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer,
        SchemamappingConfig.Producer,
        QrSearchersConfig.Producer,
        QrStartConfig.Producer,
        QueryProfilesConfig.Producer,
        PageTemplatesConfig.Producer,
        SemanticRulesConfig.Producer,
        DocprocConfig.Producer,
        MetricDefaultsConfig.Producer,
        ClusterInfoConfig.Producer,
        ServletPathsConfig.Producer,
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
    public static final String STATE_HANDLER_CLASS = "com.yahoo.container.jdisc.state.StateHandler";
    public static final String STATISTICS_HANDLER_CLASS = "com.yahoo.container.config.StatisticsRequestHandler";
    public static final String SIMPLE_LINGUISTICS_PROVIDER = "com.yahoo.language.provider.SimpleLinguisticsProvider";

    public static final String ROOT_HANDLER_BINDING = "*://*/";

    private final String name;

    private List<Container> containers = new ArrayList<>();

    private Http http;
    private ProcessingChains processingChains;
    private ContainerSearch containerSearch;
    private ContainerDocproc containerDocproc;
    private ContainerDocumentApi containerDocumentApi;
    private SecretStore secretStore;

    private MbusParams mbusParams;

    private final Set<FileReference> applicationBundles = new LinkedHashSet<>();
    private final Set<Path> platformBundles = new LinkedHashSet<>();

    private final List<String> serviceAliases = new ArrayList<>();
    private final List<String> endpointAliases = new ArrayList<>();
    protected final ComponentGroup<Component<?, ?>> componentGroup;
    private final ConfigProducerGroup<RestApi> restApiGroup;
    private final ConfigProducerGroup<Servlet> servletGroup;
    private final ContainerClusterVerifier clusterVerifier;
    private final DeployState deployState;

    private Map<String, String> concreteDocumentTypes = new LinkedHashMap<>();
    private MetricDefaultsConfig.Factory.Enum defaultMetricConsumerFactory;

    private ApplicationMetaData applicationMetaData = null;

    /** The zone this is deployed in, or the default zone if not on hosted Vespa */
    private Zone zone;
    
    private Optional<String> hostClusterId = Optional.empty();
    private Optional<Integer> memoryPercentage = Optional.empty();

    private static class AcceptAllVerifier implements ContainerClusterVerifier {
        @Override
        public boolean acceptComponent(Component component) { return true; }

        @Override
        public boolean acceptContainer(Container container) { return true; }

        @Override
        public void getConfig(ThreadpoolConfig.Builder builder) {

        }
    }

    public ContainerCluster(AbstractConfigProducer<?> parent, String subId, String name) {
        this(parent, subId, name, new AcceptAllVerifier());
    }

    public ContainerCluster(AbstractConfigProducer<?> parent, String subId, String name, ContainerClusterVerifier verifier) {
        super(parent, subId);
        this.clusterVerifier = verifier;
        this.deployState = deployStateFrom(parent);
        this.name = name;
        this.zone = (deployState != null) ? deployState.zone() : Zone.defaultZone();
        componentGroup = new ComponentGroup<>(this, "component");
        restApiGroup = new ConfigProducerGroup<>(this, "rest-api");
        servletGroup = new ConfigProducerGroup<>(this, "servlet");

        addComponent(new StatisticsComponent());
        addSimpleComponent(AccessLog.class);
        // TODO better modelling
        addSimpleComponent(ThreadPoolProvider.class);
        addSimpleComponent(com.yahoo.concurrent.classlock.ClassLocking.class);
        addSimpleComponent("com.yahoo.jdisc.http.filter.SecurityFilterInvoker");
        addSimpleComponent(SIMPLE_LINGUISTICS_PROVIDER);
        addSimpleComponent("com.yahoo.container.jdisc.SecretStoreProvider");
        addSimpleComponent("com.yahoo.container.jdisc.CertificateStoreProvider");
        addSimpleComponent("com.yahoo.container.jdisc.metric.MetricConsumerProviderProvider");
        addSimpleComponent("com.yahoo.container.jdisc.metric.MetricProvider");
        addSimpleComponent("com.yahoo.container.jdisc.metric.MetricUpdater");
        addSimpleComponent(com.yahoo.container.jdisc.LoggingRequestHandler.Context.class);
        addSimpleComponent(com.yahoo.metrics.simple.MetricManager.class.getName(), null, MetricProperties.BUNDLE_SYMBOLIC_NAME);
        addSimpleComponent(com.yahoo.metrics.simple.jdisc.JdiscMetricsFactory.class.getName(), null, MetricProperties.BUNDLE_SYMBOLIC_NAME);
        addSimpleComponent("com.yahoo.container.jdisc.state.StateMonitor");
        addSimpleComponent("com.yahoo.container.jdisc.ContainerThreadFactory");
        addSimpleComponent("com.yahoo.container.protect.FreezeDetector");
        addSimpleComponent("com.yahoo.container.core.slobrok.SlobrokConfigurator");
        addSimpleComponent("com.yahoo.container.handler.VipStatus");
        addJaxProviders();
    }

    public DeployState getDeployState() { return deployState; }

    public void setZone(Zone zone) {
        this.zone = zone;
    }
    public Zone getZone() {
        return zone;
    }

    public void addMetricStateHandler() {
        Handler<AbstractConfigProducer<?>> stateHandler = new Handler<>(
                new ComponentModel(STATE_HANDLER_CLASS, null, null, null));
        stateHandler.addServerBindings("http://*" + StateHandler.STATE_API_ROOT,
                                       "https://*" + StateHandler.STATE_API_ROOT,
                                       "http://*" + StateHandler.STATE_API_ROOT + "/*",
                                       "https://*" + StateHandler.STATE_API_ROOT + "/*");
        addComponent(stateHandler);
    }

    public void addDefaultRootHandler() {
        if (hasHandlerWithBinding(ROOT_HANDLER_BINDING))
            return;

        Handler<AbstractConfigProducer<?>> handler = new Handler<>(
                new ComponentModel(BundleInstantiationSpecification.getFromStrings(
                        BINDINGS_OVERVIEW_HANDLER_CLASS, null, null), null));  // null bundle, as the handler is in container-disc
        handler.addServerBindings(ROOT_HANDLER_BINDING);
        addComponent(handler);
    }

    private boolean hasHandlerWithBinding(String binding) {
        Collection<Handler<?>> handlers = getHandlers();
        for (Handler handler : handlers) {
            if (handler.getServerBindings().contains(binding))
                return true;
        }
        return false;
    }

    public void addApplicationStatusHandler() {
        Handler<AbstractConfigProducer<?>> statusHandler = new Handler<>(
                new ComponentModel(BundleInstantiationSpecification.getInternalHandlerSpecificationFromStrings(
                        APPLICATION_STATUS_HANDLER_CLASS, null), null));
        statusHandler.addServerBindings("http://*/ApplicationStatus", "https://*/ApplicationStatus");
        addComponent(statusHandler);
    }

    public void addVipHandler() {
        Handler<?> vipHandler = Handler.fromClassName(FileStatusHandlerComponent.CLASS);
        vipHandler.addServerBindings("http://*/status.html", "https://*/status.html");
        addComponent(vipHandler);
    }

    public void addStatisticsHandler() {
        Handler<?> statsHandler = Handler.fromClassName(STATISTICS_HANDLER_CLASS);
        statsHandler.addServerBindings("http://*/statistics/*", "https://*/statistics/*");
        addComponent(statsHandler);
    }

    public void addJaxProviders() {
        addSimpleComponent(DatatypeFactoryProvider.class);
        addSimpleComponent(DocumentBuilderFactoryProvider.class);
        addSimpleComponent(JAXBContextFactoryProvider.class);
        addSimpleComponent(SAXParserFactoryProvider.class);
        addSimpleComponent(SchemaFactoryProvider.class);
        addSimpleComponent(TransformerFactoryProvider.class);
        addSimpleComponent(XMLEventFactoryProvider.class);
        addSimpleComponent(XMLInputFactoryProvider.class);
        addSimpleComponent(XMLOutputFactoryProvider.class);
        addSimpleComponent(XPathFactoryProvider.class);
    }

    public final void addComponent(Component<?, ?> component) {
        if (clusterVerifier.acceptComponent(component)) {
            componentGroup.addComponent(component);
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
    public Component removeComponent(ComponentId componentId) {
        return componentGroup.removeComponent(componentId);
    }

    public void addSimpleComponent(Class<?> clazz) {
        addSimpleComponent(clazz.getName());
    }

    private void addSimpleComponent(String className) {
        addComponent(new SimpleComponent(className));
    }

    public void prepare() {
        addAndSendApplicationBundles();
        sendUserConfiguredFiles();
        setApplicationMetaData();
        for (RestApi restApi : restApiGroup.getComponents())
            restApi.prepare();
    }

    private void setApplicationMetaData() {
        applicationMetaData = getRoot().getDeployState().getApplicationPackage().getMetaData();
    }

    public void addMbusServer(ComponentId chainId) {
        ComponentId serviceId = chainId.nestInNamespace(ComponentId.fromString("MbusServer"));

        addComponent(
                new Component<>(new ComponentModel(new BundleInstantiationSpecification(
                        serviceId,
                        ComponentSpecification.fromString(MbusServerProvider.class.getName()),
                        null))));
    }

    private void addAndSendApplicationBundles() {
        for (ComponentInfo component : getRoot().getDeployState().getApplicationPackage().getComponentsInfo(getRoot().getDeployState().getProperties().vespaVersion())) {
            FileReference reference = FileSender.sendFileToServices(component.getPathRelativeToAppDir(), containers);
            applicationBundles.add(reference);
        }
    }

    private void sendUserConfiguredFiles() {
        // Files referenced from user configs to all components.
        for (Component<?, ?> component : getAllComponents()) {
            FileSender.sendUserConfiguredFiles(component, containers, deployLogger());
        }
    }

    public String getName() {
        return name;
    }

    public List<Container> getContainers() {
        return Collections.unmodifiableList(containers);
    }

    public void addContainer(Container container) {
        if ( ! clusterVerifier.acceptContainer(container)) {
            throw new IllegalArgumentException("Cluster " + name + " does not accept container " + container);
        }
        container.setClusterName(name);
        container.setProp("clustername", name)
                .setProp("index", this.containers.size());
        containers.add(container);
    }

    public void addContainers(Collection<Container> containers) {
        for (Container container : containers) {
            addContainer(container);
        }
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

    public ProcessingChains getProcessingChains() {
        return processingChains;
    }

    @NonNull
    public SearchChains getSearchChains() {
        if (containerSearch == null)
            throw new IllegalStateException("Null search components!");
        return containerSearch.getChains();
    }

    @Nullable
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

    @Nullable
    public Http getHttp() {
        return http;
    }

    public final void addRestApi(@NonNull RestApi restApi) {
        restApiGroup.addComponent(ComponentId.fromString(restApi.getBindingPath()), restApi);
    }

    public Map<ComponentId, RestApi> getRestApiMap() {
        return restApiGroup.getComponentMap();
    }

    public Map<ComponentId, Servlet> getServletMap() {
        return servletGroup.getComponentMap();
    }

    public final void addServlet(@NonNull Servlet servlet) {
        servletGroup.addComponent(servlet.getGlobalComponentId(), servlet);
    }

    @Nullable
    public ContainerDocproc getDocproc() {
        return containerDocproc;
    }

    public void setDocproc(ContainerDocproc containerDocproc) {
        this.containerDocproc = containerDocproc;
    }

    @Nullable
    public ContainerDocumentApi getDocumentApi() {
        return containerDocumentApi;
    }

    public void setDocumentApi(ContainerDocumentApi containerDocumentApi) {
        this.containerDocumentApi = containerDocumentApi;
    }

    @NonNull
    public DocprocChains getDocprocChains() {
        if (containerDocproc == null)
            throw new IllegalStateException("Null docproc components!");
        return containerDocproc.getChains();
    }

    @SuppressWarnings("unchecked")
    public Collection<Handler<?>> getHandlers() {
        return (Collection<Handler<?>>)(Collection)componentGroup.getComponents(Handler.class);
    }

    // Returns all servlets, including rest-api/jersey servlets.
    public Collection<Servlet> getAllServlets() {
        return allServlets().collect(Collectors.toCollection(ArrayList::new));
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
    public final void getConfig(ComponentsConfig.Builder builder) {
        builder.components.addAll(ComponentsConfigGenerator.generate(getAllComponents()));
        builder.components(new ComponentsConfig.Components.Builder().id("com.yahoo.container.core.config.HandlersConfigurerDi$RegistriesHack"));
    }

    @Override
    public final void getConfig(JdiscBindingsConfig.Builder builder) {
        builder.handlers.putAll(DiscBindingsConfigGenerator.generate(getHandlers()));

        allJersey1Handlers().forEach(handler ->
                        builder.handlers.putAll(DiscBindingsConfigGenerator.generate(handler))
        );
    }

    @Override
    public void getConfig(ThreadpoolConfig.Builder builder) {
        clusterVerifier.getConfig(builder);
    }

    private Stream<JerseyHandler> allJersey1Handlers() {
        return restApiGroup.getComponents().stream().flatMap(streamOf(RestApi::getJersey1Handler));
    }

    @Override
    public void getConfig(ServletPathsConfig.Builder builder) {
        allServlets().forEach(servlet ->
                        builder.servlets(servlet.getComponentId().stringValue(),
                                         servlet.toConfigBuilder())
        );
    }

    private Stream<Servlet> allServlets() {
        return Stream.concat(allJersey2Servlets(),
                servletGroup.getComponents().stream());
    }

    private Stream<Jersey2Servlet> allJersey2Servlets() {
        return restApiGroup.getComponents().stream().flatMap(streamOf(RestApi::getJersey2Servlet));
    }

    private <T, R> Function<T, Stream<R>> streamOf(Function<T, Optional<R>> f) {
        return t ->
                f.apply(t).
                <Stream<R>>map(Stream::of).
                orElse(Stream.empty());
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
            builder.name(applicationMetaData.getApplicationName()).
                    user(applicationMetaData.getDeployedByUser()).
                    path(applicationMetaData.getDeployPath()).
                    timestamp(applicationMetaData.getDeployTimestamp()).
                    checksum(applicationMetaData.getCheckSum()).
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
        Stream.concat(applicationBundles.stream().map(FileReference::value),
                      platformBundles.stream()
                                     .map(ContainerCluster::toFileReferenceString))
                                     .forEach(builder::bundle);
    }

    private static String toFileReferenceString(Path path) {
        return DISK_BUNDLE_PREFIX + path.toString();
    }

    @Override
    public void getConfig(QrSearchersConfig.Builder builder) {
    	if (containerSearch!=null) containerSearch.getConfig(builder);
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
    	if (containerSearch!=null) containerSearch.getConfig(builder);
    }

    @Override
    public void getConfig(DocprocConfig.Builder builder) {
        if (containerDocproc != null) {
            containerDocproc.getConfig(builder);
        }
    }

    @Override
    public void getConfig(PageTemplatesConfig.Builder builder) {
        if (containerSearch != null) {
            containerSearch.getConfig(builder);
        }
    }

    @Override
    public void getConfig(SemanticRulesConfig.Builder builder) {
        if (containerSearch != null) {
            containerSearch.getConfig(builder);
        }
    }

    @Override
    public void getConfig(QueryProfilesConfig.Builder builder) {
        if (containerSearch != null) {
            containerSearch.getConfig(builder);
        }
    }

    @Override
    public void getConfig(SchemamappingConfig.Builder builder) {
        if (containerDocproc!=null) containerDocproc.getConfig(builder);
    }

    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        if (containerSearch!=null) containerSearch.getConfig(builder);
    }

    @Override
    public void getConfig(FeederConfig.Builder builder) {
        if (containerDocumentApi != null) {
            containerDocumentApi.getConfig(builder);
        }
    }

    @Override
    public void getConfig(ContainerMbusConfig.Builder builder) {
        if (mbusParams != null) {
            if (mbusParams.maxConcurrentFactor != null)
                builder.maxConcurrentFactor(mbusParams.maxConcurrentFactor);
            if (mbusParams.documentExpansionFactor != null)
                builder.documentExpansionFactor(mbusParams.documentExpansionFactor);
            if (mbusParams.containerCoreMemory != null)
                builder.containerCoreMemory(mbusParams.containerCoreMemory);
        }
        if (containerDocproc != null)
            containerDocproc.getConfig(builder);
    }

    public void setMbusParams(MbusParams mbusParams) {
        this.mbusParams = mbusParams;
    }

    public void initialize(Map<String, AbstractSearchCluster> clusterMap) {
        if (containerSearch != null)
            containerSearch.connectSearchClusters(clusterMap);
    }

    public void addDefaultSearchAccessLog() {
        addComponent(new AccessLogComponent(AccessLogComponent.AccessLogType.queryAccessLog, getName(), getDeployState()));
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
    public void getConfig(MetricDefaultsConfig.Builder builder) {
        if (defaultMetricConsumerFactory != null) {
            builder.factory(defaultMetricConsumerFactory);
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
        builder.system(zone.system().name());
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

    public void setDefaultMetricConsumerFactory(MetricDefaultsConfig.Factory.Enum defaultMetricConsumerFactory) {
        Objects.requireNonNull(defaultMetricConsumerFactory, "defaultMetricConsumerFactory");
        this.defaultMetricConsumerFactory = defaultMetricConsumerFactory;
    }

    public boolean isHostedVespa() {
        return stateIsHosted(deployState);
    }

    @Override
    public void getConfig(RoutingProviderConfig.Builder builder) {
        builder.enabled(isHostedVespa());
    }

    public Map<String, String> concreteDocumentTypes() { return concreteDocumentTypes; }

    /** The configured service aliases for the service in this cluster */
    public List<String> serviceAliases() { return serviceAliases; }

    /** The configured endpoint aliases (fqdn) for the service in this cluster */
    public List<String> endpointAliases() { return endpointAliases; }
    
    public void setHostClusterId(String clusterId) { hostClusterId = Optional.ofNullable(clusterId); }

    /** 
     * Returns the id of the content cluster which hosts this container cluster, if any.
     * This is only set with hosted clusters where this container cluster is set up to run on the nodes
     * of a content cluster.
     */
    public Optional<String> getHostClusterId() { return hostClusterId; }

    public void setMemoryPercentage(Optional<Integer> memoryPercentage) { this.memoryPercentage = memoryPercentage; }

    /** 
     * Returns the percentage of host physical memory this application has specified for nodes in this cluster,
     * or empty if this is not specified by the application.
     */
    public Optional<Integer> getMemoryPercentage() { return memoryPercentage; }

    @Override
    public String toString() {
        return "container cluster '" + getName() + "'";
    }

    public static class MbusParams {
        // the amount of the maxpendingbytes to process concurrently, typically 0.2 (20%)
        final Double maxConcurrentFactor;

        // the amount that documents expand temporarily when processing them
        final Double documentExpansionFactor;

        // the space to reserve for container, docproc stuff (memory that cannot be used for processing documents), in MB
        final Integer containerCoreMemory;

        public MbusParams(Double maxConcurrentFactor, Double documentExpansionFactor, Integer containerCoreMemory) {
            this.maxConcurrentFactor = maxConcurrentFactor;
            this.documentExpansionFactor = documentExpansionFactor;
            this.containerCoreMemory = containerCoreMemory;
        }
    }
}
