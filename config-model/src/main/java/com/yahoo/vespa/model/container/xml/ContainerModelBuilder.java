// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.google.common.collect.ImmutableList;
import com.yahoo.component.ComponentId;
import com.yahoo.component.Version;
import com.yahoo.config.application.Xml;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.DeploymentInstanceSpec;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelContext.ApplicationType;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.EndpointCertificateSecrets;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.model.application.provider.IncludeDirs;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.Zone;
import com.yahoo.container.logging.FileConnectionLog;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.searchdefinition.derived.RankProfileList;
import com.yahoo.text.XML;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.builder.xml.dom.DomClientProviderBuilder;
import com.yahoo.vespa.model.builder.xml.dom.DomComponentBuilder;
import com.yahoo.vespa.model.builder.xml.dom.DomHandlerBuilder;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.NodesSpecification;
import com.yahoo.vespa.model.builder.xml.dom.ServletBuilder;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.docproc.DomDocprocChainsBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.processing.DomProcessingBuilder;
import com.yahoo.vespa.model.builder.xml.dom.chains.search.DomSearchChainsBuilder;
import com.yahoo.vespa.model.clients.ContainerDocumentApi;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.ContainerModelEvaluation;
import com.yahoo.vespa.model.container.ContainerThreadpool;
import com.yahoo.vespa.model.container.IdentityProvider;
import com.yahoo.vespa.model.container.SecretStore;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.ConnectionLogComponent;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import com.yahoo.vespa.model.container.component.chain.ProcessingHandler;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.docproc.DocprocChains;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.http.JettyHttpServer;
import com.yahoo.vespa.model.container.http.ssl.HostedSslConnectorFactory;
import com.yahoo.vespa.model.container.http.xml.HttpBuilder;
import com.yahoo.vespa.model.container.jersey.xml.RestApiBuilder;
import com.yahoo.vespa.model.container.processing.ProcessingChains;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.container.search.GUIHandler;
import com.yahoo.vespa.model.container.search.PageTemplates;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.container.xml.document.DocumentFactoryBuilder;
import com.yahoo.vespa.model.content.StorageGroup;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.logging.Level.WARNING;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class ContainerModelBuilder extends ConfigModelBuilder<ContainerModel> {

    // Default path to vip status file for container in Hosted Vespa.
    static final String HOSTED_VESPA_STATUS_FILE = Defaults.getDefaults().underVespaHome("var/vespa/load-balancer/status.html");

    //Path to vip status file for container in Hosted Vespa. Only used if set, else use HOSTED_VESPA_STATUS_FILE
    private static final String HOSTED_VESPA_STATUS_FILE_SETTING = "VESPA_LB_STATUS_FILE";

    private static final String CONTAINER_TAG = "container";
    private static final String DEPRECATED_CONTAINER_TAG = "jdisc";
    private static final String ENVIRONMENT_VARIABLES_ELEMENT = "environment-variables";

    // The node count to enforce in a cluster running ZooKeeper
    private static final int MIN_ZOOKEEPER_NODE_COUNT = 1;
    private static final int MAX_ZOOKEEPER_NODE_COUNT = 7;

    public enum Networking { disable, enable }

    private ApplicationPackage app;
    private final boolean standaloneBuilder;
    private final Networking networking;
    private final boolean rpcServerEnabled;
    private final boolean httpServerEnabled;
    protected DeployLogger log;

    public static final List<ConfigModelId> configModelIds =  
            ImmutableList.of(ConfigModelId.fromName(CONTAINER_TAG), ConfigModelId.fromName(DEPRECATED_CONTAINER_TAG));

    private static final String xmlRendererId = RendererRegistry.xmlRendererId.getName();
    private static final String jsonRendererId = RendererRegistry.jsonRendererId.getName();

    public ContainerModelBuilder(boolean standaloneBuilder, Networking networking) {
        super(ContainerModel.class);
        this.standaloneBuilder = standaloneBuilder;
        this.networking = networking;
        // Always disable rpc server for standalone container
        this.rpcServerEnabled = !standaloneBuilder;
        this.httpServerEnabled = networking == Networking.enable;
    }

    @Override
    public List<ConfigModelId> handlesElements() {
        return configModelIds;
    }

    @Override
    public void doBuild(ContainerModel model, Element spec, ConfigModelContext modelContext) {
        log = modelContext.getDeployLogger();
        app = modelContext.getApplicationPackage();

        checkVersion(spec);
        checkTagName(spec, log);

        ApplicationContainerCluster cluster = createContainerCluster(spec, modelContext);
        addClusterContent(cluster, spec, modelContext);
        cluster.setMessageBusEnabled(rpcServerEnabled);
        cluster.setRpcServerEnabled(rpcServerEnabled);
        cluster.setHttpServerEnabled(httpServerEnabled);
        model.setCluster(cluster);
    }

    private ApplicationContainerCluster createContainerCluster(Element spec, ConfigModelContext modelContext) {
        return new VespaDomBuilder.DomConfigProducerBuilder<ApplicationContainerCluster>() {
            @Override
            protected ApplicationContainerCluster doBuild(DeployState deployState, AbstractConfigProducer ancestor, Element producerSpec) {
                return new ApplicationContainerCluster(ancestor, modelContext.getProducerId(),
                                                       modelContext.getProducerId(), deployState);
            }
        }.build(modelContext.getDeployState(), modelContext.getParentProducer(), spec);
    }

    private void addClusterContent(ApplicationContainerCluster cluster, Element spec, ConfigModelContext context) {
        DeployState deployState = context.getDeployState();
        DocumentFactoryBuilder.buildDocumentFactories(cluster, spec);
        addConfiguredComponents(deployState, cluster, spec);
        addSecretStore(cluster, spec, deployState);

        addRestApis(deployState, spec, cluster);
        addServlets(deployState, spec, cluster);
        addModelEvaluation(spec, cluster, context);

        addProcessing(deployState, spec, cluster);
        addSearch(deployState, spec, cluster);
        addDocproc(deployState, spec, cluster);
        addDocumentApi(spec, cluster);  // NOTE: Must be done after addSearch

        cluster.addDefaultHandlersExceptStatus();
        addStatusHandlers(cluster, context.getDeployState().isHosted());
        addUserHandlers(deployState, cluster, spec);

        addHttp(deployState, spec, cluster, context);

        addAccessLogs(deployState, cluster, spec);
        addRoutingAliases(cluster, spec, deployState.zone().environment());
        addNodes(cluster, spec, context);

        addClientProviders(deployState, spec, cluster);
        addServerProviders(deployState, spec, cluster);

        // Must be added after nodes:
        addAthensCopperArgos(cluster, context);
        addZooKeeper(cluster, spec);

        addParameterStoreValidationHandler(cluster, deployState);
    }


    private void addParameterStoreValidationHandler(ApplicationContainerCluster cluster, DeployState deployState) {
        if (deployState.featureFlags().tenantIamRole()) {
            BindingPattern bindingPattern = SystemBindingPattern.fromHttpPath("/validate-secret-store");
            Handler<AbstractConfigProducer<?>> handler = new Handler<>(
                    new ComponentModel("com.yahoo.jdisc.cloud.aws.AwsParameterStoreValidationHandler", null, "jdisc-cloud-aws", null));
            handler.addServerBindings(bindingPattern);
            cluster.addPlatformBundle(PlatformBundles.absoluteBundlePath("jdisc-cloud-aws"));
            cluster.addComponent(handler);
        }
    }

    private void addZooKeeper(ApplicationContainerCluster cluster, Element spec) {
        if ( ! hasZooKeeper(spec)) return;
        Element nodesElement = XML.getChild(spec, "nodes");
        boolean isCombined = nodesElement != null && nodesElement.hasAttribute("of");
        if (isCombined) {
            throw new IllegalArgumentException("A combined cluster cannot run ZooKeeper");
        }
        long nonRetiredNodes = cluster.getContainers().stream().filter(c -> !c.isRetired()).count();
        if (nonRetiredNodes < MIN_ZOOKEEPER_NODE_COUNT || nonRetiredNodes > MAX_ZOOKEEPER_NODE_COUNT || nonRetiredNodes % 2 == 0) {
            throw new IllegalArgumentException("Cluster with ZooKeeper needs an odd number of nodes, between " +
                                               MIN_ZOOKEEPER_NODE_COUNT + " and " + MAX_ZOOKEEPER_NODE_COUNT +
                                               ", have " + nonRetiredNodes + " non-retired");
        }
        cluster.addSimpleComponent("com.yahoo.vespa.curator.Curator", null, "zkfacade");

        // These need to be setup so that they will use the container's config id, since each container
        // have different config (id of zookeeper server)
        cluster.getContainers().forEach(ContainerModelBuilder::addReconfigurableZooKeeperServerComponents);
    }

    public static void addReconfigurableZooKeeperServerComponents(Container container) {
        container.addComponent(zookeeperComponent("com.yahoo.vespa.zookeeper.ReconfigurableVespaZooKeeperServer", container));
        container.addComponent(zookeeperComponent("com.yahoo.vespa.zookeeper.Reconfigurer", container));
        container.addComponent(zookeeperComponent("com.yahoo.vespa.zookeeper.VespaZooKeeperAdminImpl", container));
    }

    private static SimpleComponent zookeeperComponent(String idSpec, Container container) {
        String configId = container.getConfigId();
        return new SimpleComponent(new ComponentModel(idSpec, null, "zookeeper-server", configId));
    }

    private void addSecretStore(ApplicationContainerCluster cluster, Element spec, DeployState deployState) {

        Element secretStoreElement = XML.getChild(spec, "secret-store");
        if (secretStoreElement != null) {
            String type = secretStoreElement.getAttribute("type");
            if ("cloud".equals(type)) {
                addCloudSecretStore(cluster, secretStoreElement, deployState);
            } else {
                SecretStore secretStore = new SecretStore();
                for (Element group : XML.getChildren(secretStoreElement, "group")) {
                    secretStore.addGroup(group.getAttribute("name"), group.getAttribute("environment"));
                }
                cluster.setSecretStore(secretStore);
            }
        }
    }

    private void addCloudSecretStore(ApplicationContainerCluster cluster, Element secretStoreElement, DeployState deployState) {
        CloudSecretStore cloudSecretStore = new CloudSecretStore();
        Map<String, TenantSecretStore> secretStoresByName = deployState.getProperties().tenantSecretStores()
                .stream()
                .collect(Collectors.toMap(
                        TenantSecretStore::getName,
                        store -> store
                ));

        for (Element group : XML.getChildren(secretStoreElement, "aws-parameter-store")) {
            String name = group.getAttribute("name");
            String region = group.getAttribute("region");
            TenantSecretStore secretStore = secretStoresByName.get(name);

            if (secretStore == null)
                throw new RuntimeException("No configured secret store named " + name);

            if (secretStore.getExternalId().isEmpty())
                throw new RuntimeException("No external ID has been set");

            cloudSecretStore.addConfig(name, region, secretStore.getAwsId(), secretStore.getRole(), secretStore.getExternalId().get());
        }

        cluster.addComponent(cloudSecretStore);
    }

    private void addAthensCopperArgos(ApplicationContainerCluster cluster, ConfigModelContext context) {
        if ( ! context.getDeployState().isHosted()) return;
        app.getDeployment().map(DeploymentSpec::fromXml)
                .ifPresent(deploymentSpec -> {
                    addIdentityProvider(cluster,
                                        context.getDeployState().getProperties().configServerSpecs(),
                                        context.getDeployState().getProperties().loadBalancerName(),
                                        context.getDeployState().getProperties().ztsUrl(),
                                        context.getDeployState().getProperties().athenzDnsSuffix(),
                                        context.getDeployState().zone(),
                                        deploymentSpec);
                    addRotationProperties(cluster, context.getDeployState().zone(), context.getDeployState().getEndpoints(), deploymentSpec);
                });
    }

    private void addRotationProperties(ApplicationContainerCluster cluster, Zone zone, Set<ContainerEndpoint> endpoints, DeploymentSpec spec) {
        cluster.getContainers().forEach(container -> {
            setRotations(container, endpoints, cluster.getName());
            container.setProp("activeRotation", Boolean.toString(zoneHasActiveRotation(zone, spec)));
        });
    }

    private boolean zoneHasActiveRotation(Zone zone, DeploymentSpec spec) {
        Optional<DeploymentInstanceSpec> instance = spec.instance(app.getApplicationId().instance());
        if (instance.isEmpty()) return false;
        return instance.get().zones().stream()
                   .anyMatch(declaredZone -> declaredZone.concerns(zone.environment(), Optional.of(zone.region())) &&
                                             declaredZone.active());
    }

    private void setRotations(Container container, Set<ContainerEndpoint> endpoints, String containerClusterName) {
        var rotationsProperty = endpoints.stream()
                                         .filter(endpoint -> endpoint.clusterId().equals(containerClusterName))
                                         .flatMap(endpoint -> endpoint.names().stream())
                                         .collect(Collectors.toUnmodifiableSet());

        // Build the comma delimited list of endpoints this container should be known as.
        // Confusingly called 'rotations' for legacy reasons.
        container.setProp("rotations", String.join(",", rotationsProperty));
    }

    private void addRoutingAliases(ApplicationContainerCluster cluster, Element spec, Environment environment) {
        if (environment != Environment.prod) return;

        Element aliases = XML.getChild(spec, "aliases");
        for (Element alias : XML.getChildren(aliases, "service-alias")) {
            cluster.serviceAliases().add(XML.getValue(alias));
        }
        for (Element alias : XML.getChildren(aliases, "endpoint-alias")) {
            cluster.endpointAliases().add(XML.getValue(alias));
        }
    }

    private void addConfiguredComponents(DeployState deployState, ApplicationContainerCluster cluster, Element spec) {
        for (Element components : XML.getChildren(spec, "components")) {
            addIncludes(components);
            addConfiguredComponents(deployState, cluster, components, "component");
        }
        addConfiguredComponents(deployState, cluster, spec, "component");
    }

    protected void addStatusHandlers(ApplicationContainerCluster cluster, boolean isHostedVespa) {
        if (isHostedVespa) {
            String name = "status.html";
            Optional<String> statusFile = Optional.ofNullable(System.getenv(HOSTED_VESPA_STATUS_FILE_SETTING));
            cluster.addComponent(
                    new FileStatusHandlerComponent(
                            name + "-status-handler",
                            statusFile.orElse(HOSTED_VESPA_STATUS_FILE),
                            SystemBindingPattern.fromHttpPath("/" + name)));
        } else {
            cluster.addVipHandler();
        }
    }

    private void addClientProviders(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        for (Element clientSpec: XML.getChildren(spec, "client")) {
            cluster.addComponent(new DomClientProviderBuilder(cluster).build(deployState, cluster, clientSpec));
        }
    }

    private void addServerProviders(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        addConfiguredComponents(deployState, cluster, spec, "server");
    }

    protected void addAccessLogs(DeployState deployState, ApplicationContainerCluster cluster, Element spec) {
        List<Element> accessLogElements = getAccessLogElements(spec);

        for (Element accessLog : accessLogElements) {
            AccessLogBuilder.buildIfNotDisabled(deployState, cluster, accessLog).ifPresent(cluster::addComponent);
        }

        if (accessLogElements.isEmpty() && deployState.getAccessLoggingEnabledByDefault())
            cluster.addDefaultSearchAccessLog();

        // Add connection log if access log is configured
        if (cluster.getAllComponents().stream().anyMatch(component -> component instanceof AccessLogComponent)) {
            cluster.addComponent(new ConnectionLogComponent(cluster, FileConnectionLog.class, "qrs"));
        }
    }

    private List<Element> getAccessLogElements(Element spec) {
        return XML.getChildren(spec, "accesslog");
    }


    protected void addHttp(DeployState deployState, Element spec, ApplicationContainerCluster cluster, ConfigModelContext context) {
        Element httpElement = XML.getChild(spec, "http");
        if (httpElement != null) {
            cluster.setHttp(buildHttp(deployState, cluster, httpElement));
        }
        if (isHostedTenantApplication(context)) {
            addHostedImplicitHttpIfNotPresent(cluster);
            addHostedImplicitAccessControlIfNotPresent(deployState, cluster);
            addDefaultConnectorHostedFilterBinding(cluster);
            addAdditionalHostedConnector(deployState, cluster, context);
        }
    }

    private void addDefaultConnectorHostedFilterBinding(ApplicationContainerCluster cluster) {
        cluster.getHttp().getAccessControl()
                .ifPresent(accessControl -> accessControl.configureDefaultHostedConnector(cluster.getHttp()));                                 ;
    }

    private void addAdditionalHostedConnector(DeployState deployState, ApplicationContainerCluster cluster, ConfigModelContext context) {
        JettyHttpServer server = cluster.getHttp().getHttpServer().get();
        String serverName = server.getComponentId().getName();

        // If the deployment contains certificate/private key reference, setup TLS port
        HostedSslConnectorFactory connectorFactory;
        if (deployState.endpointCertificateSecrets().isPresent()) {
            boolean authorizeClient = deployState.zone().system().isPublic();
            if (authorizeClient && deployState.tlsClientAuthority().isEmpty()) {
                throw new RuntimeException("Client certificate authority security/clients.pem is missing - see: https://cloud.vespa.ai/en/security-model#data-plane");
            }
            EndpointCertificateSecrets endpointCertificateSecrets = deployState.endpointCertificateSecrets().get();

            boolean enforceHandshakeClientAuth = context.properties().featureFlags().useAccessControlTlsHandshakeClientAuth() &&
                    cluster.getHttp().getAccessControl()
                    .map(accessControl -> accessControl.clientAuthentication)
                    .map(clientAuth -> clientAuth.equals(AccessControl.ClientAuthentication.need))
                    .orElse(false);

            connectorFactory = authorizeClient
                    ? HostedSslConnectorFactory.withProvidedCertificateAndTruststore(serverName, endpointCertificateSecrets, deployState.tlsClientAuthority().get())
                    : HostedSslConnectorFactory.withProvidedCertificate(serverName, endpointCertificateSecrets, enforceHandshakeClientAuth);
        } else {
            connectorFactory = HostedSslConnectorFactory.withDefaultCertificateAndTruststore(serverName);
        }
        cluster.getHttp().getAccessControl().ifPresent(accessControl -> accessControl.configureHostedConnector(connectorFactory));
        server.addConnector(connectorFactory);
    }

    private static boolean isHostedTenantApplication(ConfigModelContext context) {
        var deployState = context.getDeployState();
        boolean isTesterApplication = deployState.getProperties().applicationId().instance().isTester();
        return deployState.isHosted() && context.getApplicationType() == ApplicationType.DEFAULT && !isTesterApplication;
    }

    private static void addHostedImplicitHttpIfNotPresent(ApplicationContainerCluster cluster) {
        if (cluster.getHttp() == null) {
            cluster.setHttp(new Http(new FilterChains(cluster)));
        }
        JettyHttpServer httpServer = cluster.getHttp().getHttpServer().orElse(null);
        if (httpServer == null) {
            httpServer = new JettyHttpServer(new ComponentId("DefaultHttpServer"), cluster, cluster.isHostedVespa());
            cluster.getHttp().setHttpServer(httpServer);
        }
        int defaultPort = Defaults.getDefaults().vespaWebServicePort();
        boolean defaultConnectorPresent = httpServer.getConnectorFactories().stream().anyMatch(connector -> connector.getListenPort() == defaultPort);
        if (!defaultConnectorPresent) {
            httpServer.addConnector(new ConnectorFactory.Builder("SearchServer", defaultPort).build());
        }
    }

    private void addHostedImplicitAccessControlIfNotPresent(DeployState deployState, ApplicationContainerCluster cluster) {
        Http http = cluster.getHttp();
        if (http.getAccessControl().isPresent()) return; // access control added explicitly
        AthenzDomain tenantDomain = deployState.getProperties().athenzDomain().orElse(null);
        if (tenantDomain == null) return; // tenant domain not present, cannot add access control. this should eventually be a failure.
        new AccessControl.Builder(tenantDomain.value())
                .setHandlers(cluster)
                .readEnabled(false)
                .writeEnabled(false)
                .clientAuthentication(AccessControl.ClientAuthentication.need)
                .build()
                .configureHttpFilterChains(http);
    }

    private Http buildHttp(DeployState deployState, ApplicationContainerCluster cluster, Element httpElement) {
        Http http = new HttpBuilder().build(deployState, cluster, httpElement);

        if (networking == Networking.disable)
            http.removeAllServers();

        return http;
    }

    private void addRestApis(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        for (Element restApiElem : XML.getChildren(spec, "rest-api")) {
            cluster.addRestApi(
                    new RestApiBuilder().build(deployState, cluster, restApiElem));
        }
    }

    private void addServlets(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        for (Element servletElem : XML.getChildren(spec, "servlet"))
            cluster.addServlet(new ServletBuilder().build(deployState, cluster, servletElem));
    }

    private void addDocumentApi(Element spec, ApplicationContainerCluster cluster) {
        ContainerDocumentApi containerDocumentApi = buildDocumentApi(cluster, spec);
        if (containerDocumentApi == null) return;

        cluster.setDocumentApi(containerDocumentApi);
    }

    private void addDocproc(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        ContainerDocproc containerDocproc = buildDocproc(deployState, cluster, spec);
        if (containerDocproc == null) return;
        cluster.setDocproc(containerDocproc);

        ContainerDocproc.Options docprocOptions = containerDocproc.options;
        cluster.setMbusParams(new ApplicationContainerCluster.MbusParams(
                docprocOptions.maxConcurrentFactor, docprocOptions.documentExpansionFactor, docprocOptions.containerCoreMemory));
    }

    private void addSearch(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        Element searchElement = XML.getChild(spec, "search");
        if (searchElement == null) return;

        addIncludes(searchElement);
        cluster.setSearch(buildSearch(deployState, cluster, searchElement));

        addSearchHandler(cluster, searchElement);
        addGUIHandler(cluster);
        validateAndAddConfiguredComponents(deployState, cluster, searchElement, "renderer", ContainerModelBuilder::validateRendererElement);
    }

    private void addModelEvaluation(Element spec, ApplicationContainerCluster cluster, ConfigModelContext context) {
        Element modelEvaluationElement = XML.getChild(spec, "model-evaluation");
        if (modelEvaluationElement == null) return;

        RankProfileList profiles =
                context.vespaModel() != null ? context.vespaModel().rankProfileList() : RankProfileList.empty;
        cluster.setModelEvaluation(new ContainerModelEvaluation(cluster, profiles));
    }

    private void addProcessing(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        Element processingElement = XML.getChild(spec, "processing");
        if (processingElement == null) return;

        addIncludes(processingElement);
        cluster.setProcessingChains(new DomProcessingBuilder(null).build(deployState, cluster, processingElement),
                                    serverBindings(processingElement, ProcessingChains.defaultBindings).toArray(BindingPattern[]::new));
        validateAndAddConfiguredComponents(deployState, cluster, processingElement, "renderer", ContainerModelBuilder::validateRendererElement);
    }

    private ContainerSearch buildSearch(DeployState deployState, ApplicationContainerCluster containerCluster, Element producerSpec) {
        SearchChains searchChains = new DomSearchChainsBuilder(null, false)
                                            .build(deployState, containerCluster, producerSpec);

        ContainerSearch containerSearch = new ContainerSearch(containerCluster, searchChains, new ContainerSearch.Options());

        applyApplicationPackageDirectoryConfigs(deployState.getApplicationPackage(), containerSearch);
        containerSearch.setQueryProfiles(deployState.getQueryProfiles());
        containerSearch.setSemanticRules(deployState.getSemanticRules());

        return containerSearch;
    }

    private void applyApplicationPackageDirectoryConfigs(ApplicationPackage applicationPackage,ContainerSearch containerSearch) {
        PageTemplates.validate(applicationPackage);
        containerSearch.setPageTemplates(PageTemplates.create(applicationPackage));
    }

    private void addUserHandlers(DeployState deployState, ApplicationContainerCluster cluster, Element spec) {
        for (Element component: XML.getChildren(spec, "handler")) {
            cluster.addComponent(
                    new DomHandlerBuilder(cluster).build(deployState, cluster, component));
        }
    }

    private void checkVersion(Element spec) {
        String version = spec.getAttribute("version");

        if ( ! Version.fromString(version).equals(new Version(1))) {
            throw new RuntimeException("Expected container version to be 1.0, but got " + version);
        }
    }

    private void checkTagName(Element spec, DeployLogger logger) {
        if (spec.getTagName().equals(DEPRECATED_CONTAINER_TAG)) {
            logger.log(WARNING, "'" + DEPRECATED_CONTAINER_TAG + "' is deprecated as tag name. Use '" + CONTAINER_TAG + "' instead.");
        }
    }

    private void addNodes(ApplicationContainerCluster cluster, Element spec, ConfigModelContext context) {
        if (standaloneBuilder)
            addStandaloneNode(cluster);
        else
            addNodesFromXml(cluster, spec, context);
    }

    private void addStandaloneNode(ApplicationContainerCluster cluster) {
        ApplicationContainer container =  new ApplicationContainer(cluster, "standalone", cluster.getContainers().size(), cluster.isHostedVespa());
        cluster.addContainers(Collections.singleton(container));
    }

    static boolean incompatibleGCOptions(String jvmargs) {
        Pattern gcAlgorithm = Pattern.compile("-XX:[-+]Use.+GC");
        Pattern cmsArgs = Pattern.compile("-XX:[-+]*CMS");
        return (gcAlgorithm.matcher(jvmargs).find() ||cmsArgs.matcher(jvmargs).find());
    }

    private static String buildJvmGCOptions(DeployState deployState, String jvmGCOPtions) {
        String options = (jvmGCOPtions != null)
                ? jvmGCOPtions
                : deployState.getProperties().jvmGCOptions();
        return (options == null || options.isEmpty())
                ? (deployState.isHosted() ? ContainerCluster.CMS : ContainerCluster.G1GC)
                : options;
    }
    private static String getJvmOptions(ApplicationContainerCluster cluster, Element nodesElement, DeployLogger deployLogger) {
        String jvmOptions;
        if (nodesElement.hasAttribute(VespaDomBuilder.JVM_OPTIONS)) {
            jvmOptions = nodesElement.getAttribute(VespaDomBuilder.JVM_OPTIONS);
            if (nodesElement.hasAttribute(VespaDomBuilder.JVMARGS_ATTRIB_NAME)) {
                String jvmArgs = nodesElement.getAttribute(VespaDomBuilder.JVMARGS_ATTRIB_NAME);
                throw new IllegalArgumentException("You have specified both jvm-options='" + jvmOptions + "'" +
                        " and deprecated jvmargs='" + jvmArgs + "'. Merge jvmargs into jvm-options.");
            }
        } else {
            jvmOptions = nodesElement.getAttribute(VespaDomBuilder.JVMARGS_ATTRIB_NAME);
            if (incompatibleGCOptions(jvmOptions)) {
                deployLogger.log(WARNING, "You need to move out your GC related options from 'jvmargs' to 'jvm-gc-options'");
                cluster.setJvmGCOptions(ContainerCluster.G1GC);
            }
        }
        return jvmOptions;
    }

    private static String extractAttribute(Element element, String attrName) {
        return element.hasAttribute(attrName) ? element.getAttribute(attrName) : null;
    }

    void extractJvmFromLegacyNodesTag(List<ApplicationContainer> nodes, ApplicationContainerCluster cluster,
                                      Element nodesElement, ConfigModelContext context) {
        applyNodesTagJvmArgs(nodes, getJvmOptions(cluster, nodesElement, context.getDeployLogger()));

        if (cluster.getJvmGCOptions().isEmpty()) {
            String jvmGCOptions = extractAttribute(nodesElement, VespaDomBuilder.JVM_GC_OPTIONS);
            cluster.setJvmGCOptions(buildJvmGCOptions(context.getDeployState(), jvmGCOptions));
        }

        applyMemoryPercentage(cluster, nodesElement.getAttribute(VespaDomBuilder.Allocated_MEMORY_ATTRIB_NAME));
    }

    void extractJvmTag(List<ApplicationContainer> nodes, ApplicationContainerCluster cluster,
                       Element jvmElement, ConfigModelContext context) {
        applyNodesTagJvmArgs(nodes, jvmElement.getAttribute(VespaDomBuilder.OPTIONS));
        applyMemoryPercentage(cluster, jvmElement.getAttribute(VespaDomBuilder.Allocated_MEMORY_ATTRIB_NAME));
        String jvmGCOptions = extractAttribute(jvmElement, VespaDomBuilder.GC_OPTIONS);
        cluster.setJvmGCOptions(buildJvmGCOptions(context.getDeployState(), jvmGCOptions));
    }

    /**
     * Add nodes to cluster according to the given containerElement.
     *
     * Note: DO NOT change allocation behaviour to allow version X and Y of the config-model to allocate a different set
     * of nodes. Such changes must be guarded by a common condition (e.g. feature flag) so the behaviour can be changed
     * simultaneously for all active config models.
     */
    private void addNodesFromXml(ApplicationContainerCluster cluster, Element containerElement, ConfigModelContext context) {
        Element nodesElement = XML.getChild(containerElement, "nodes");
        if (nodesElement == null) {
            cluster.addContainers(allocateWithoutNodesTag(cluster, context));
        } else {
            List<ApplicationContainer> nodes = createNodes(cluster, containerElement, nodesElement, context);

            Element jvmElement = XML.getChild(nodesElement, "jvm");
            if (jvmElement == null) {
                extractJvmFromLegacyNodesTag(nodes, cluster, nodesElement, context);
            } else {
                extractJvmTag(nodes, cluster, jvmElement, context);
            }
            applyRoutingAliasProperties(nodes, cluster);
            applyDefaultPreload(nodes, nodesElement);
            String environmentVars = getEnvironmentVariables(XML.getChild(nodesElement, ENVIRONMENT_VARIABLES_ELEMENT));
            if (!environmentVars.isEmpty()) {
                cluster.setEnvironmentVars(environmentVars);
            }
            if (useCpuSocketAffinity(nodesElement))
                AbstractService.distributeCpuSocketAffinity(nodes);

            cluster.addContainers(nodes);
        }
    }

    private static String getEnvironmentVariables(Element environmentVariables) {
        StringBuilder sb = new StringBuilder();
        if (environmentVariables != null) {
            for (Element var: XML.getChildren(environmentVariables)) {
                sb.append(var.getNodeName()).append('=').append(var.getTextContent()).append(' ');
            }
        }
        return sb.toString();
    }
    
    private List<ApplicationContainer> createNodes(ApplicationContainerCluster cluster, Element containerElement, Element nodesElement, ConfigModelContext context) {
        if (nodesElement.hasAttribute("type")) // internal use for hosted system infrastructure nodes
            return createNodesFromNodeType(cluster, nodesElement, context);
        else if (nodesElement.hasAttribute("of")) // hosted node spec referencing a content cluster
            return createNodesFromContentServiceReference(cluster, nodesElement, context);
        else if (nodesElement.hasAttribute("count")) // regular, hosted node spec
            return createNodesFromNodeCount(cluster, containerElement, nodesElement, context);
        else if (cluster.isHostedVespa() && cluster.getZone().environment().isManuallyDeployed()) // default to 1 in manual zones
            return createNodesFromNodeCount(cluster, containerElement, nodesElement, context);
        else // the non-hosted option
            return createNodesFromNodeList(context.getDeployState(), cluster, nodesElement);
    }

    private static void applyRoutingAliasProperties(List<ApplicationContainer> result, ApplicationContainerCluster cluster) {
        if (!cluster.serviceAliases().isEmpty()) {
            result.forEach(container -> {
                container.setProp("servicealiases", cluster.serviceAliases().stream().collect(Collectors.joining(",")));
            });
        }
        if (!cluster.endpointAliases().isEmpty()) {
            result.forEach(container -> {
                container.setProp("endpointaliases", cluster.endpointAliases().stream().collect(Collectors.joining(",")));
            });
        }
    }
    
    private static void applyMemoryPercentage(ApplicationContainerCluster cluster, String memoryPercentage) {
        if (memoryPercentage == null || memoryPercentage.isEmpty()) return;
        memoryPercentage = memoryPercentage.trim();

        if ( ! memoryPercentage.endsWith("%"))
            throw new IllegalArgumentException("The memory percentage given for nodes in " + cluster +
                                               " must be an integer percentage ending by the '%' sign");
        memoryPercentage = memoryPercentage.substring(0, memoryPercentage.length()-1).trim();

        try {
            cluster.setMemoryPercentage(Integer.parseInt(memoryPercentage));
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("The memory percentage given for nodes in " + cluster +
                                               " must be an integer percentage ending by the '%' sign");
        }
    }

    /** Allocate a container cluster without a nodes tag */
    private List<ApplicationContainer> allocateWithoutNodesTag(ApplicationContainerCluster cluster, ConfigModelContext context) {
        DeployState deployState = context.getDeployState();
        HostSystem hostSystem = cluster.hostSystem();
        if (deployState.isHosted()) {
            // request just enough nodes to satisfy environment capacity requirement
            ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.container,
                                                          ClusterSpec.Id.from(cluster.getName()))
                                                 .vespaVersion(deployState.getWantedNodeVespaVersion())
                                                 .dockerImageRepository(deployState.getWantedDockerImageRepo())
                                                 .build();
            int nodeCount = deployState.zone().environment().isProduction() ? 2 : 1;
            Capacity capacity = Capacity.from(new ClusterResources(nodeCount, 1, NodeResources.unspecified()),
                                              false,
                                              !deployState.getProperties().isBootstrap());
            var hosts = hostSystem.allocateHosts(clusterSpec, capacity, log);
            return createNodesFromHosts(log, hosts, cluster);
        }
        else {
            return singleHostContainerCluster(cluster, hostSystem.getHost(Container.SINGLENODE_CONTAINER_SERVICESPEC), context);
        }
    }

    private List<ApplicationContainer> singleHostContainerCluster(ApplicationContainerCluster cluster, HostResource host, ConfigModelContext context) {
        ApplicationContainer node = new ApplicationContainer(cluster, "container.0", 0, cluster.isHostedVespa());
        node.setHostResource(host);
        node.initService(context.getDeployLogger());
        return List.of(node);
    }

    private List<ApplicationContainer> createNodesFromNodeCount(ApplicationContainerCluster cluster, Element containerElement, Element nodesElement, ConfigModelContext context) {
        NodesSpecification nodesSpecification = NodesSpecification.from(new ModelElement(nodesElement), context);
        Map<HostResource, ClusterMembership> hosts = nodesSpecification.provision(cluster.getRoot().hostSystem(),
                                                                                  ClusterSpec.Type.container,
                                                                                  ClusterSpec.Id.from(cluster.getName()), 
                                                                                  log,
                                                                                  hasZooKeeper(containerElement));
        return createNodesFromHosts(context.getDeployLogger(), hosts, cluster);
    }

    private List<ApplicationContainer> createNodesFromNodeType(ApplicationContainerCluster cluster, Element nodesElement, ConfigModelContext context) {
        NodeType type = NodeType.valueOf(nodesElement.getAttribute("type"));
        ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from(cluster.getName()))
                .vespaVersion(context.getDeployState().getWantedNodeVespaVersion())
                .dockerImageRepository(context.getDeployState().getWantedDockerImageRepo())
                .build();
        Map<HostResource, ClusterMembership> hosts = 
                cluster.getRoot().hostSystem().allocateHosts(clusterSpec,
                                                             Capacity.fromRequiredNodeType(type), log);
        return createNodesFromHosts(context.getDeployLogger(), hosts, cluster);
    }
    
    private List<ApplicationContainer> createNodesFromContentServiceReference(ApplicationContainerCluster cluster, Element nodesElement, ConfigModelContext context) {
        NodesSpecification nodeSpecification;
        try {
            nodeSpecification = NodesSpecification.from(new ModelElement(nodesElement), context);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(cluster + " contains an invalid reference", e);
        }
        String referenceId = nodesElement.getAttribute("of");
        cluster.setHostClusterId(referenceId);

        Map<HostResource, ClusterMembership> hosts = 
                StorageGroup.provisionHosts(nodeSpecification,
                                            referenceId, 
                                            cluster.getRoot().hostSystem(),
                                            context.getDeployLogger());
        return createNodesFromHosts(context.getDeployLogger(), hosts, cluster);
    }

    private List<ApplicationContainer> createNodesFromHosts(DeployLogger deployLogger, Map<HostResource, ClusterMembership> hosts, ApplicationContainerCluster cluster) {
        List<ApplicationContainer> nodes = new ArrayList<>();
        for (Map.Entry<HostResource, ClusterMembership> entry : hosts.entrySet()) {
            String id = "container." + entry.getValue().index();
            ApplicationContainer container = new ApplicationContainer(cluster, id, entry.getValue().retired(), entry.getValue().index(), cluster.isHostedVespa());
            container.setHostResource(entry.getKey());
            container.initService(deployLogger);
            nodes.add(container);
        }
        return nodes;
    }

    private List<ApplicationContainer> createNodesFromNodeList(DeployState deployState, ApplicationContainerCluster cluster, Element nodesElement) {
        List<ApplicationContainer> nodes = new ArrayList<>();
        int nodeIndex = 0;
        for (Element nodeElem: XML.getChildren(nodesElement, "node")) {
            nodes.add(new ContainerServiceBuilder("container." + nodeIndex, nodeIndex).build(deployState, cluster, nodeElem));
            nodeIndex++;
        }
        return nodes;
    }

    private static boolean useCpuSocketAffinity(Element nodesElement) {
        if (nodesElement.hasAttribute(VespaDomBuilder.CPU_SOCKET_AFFINITY_ATTRIB_NAME))
            return Boolean.parseBoolean(nodesElement.getAttribute(VespaDomBuilder.CPU_SOCKET_AFFINITY_ATTRIB_NAME));
        else
            return false;
    }

    private static void applyNodesTagJvmArgs(List<ApplicationContainer> containers, String jvmArgs) {
        for (Container container: containers) {
            if (container.getAssignedJvmOptions().isEmpty())
                container.prependJvmOptions(jvmArgs);
        }
    }

    private static void applyDefaultPreload(List<ApplicationContainer> containers, Element nodesElement) {
        if (! nodesElement.hasAttribute(VespaDomBuilder.PRELOAD_ATTRIB_NAME)) return;
        for (Container container: containers)
            container.setPreLoad(nodesElement.getAttribute(VespaDomBuilder.PRELOAD_ATTRIB_NAME));
    }

    private void addSearchHandler(ApplicationContainerCluster cluster, Element searchElement) {
        // Magic spell is needed to receive the chains config :-|
        cluster.addComponent(new ProcessingHandler<>(cluster.getSearch().getChains(),
                                                     "com.yahoo.search.searchchain.ExecutionFactory"));

        cluster.addComponent(
                new SearchHandler(
                        cluster,
                        serverBindings(searchElement, SearchHandler.DEFAULT_BINDING),
                        ContainerThreadpool.UserOptions.fromXml(searchElement).orElse(null)));
    }

    private void addGUIHandler(ApplicationContainerCluster cluster) {
        Handler<?> guiHandler = new GUIHandler();
        guiHandler.addServerBindings(SystemBindingPattern.fromHttpPath(GUIHandler.BINDING_PATH));
        cluster.addComponent(guiHandler);
    }


    private List<BindingPattern> serverBindings(Element searchElement, BindingPattern... defaultBindings) {
        List<Element> bindings = XML.getChildren(searchElement, "binding");
        if (bindings.isEmpty())
            return List.of(defaultBindings);

        return toBindingList(bindings);
    }

    private List<BindingPattern> toBindingList(List<Element> bindingElements) {
        List<BindingPattern> result = new ArrayList<>();

        for (Element element: bindingElements) {
            String text = element.getTextContent().trim();
            if (!text.isEmpty())
                result.add(UserBindingPattern.fromPattern(text));
        }

        return result;
    }

    private ContainerDocumentApi buildDocumentApi(ApplicationContainerCluster cluster, Element spec) {
        Element documentApiElement = XML.getChild(spec, "document-api");
        if (documentApiElement == null) return null;

        ContainerDocumentApi.Options documentApiOptions = DocumentApiOptionsBuilder.build(documentApiElement);
        return new ContainerDocumentApi(cluster, documentApiOptions);
    }

    private ContainerDocproc buildDocproc(DeployState deployState, ApplicationContainerCluster cluster, Element spec) {
        Element docprocElement = XML.getChild(spec, "document-processing");
        if (docprocElement == null)
            return null;

        addIncludes(docprocElement);
        DocprocChains chains = new DomDocprocChainsBuilder(null, false).build(deployState, cluster, docprocElement);

        ContainerDocproc.Options docprocOptions = DocprocOptionsBuilder.build(docprocElement);
        return new ContainerDocproc(cluster, chains, docprocOptions, !standaloneBuilder);
     }

    private void addIncludes(Element parentElement) {
        List<Element> includes = XML.getChildren(parentElement, IncludeDirs.INCLUDE);
        if (includes.isEmpty()) {
            return;
        }
        if (app == null) {
            throw new IllegalArgumentException("Element <include> given in XML config, but no application package given.");
        }
        for (Element include : includes) {
            addInclude(parentElement, include);
        }
    }

    private void addInclude(Element parentElement, Element include) {
        String dirName = include.getAttribute(IncludeDirs.DIR);
        app.validateIncludeDir(dirName);

        List<Element> includedFiles = Xml.allElemsFromPath(app, dirName);
        for (Element includedFile : includedFiles) {
            List<Element> includedSubElements = XML.getChildren(includedFile);
            for (Element includedSubElement : includedSubElements) {
                Node copiedNode = parentElement.getOwnerDocument().importNode(includedSubElement, true);
                parentElement.appendChild(copiedNode);
            }
        }
    }

    private static void addConfiguredComponents(DeployState deployState, ContainerCluster<? extends Container> cluster,
                                                Element spec, String componentName) {
        for (Element node : XML.getChildren(spec, componentName)) {
            cluster.addComponent(new DomComponentBuilder().build(deployState, cluster, node));
        }
    }

    private static void validateAndAddConfiguredComponents(DeployState deployState,
                                                           ContainerCluster<? extends Container> cluster,
                                                           Element spec, String componentName,
                                                           Consumer<Element> elementValidator) {
        for (Element node : XML.getChildren(spec, componentName)) {
            elementValidator.accept(node); // throws exception here if something is wrong
            cluster.addComponent(new DomComponentBuilder().build(deployState, cluster, node));
        }
    }

    private void addIdentityProvider(ApplicationContainerCluster cluster,
                                     List<ConfigServerSpec> configServerSpecs,
                                     HostName loadBalancerName,
                                     URI ztsUrl,
                                     String athenzDnsSuffix,
                                     Zone zone,
                                     DeploymentSpec spec) {
        spec.athenzDomain()
            .ifPresent(domain -> {
                AthenzService service = spec.instance(app.getApplicationId().instance())
                                            .flatMap(instanceSpec -> instanceSpec.athenzService(zone.environment(), zone.region()))
                                            .or(() -> spec.athenzService())
                                            .orElseThrow(() -> new RuntimeException("Missing Athenz service configuration in instance '" + app.getApplicationId().instance() + "'"));
            String zoneDnsSuffix = zone.environment().value() + "-" + zone.region().value() + "." + athenzDnsSuffix;
            IdentityProvider identityProvider = new IdentityProvider(domain, service, getLoadBalancerName(loadBalancerName, configServerSpecs), ztsUrl, zoneDnsSuffix, zone);
            cluster.addComponent(identityProvider);

            cluster.getContainers().forEach(container -> {
                container.setProp("identity.domain", domain.value());
                container.setProp("identity.service", service.value());
            });
        });
    }

    private HostName getLoadBalancerName(HostName loadbalancerName, List<ConfigServerSpec> configServerSpecs) {
        // Set lbaddress, or use first hostname if not specified.
        // TODO: Remove this method and use the loadbalancerName directly
        return Optional.ofNullable(loadbalancerName)
                .orElseGet(
                        () -> HostName.from(configServerSpecs.stream()
                                                    .findFirst()
                                                    .map(ConfigServerSpec::getHostName)
                                                    .orElse("unknown") // Currently unable to test this, hence the unknown
                        ));
    }

    private static boolean hasZooKeeper(Element spec) {
        return XML.getChild(spec, "zookeeper") != null;
    }

    /** Disallow renderers named "XmlRenderer" or "JsonRenderer" */
    private static void validateRendererElement(Element element) {
        String idAttr = element.getAttribute("id");

        if (idAttr.equals(xmlRendererId) || idAttr.equals(jsonRendererId)) {
            throw new IllegalArgumentException(String.format("Renderer id %s is reserved for internal use", idAttr));
        }
    }

    public static boolean isContainerTag(Element element) {
        return CONTAINER_TAG.equals(element.getTagName()) || DEPRECATED_CONTAINER_TAG.equals(element.getTagName());
    }

}
