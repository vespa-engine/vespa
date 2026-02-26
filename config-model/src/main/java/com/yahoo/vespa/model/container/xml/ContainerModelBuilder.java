// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container.xml;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.Version;
import com.yahoo.component.chain.dependencies.Dependencies;
import com.yahoo.component.chain.model.ChainedComponentModel;
import com.yahoo.config.application.Xml;
import com.yahoo.config.application.api.ApplicationFile;
import com.yahoo.config.application.api.ApplicationPackage;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ConfigServerSpec;
import com.yahoo.config.model.api.ContainerEndpoint;
import com.yahoo.config.model.api.TenantSecretStore;
import com.yahoo.config.model.application.provider.IncludeDirs;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.AthenzDomain;
import com.yahoo.config.provision.AthenzService;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DataplaneToken;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.SidecarProbe;
import com.yahoo.config.provision.SidecarSpec;
import com.yahoo.config.provision.Zone;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.container.bundle.BundleInstantiationSpecification;


import com.yahoo.container.logging.AccessLog;
import com.yahoo.container.logging.FileConnectionLog;
import com.yahoo.io.IOUtils;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig;
import com.yahoo.jdisc.http.filter.security.cloud.config.CloudTokenDataPlaneFilterConfig.Builder;


import com.yahoo.jdisc.http.server.jetty.VoidRequestLog;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.path.Path;
import com.yahoo.schema.OnnxModel;
import com.yahoo.schema.derived.FileDistributedOnnxModels;
import com.yahoo.schema.derived.RankProfileList;
import com.yahoo.search.rendering.RendererRegistry;
import com.yahoo.security.X509CertificateUtils;
import com.yahoo.text.Text;
import com.yahoo.text.XML;
import com.yahoo.vespa.defaults.Defaults;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.builder.xml.dom.BinaryUnit;
import com.yahoo.vespa.model.builder.xml.dom.DomComponentBuilder;
import com.yahoo.vespa.model.builder.xml.dom.DomHandlerBuilder;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.NodesSpecification;
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
import com.yahoo.vespa.model.container.DataplaneProxy;
import com.yahoo.vespa.model.container.DefaultThreadpoolProvider;
import com.yahoo.vespa.model.container.IdentityProvider;
import com.yahoo.vespa.model.container.PlatformBundles;
import com.yahoo.vespa.model.container.SecretStore;
import com.yahoo.vespa.model.container.component.AccessLogComponent;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.ConnectionLogComponent;
import com.yahoo.vespa.model.container.component.FileStatusHandlerComponent;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SignificanceModelRegistry;
import com.yahoo.vespa.model.container.component.SimpleComponent;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.component.UserBindingPattern;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.docproc.DocprocChains;
import com.yahoo.vespa.model.container.http.AccessControl;
import com.yahoo.vespa.model.container.http.Client;
import com.yahoo.vespa.model.container.http.ConnectorFactory;
import com.yahoo.vespa.model.container.http.Filter;
import com.yahoo.vespa.model.container.http.FilterBinding;
import com.yahoo.vespa.model.container.http.FilterChains;
import com.yahoo.vespa.model.container.http.Http;
import com.yahoo.vespa.model.container.http.HttpFilterChain;
import com.yahoo.vespa.model.container.http.JettyHttpServer;
import com.yahoo.vespa.model.container.http.ssl.HostedSslConnectorFactory;
import com.yahoo.vespa.model.container.http.ssl.HostedSslConnectorFactory.SslClientAuth;
import com.yahoo.vespa.model.container.http.xml.HttpBuilder;
import com.yahoo.vespa.model.container.processing.ProcessingChains;
import com.yahoo.vespa.model.container.search.ContainerSearch;
import com.yahoo.vespa.model.container.search.PageTemplates;
import com.yahoo.vespa.model.container.search.searchchain.SearchChains;
import com.yahoo.vespa.model.container.xml.document.DocumentFactoryBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.io.IOException;
import java.io.Reader;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.yahoo.vespa.model.container.ContainerCluster.VIP_HANDLER_BINDING;
import static java.util.logging.Level.WARNING;

/**
 * @author Tony Vaagenes
 * @author gjoranv
 */
public class ContainerModelBuilder extends ConfigModelBuilder<ContainerModel> {

    // Default path to vip status file for container in Hosted Vespa.
    static final String HOSTED_VESPA_STATUS_FILE = Defaults.getDefaults().underVespaHome("var/vespa/load-balancer/status.html");

    static final String HOSTED_VESPA_TENANT_PARENT_DOMAIN = "vespa.tenant.";

    //Path to vip status file for container in Hosted Vespa. Only used if set, else use HOSTED_VESPA_STATUS_FILE
    private static final String HOSTED_VESPA_STATUS_FILE_SETTING = "VESPA_LB_STATUS_FILE";

    private static final String CONTAINER_TAG = "container";
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
    protected DeployLogger deployLogger;

    public static final List<ConfigModelId> configModelIds = List.of(ConfigModelId.fromName(CONTAINER_TAG));

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
        deployLogger = modelContext.getDeployLogger();
        app = modelContext.getApplicationPackage();

        checkVersion(spec);

        ApplicationContainerCluster cluster = createContainerCluster(spec, modelContext);
        addClusterContent(cluster, spec, modelContext);
        cluster.setMessageBusEnabled(rpcServerEnabled);
        cluster.setRpcServerEnabled(rpcServerEnabled);
        cluster.setHttpServerEnabled(httpServerEnabled);
        model.setCluster(cluster);
    }

    private ApplicationContainerCluster createContainerCluster(Element spec, ConfigModelContext modelContext) {
        return new VespaDomBuilder.DomConfigProducerBuilderBase<ApplicationContainerCluster>() {
            @Override
            protected ApplicationContainerCluster doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element producerSpec) {
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
        addSecrets(cluster, spec, deployState);

        addProcessing(deployState, spec, cluster, context);
        addSearch(deployState, spec, cluster, context);
        addDocproc(deployState, spec, cluster);
        addDocumentApi(deployState, spec, cluster, context);  // NOTE: Must be done after addSearch
        addDefaultThreadpool(deployState, spec, cluster);

        cluster.addDefaultHandlersExceptStatus();
        addStatusHandlers(cluster, context.getDeployState().isHosted());
        addUserHandlers(deployState, cluster, spec, context);

        addClients(deployState, spec, cluster);
        addHttp(deployState, spec, cluster, context);

        addNodes(cluster, spec, context);

        addModelEvaluationRuntime(deployState, cluster);
        addModelEvaluation(spec, cluster, context); // NOTE: Must be done after addNodes
        addInferenceMemory(spec, cluster);

        addServerProviders(deployState, spec, cluster);

        if (!standaloneBuilder) cluster.addAllPlatformBundles();

        // Must be added after nodes:
        addAccessLogs(deployState, cluster, spec);
        addDeploymentSpecConfig(cluster, context, deployState.getDeployLogger());
        addZooKeeper(cluster, spec);
        addAthenzServiceIdentityProvider(cluster, context);

        addParameterStoreValidationHandler(cluster, deployState);
    }

    private boolean shouldUseTriton(ApplicationContainerCluster cluster, DeployState deployState) {
        var isPublicCloud = deployState.zone().system().isPublicCloudLike();
        var hasOnnxModels =  !cluster.onnxModelCostCalculator().models().isEmpty();
        var useTritonFeatureFlagValue = deployState.featureFlags().useTriton();
        return useTritonFeatureFlagValue && isPublicCloud && hasOnnxModels;
    }

    private List<SidecarSpec> getSidecars(ApplicationContainerCluster cluster, DeployState deployState, NodesSpecification nodesSpecification) {
        var sidecars = new ArrayList<SidecarSpec>();

        if (shouldUseTriton(cluster, deployState)) {
            var hasGpu = !nodesSpecification.minResources().nodeResources().gpuResources().isZero();

            // Hardcoded values for changes to be reviewed and tested
            var spec = SidecarSpec.builder()
                    .id(0)
                    .name("triton")
                    .image(DockerImage.fromString("nvcr.io/nvidia/tritonserver:25.12-py3"))
                    .minCpu(1) // Must have at least one CPU
                    .hasGpu(hasGpu)
                    .volumeMounts(List.of("/models"))
                    .command(List.of("tritonserver", "--model-repository=/models", "--model-control-mode=explicit"))
                    .livenessProbe(new SidecarProbe(new SidecarProbe.HttpGetAction("/v2/health/live", 8000), 10, 5, 2, 3))
                    .build();

            sidecars.add(spec);
        }

        return sidecars;
    }

    private void addParameterStoreValidationHandler(ApplicationContainerCluster cluster, DeployState deployState) {
        if ( ! deployState.isHosted()) return;
        // Always add platform bundle. Cannot be controlled by a feature flag as platform bundle cannot change.
        cluster.addPlatformBundle(PlatformBundles.absoluteBundlePath("cloud-aws", PlatformBundles.JarSuffix.DEPLOY));
        if (deployState.zone().system().isPublicCloudLike()) {
            BindingPattern bindingPattern = SystemBindingPattern.fromHttpPath("/validate-secret-store");
            Handler handler = new Handler(
                    new ComponentModel("com.yahoo.jdisc.cloud.aws.AwsParameterStoreValidationHandler", null, "cloud-aws", null));
            handler.addServerBindings(bindingPattern);
            cluster.addComponent(handler);
        }
    }

    private void addZooKeeper(ApplicationContainerCluster cluster, Element spec) {
        Element zooKeeper = getZooKeeper(spec);
        if (zooKeeper == null) return;

        long nonRetiredNodes = cluster.getContainers().stream().filter(c -> !c.isRetired()).count();
        if (nonRetiredNodes < MIN_ZOOKEEPER_NODE_COUNT || nonRetiredNodes > MAX_ZOOKEEPER_NODE_COUNT || nonRetiredNodes % 2 == 0) {
            throw new IllegalArgumentException("Cluster with ZooKeeper needs an odd number of nodes, between " +
                                               MIN_ZOOKEEPER_NODE_COUNT + " and " + MAX_ZOOKEEPER_NODE_COUNT +
                                               ", have " + nonRetiredNodes + " non-retired");
        }
        cluster.addSimpleComponent("com.yahoo.vespa.curator.Curator", null, "zkfacade");
        cluster.addSimpleComponent("com.yahoo.vespa.curator.CuratorWrapper", null, "zkfacade");
        String sessionTimeoutSeconds = zooKeeper.getAttribute("session-timeout-seconds");
        if ( ! sessionTimeoutSeconds.isBlank()) {
            try {
                int timeoutSeconds = Integer.parseInt(sessionTimeoutSeconds);
                if (timeoutSeconds <= 0) throw new IllegalArgumentException("must be a positive value");
                cluster.setZookeeperSessionTimeoutSeconds(timeoutSeconds);
            }
            catch (RuntimeException e) {
                throw new IllegalArgumentException("invalid zookeeper session-timeout-seconds '" + sessionTimeoutSeconds + "'", e);
            }
        }

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

    private void addSecrets(ApplicationContainerCluster cluster, Element spec, DeployState deployState) {
        if ( ! deployState.isHosted() || ! cluster.getZone().system().isPublicCloudLike())
            return;
        Element secretsElement = XML.getChild(spec, "secrets");
        if (secretsElement != null) {
            CloudSecrets secretsConfig = new CloudSecrets();
            for (Element element : XML.getChildren(secretsElement)) {
                String key = element.getTagName();
                String name = element.getAttribute("name");
                String vault = element.getAttribute("vault");
                secretsConfig.addSecret(key, name, vault);
            }
            cluster.setTenantSecretsConfig(secretsConfig);
            cluster.addComponent(new CloudAsmSecrets(deployState.getProperties().ztsUrl(),
                                                     deployState.getProperties().tenantSecretDomain(),
                                                     deployState.zone().system(),
                                                     deployState.getProperties().applicationId().tenant(),
                                                     deployState.getProperties().tenantVaults()));
        }
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
        if ( ! deployState.isHosted()) return;
        if ( ! cluster.getZone().system().isPublicCloudLike())
            throw new IllegalArgumentException("Cloud secret store is not supported in non-public system, see the documentation");
        CloudSecretStore cloudSecretStore = new CloudSecretStore();
        Map<String, TenantSecretStore> secretStoresByName = deployState.getProperties().tenantSecretStores()
                .stream()
                .collect(Collectors.toMap(
                        TenantSecretStore::getName,
                        store -> store
                ));
        Element store = XML.getChild(secretStoreElement, "store");
        for (Element group : XML.getChildren(store, "aws-parameter-store")) {
            String account = group.getAttribute("account");
            String region = group.getAttribute("aws-region");
            TenantSecretStore secretStore = secretStoresByName.get(account);

            if (secretStore == null)
                throw new IllegalArgumentException("No configured secret store named " + account);

            if (secretStore.getExternalId().isEmpty())
                throw new IllegalArgumentException("No external ID has been set for secret store " + secretStore.getName());

            cloudSecretStore.addConfig(account, region, secretStore.getAwsId(), secretStore.getRole(), secretStore.getExternalId().get());
        }

        cluster.addComponent(cloudSecretStore);
    }

    private void addAthenzServiceIdentityProvider(ApplicationContainerCluster cluster, ConfigModelContext context) {
        if ( ! context.getDeployState().isHosted()) return;
        if ( ! context.getDeployState().zone().system().isPublicCloudLike()) return; // Non-public is handled by deployment spec config.
        var appContext = context.getDeployState().zone().environment().isManuallyDeployed() ? "sandbox" : "production";
        addIdentityProvider(cluster,
                            context.getDeployState().getProperties().configServerSpecs(),
                            context.getDeployState().getProperties().loadBalancerName(),
                            context.getDeployState().getProperties().ztsUrl(),
                            context.getDeployState().getProperties().athenzDnsSuffix(),
                            context.getDeployState().zone(),
                            AthenzDomain.from(HOSTED_VESPA_TENANT_PARENT_DOMAIN + context.properties().applicationId().tenant().value()),
                            AthenzService.from(Text.format("%s-%s", context.properties().applicationId().application().value(), appContext)));
    }

    private void addDeploymentSpecConfig(ApplicationContainerCluster cluster, ConfigModelContext context, DeployLogger deployLogger) {
        if ( ! context.getDeployState().isHosted()) return;
        DeploymentSpec deploymentSpec = app.getDeploymentSpec();
        if (deploymentSpec.isEmpty()) return;

        for (var deprecatedElement : deploymentSpec.deprecatedElements()) {
            deployLogger.logApplicationPackage(WARNING, deprecatedElement.humanReadableString());
        }

        addIdentityProvider(cluster,
                            context.getDeployState().getProperties().configServerSpecs(),
                            context.getDeployState().getProperties().loadBalancerName(),
                            context.getDeployState().getProperties().ztsUrl(),
                            context.getDeployState().getProperties().athenzDnsSuffix(),
                            context.getDeployState().zone(),
                            deploymentSpec);
        addRotationProperties(cluster, context.getDeployState().getEndpoints());
    }

    private void addRotationProperties(ApplicationContainerCluster cluster, Set<ContainerEndpoint> endpoints) {
        cluster.getContainers().forEach(container -> {
            setRotations(container, endpoints, cluster.getName());
            container.setProp("activeRotation", "true"); // TODO(mpolden): This is unused and should be removed
        });
    }

    private void setRotations(Container container, Set<ContainerEndpoint> endpoints, String containerClusterName) {
        var rotationsProperty = endpoints.stream()
                .filter(endpoint -> endpoint.clusterId().equals(containerClusterName))
                // Only consider global endpoints.
                .filter(endpoint -> endpoint.scope() == ApplicationClusterEndpoint.Scope.global)
                .flatMap(endpoint -> endpoint.names().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        // Build the comma delimited list of endpoints this container should be known as.
        // Confusingly called 'rotations' for legacy reasons.
        container.setProp("rotations", String.join(",", rotationsProperty));
    }

    private void addConfiguredComponents(DeployState deployState, ApplicationContainerCluster cluster, Element parent) {
        for (Element components : XML.getChildren(parent, "components")) {
            addIncludes(components);
            addConfiguredComponents(deployState, cluster, components, "component");
        }
        addConfiguredComponents(deployState, cluster, parent, "component");
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

    private void addServerProviders(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        addConfiguredComponents(deployState, cluster, spec, "server");
    }

    protected void addAccessLogs(DeployState deployState, ApplicationContainerCluster cluster, Element spec) {
        List<Element> accessLogElements = getAccessLogElements(spec);

        if (accessLogElements.isEmpty() && deployState.getAccessLoggingEnabledByDefault()) {
            cluster.addAccessLog();
        } else {
            if (cluster.isHostedVespa()) {
                deployLogger.logApplicationPackage(WARNING, "Applications are not allowed to override the 'accesslog' element");
            } else {
                List<AccessLogComponent> components = new ArrayList<>();
                for (Element accessLog : accessLogElements) {
                    AccessLogBuilder.buildIfNotDisabled(deployState, cluster, accessLog).ifPresent(accessLogComponent -> {
                        components.add(accessLogComponent);
                        cluster.addComponent(accessLogComponent);
                        // The request content logging configuration is not really tied to the access log, but instead to each connector,
                        // as each connector must know whether to log the request content or not at the start of processing a request.
                        // If multiple access logs are configured with different request content logging configuration,
                        // the effective configuration for both will be the union set.
                        Optional.ofNullable(cluster.getHttp())
                                .flatMap(Http::getHttpServer)
                                .ifPresent(jhs -> addRequestContentLogging(jhs, accessLogComponent.getRequestContent()));
                        for (var c : cluster.getContainers()) {
                            addRequestContentLogging(c.getDefaultHttpServer(), accessLogComponent.getRequestContent());
                        }
                    });
                }
                if ( ! components.isEmpty()) {
                    cluster.removeSimpleComponent(VoidRequestLog.class);
                    cluster.addSimpleComponent(AccessLog.class);
                }
            }
        }

        // Add connection log if access log is configured
        if (cluster.getAllComponents().stream().anyMatch(component -> component instanceof AccessLogComponent))
            cluster.addComponent(new ConnectionLogComponent(cluster, FileConnectionLog.class, "access"));
    }

    private static void addRequestContentLogging(
            JettyHttpServer httpServer, Set<AccessLogComponent.RequestContentItem> requestContent) {
                httpServer.getConnectorFactories()
                        .forEach(c -> c.addRequestContentLogging(requestContent));
    }

    private List<Element> getAccessLogElements(Element spec) {
        return XML.getChildren(spec, "accesslog");
    }

    protected void addHttp(DeployState deployState, Element spec, ApplicationContainerCluster cluster, ConfigModelContext context) {
        Element httpElement = XML.getChild(spec, "http");
        if (httpElement != null) {
            cluster.setHttp(buildHttp(deployState, cluster, httpElement, context));
        }
        if (isHostedTenantApplication(context)) {
            addHostedImplicitHttpIfNotPresent(deployState, cluster);
            addHostedImplicitAccessControlIfNotPresent(deployState, cluster);
            addDefaultConnectorHostedFilterBinding(cluster);
            addCloudMtlsConnector(deployState, cluster);
            addCloudDataPlaneFilter(deployState, cluster);
            addCloudTokenSupport(deployState, cluster);
        }
    }

    private static void addCloudDataPlaneFilter(DeployState deployState, ApplicationContainerCluster cluster) {
        if (!deployState.isHosted() || !deployState.zone().system().isPublicCloudLike()) return;

        var dataplanePort = getMtlsDataplanePort(deployState, cluster);
        // Setup secure filter chain
        var secureChain = new HttpFilterChain("cloud-data-plane-secure", HttpFilterChain.Type.SYSTEM);
        secureChain.addInnerComponent(new CloudDataPlaneFilter(cluster, deployState));
        cluster.getHttp().getFilterChains().add(secureChain);
        // Set cloud data plane filter as default request filter chain for data plane connector
        cluster.getHttp().getHttpServer().orElseThrow().getConnectorFactories().stream()
                .filter(c -> c.getListenPort() == dataplanePort).findAny().orElseThrow()
                .setDefaultRequestFilterChain(secureChain.getComponentId());

        // Setup insecure filter chain
        var insecureChain = new HttpFilterChain("cloud-data-plane-insecure", HttpFilterChain.Type.SYSTEM);
        insecureChain.addInnerComponent(new Filter(
                new ChainedComponentModel(
                        new BundleInstantiationSpecification(
                                new ComponentSpecification("com.yahoo.jdisc.http.filter.security.misc.NoopFilter"),
                                null, new ComponentSpecification("container-disc")),
                        Dependencies.emptyDependencies())));
        cluster.getHttp().getFilterChains().add(insecureChain);
        var insecureChainComponentSpec = new ComponentSpecification(insecureChain.getComponentId().toString());
        FilterBinding insecureBinding =
                FilterBinding.create(FilterBinding.Type.REQUEST, insecureChainComponentSpec, VIP_HANDLER_BINDING);
        cluster.getHttp().getBindings().add(insecureBinding);
        // Set insecure filter as default request filter chain for default connector
        cluster.getHttp().getHttpServer().orElseThrow().getConnectorFactories().stream()
                .filter(c -> c.getListenPort() == Defaults.getDefaults().vespaWebServicePort()).findAny().orElseThrow()
                .setDefaultRequestFilterChain(insecureChain.getComponentId());

    }

    protected void addClients(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        if (!deployState.isHosted() || !deployState.zone().system().isPublicCloudLike()) return;

        List<Client> clients;
        Element clientsElement = XML.getChild(spec, "clients");
        boolean legacyMode = false;
        if (clientsElement == null) {
            clients = List.of(new Client(
                    "default", List.of(), getCertificates(app.getFile(Path.fromString("security/clients.pem"))), List.of()));
            legacyMode = true;
        } else {
            clients = XML.getChildren(clientsElement, "client").stream()
                    .flatMap(elem -> getClient(elem, deployState).stream())
                    .toList();
            boolean atLeastOneClientWithCertificate = clients.stream().anyMatch(client -> !client.certificates().isEmpty());
            if (!atLeastOneClientWithCertificate)
                throw new IllegalArgumentException("At least one client must require a certificate");

            List<String> duplicates = clients.stream().collect(Collectors.groupingBy(Client::id))
                    .entrySet().stream().filter(entry -> entry.getValue().size() > 1)
                    .map(Map.Entry::getKey).sorted().toList();
            if (! duplicates.isEmpty()) {
                throw new IllegalArgumentException("Duplicate client ids: " + duplicates);
            }
        }

        List<X509Certificate> operatorAndTesterCertificates = deployState.getProperties().operatorCertificates();
        if(!operatorAndTesterCertificates.isEmpty())
            clients = Stream.concat(clients.stream(), Stream.of(Client.internalClient(operatorAndTesterCertificates))).toList();
        cluster.setClients(legacyMode, clients);
    }

    private Optional<Client> getClient(Element clientElement, DeployState state) {
        String clientId = XML.attribute("id", clientElement).orElseThrow();
        if (clientId.startsWith("_"))
            throw new IllegalArgumentException(Text.format("Invalid client id '%s', id cannot start with '_'", clientId));
        var permissions = XML.attribute("permissions", clientElement)
                .map(Client.Permission::fromCommaSeparatedString).orElse(Set.of());

        var certificates = XML.getChildren(clientElement, "certificate").stream()
                .flatMap(certElem -> {
                    var file = app.getFile(Path.fromString(certElem.getAttribute("file")));
                    if (!file.exists()) {
                        throw new IllegalArgumentException(Text.format("Certificate file '%s' for client '%s' does not exist", file.getPath().getRelative(), clientId));
                    }
                    return getCertificates(file).stream();
                })
                .toList();
        // A client cannot use both tokens and certificates
        if (!certificates.isEmpty()) return Optional.of(new Client(clientId, permissions, certificates, List.of()));

        var knownTokens = state.getProperties().dataplaneTokens().stream()
                .collect(Collectors.toMap(DataplaneToken::tokenId, Function.identity()));

        var referencedTokens = XML.getChildren(clientElement, "token").stream()
                .map(elem -> {
                    var tokenId = elem.getAttribute("id");
                    var token = knownTokens.get(tokenId);
                    if (token == null)
                        deployLogger.logApplicationPackage(
                                WARNING, Text.format("Token '%s' for client '%s' does not exist", tokenId, clientId));
                    return token;
                })
                .filter(token -> {
                    if (token == null) return false;
                    boolean empty = token.versions().isEmpty();
                    if (empty)
                        deployLogger.logApplicationPackage(
                                WARNING, Text.format("Token '%s' for client '%s' has no active versions", token.tokenId(), clientId));
                    return !empty;
                })
                .toList();

        // Don't include 'client' that refers to token without versions
        if (referencedTokens.isEmpty()) {
            deployLogger.log(Level.INFO, Text.format("Skipping client '%s' as it does not refer to any activate tokens", clientId));
            return Optional.empty();
        }

        return Optional.of(new Client(clientId, permissions, List.of(), referencedTokens));
    }

    private List<X509Certificate> getCertificates(ApplicationFile file) {
        if (!file.exists()) return List.of();
        try {
            Reader reader = file.createReader();
            String certPem = IOUtils.readAll(reader);
            reader.close();
            List<X509Certificate> x509Certificates;
            try {
                x509Certificates = X509CertificateUtils.certificateListFromPem(certPem);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(Text.format("File %s contains an invalid certificate", file.getPath().getRelative()), e);
            }
            if (x509Certificates.isEmpty()) {
                throw new IllegalArgumentException(Text.format("File %s does not contain any certificates.", file.getPath().getRelative()));
            }
            return x509Certificates;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void addDefaultConnectorHostedFilterBinding(ApplicationContainerCluster cluster) {
        cluster.getHttp().getAccessControl()
                .ifPresent(accessControl -> accessControl.configureDefaultHostedConnector(cluster.getHttp()));
    }

    private void addCloudMtlsConnector(DeployState state, ApplicationContainerCluster cluster) {
        JettyHttpServer server = cluster.getHttp().getHttpServer().get();
        String serverName = server.getComponentId().getName();

        // If the deployment contains certificate/private key reference, setup TLS port
        var builder = HostedSslConnectorFactory.builder(serverName, getMtlsDataplanePort(state, cluster))
                .proxyProtocol(state.zone().cloud().useProxyProtocol() || enableTokenSupport(state, cluster))
                .tlsCiphersOverride(state.getProperties().tlsCiphersOverride())
                .endpointConnectionTtl(state.getProperties().endpointConnectionTtl())
                .requestPrefixForLoggingContent(state.getProperties().requestPrefixForLoggingContent())
                .httpComplianceViolations(state.getProperties().jdiscHttpComplianceViolations());
        var endpointCert = state.endpointCertificateSecrets().orElse(null);
        if (endpointCert != null) {
            builder.endpointCertificate(endpointCert);
            Set<String> mtlsEndpointNames = state.getEndpoints().stream()
                    .filter(endpoint -> endpoint.authMethod() == ApplicationClusterEndpoint.AuthMethod.mtls)
                    .flatMap(endpoint -> endpoint.names().stream())
                    .collect(Collectors.toSet());
            builder.knownServerNames(mtlsEndpointNames);
            boolean isPublic = state.zone().system().isPublicCloudLike();
            List<X509Certificate> clientCertificates = getClientCertificates(cluster);
            if (isPublic) {
                if (clientCertificates.isEmpty())
                    throw new IllegalArgumentException("Client certificate authority security/clients.pem is missing - " +
                                                               "see: https://docs.vespa.ai/en/security/guide.html#data-plane");
                builder.tlsCaCertificatesPem(X509CertificateUtils.toPem(clientCertificates))
                        .clientAuth(SslClientAuth.WANT_WITH_ENFORCER);
            } else {
                builder.tlsCaCertificatesPath("/opt/yahoo/share/ssl/certs/athenz_tw_certificate_bundle.pem");
                var needAuth = cluster.getHttp().getAccessControl()
                        .map(accessControl -> accessControl.clientAuthentication)
                        .map(clientAuth -> clientAuth == AccessControl.ClientAuthentication.need)
                        .orElse(false);
                builder.clientAuth(needAuth ? SslClientAuth.NEED : SslClientAuth.WANT);
            }
        } else {
            builder.clientAuth(SslClientAuth.WANT_WITH_ENFORCER);
        }
        var connectorFactory = builder.build();
        cluster.getHttp().getAccessControl().ifPresent(accessControl -> accessControl.configureHostedConnector(connectorFactory));
        server.addConnector(connectorFactory);
    }

    private void addCloudTokenSupport(DeployState state, ApplicationContainerCluster cluster) {
        var server = cluster.getHttp().getHttpServer().get();
        if (!enableTokenSupport(state, cluster)) return;
        Set<String> tokenEndpoints = tokenEndpoints(state, cluster).stream()
                .map(ContainerEndpoint::names)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        var endpointCert = state.endpointCertificateSecrets().orElseThrow();
        int tokenPort = getTokenDataplanePort(state, cluster).orElseThrow();

        // Set up component to generate proxy cert if token support is enabled
        cluster.addSimpleComponent("com.yahoo.vespa.cloud.tenant.dataplane.DataplaneProxyCredentials", null, "cloud-tenant");
        cluster.addSimpleComponent("com.yahoo.vespa.cloud.tenant.dataplane.DataplaneProxyService", null, "cloud-tenant");
        var dataplaneProxy = new DataplaneProxy(
                getMtlsDataplanePort(state, cluster),
                tokenPort,
                endpointCert.certificate(),
                endpointCert.key(),
                tokenEndpoints);
        cluster.addComponent(dataplaneProxy);

        // Setup dedicated connector
        var connector = HostedSslConnectorFactory.builder(server.getComponentId().getName()+"-token", tokenPort)
                .tokenEndpoint(true)
                .proxyProtocol(false)
                .endpointCertificate(endpointCert)
                .remoteAddressHeader("X-Forwarded-For")
                .remotePortHeader("X-Forwarded-Port")
                .clientAuth(SslClientAuth.NEED)
                .knownServerNames(tokenEndpoints)
                .requestPrefixForLoggingContent(state.getProperties().requestPrefixForLoggingContent())
                .httpComplianceViolations(state.getProperties().jdiscHttpComplianceViolations())
                .build();
        server.addConnector(connector);

        // Setup token filter chain
        var tokenChain = new HttpFilterChain("cloud-token-data-plane-secure", HttpFilterChain.Type.SYSTEM);
        var tokenFilter = new CloudTokenDataPlaneFilter(cluster, state);
        tokenChain.addInnerComponent(tokenFilter);
        cluster.getHttp().getFilterChains().add(tokenChain);

        // Set as default filter for token port
        cluster.getHttp().getHttpServer().orElseThrow().getConnectorFactories().stream()
                .filter(c -> c.getListenPort() == tokenPort).findAny().orElseThrow()
                .setDefaultRequestFilterChain(tokenChain.getComponentId());

        // Set up handler that tells what fingerprints are known to the container
        class CloudTokenDataPlaneHandler extends Handler implements CloudTokenDataPlaneFilterConfig.Producer {
            CloudTokenDataPlaneHandler() {
                super(new ComponentModel("com.yahoo.jdisc.http.filter.security.cloud.CloudTokenDataPlaneHandler", null, "cloud-tenant", null));
                addServerBindings(SystemBindingPattern.fromHttpPortAndPath(Defaults.getDefaults().vespaWebServicePort(), "/data-plane-tokens/v1"));
            }
            @Override public void getConfig(Builder builder) { tokenFilter.getConfig(builder); }
        }
        cluster.addComponent(new CloudTokenDataPlaneHandler());
    }

    // Returns the client certificates of the clients defined for an application cluster
    private List<X509Certificate> getClientCertificates(ApplicationContainerCluster cluster) {
        return cluster.getClients()
                .stream()
                .map(Client::certificates)
                .flatMap(Collection::stream)
                .toList();
    }

    private static boolean isHostedTenantApplication(ConfigModelContext context) {
        return context.getDeployState().isHostedTenantApplication(context.getApplicationType());
    }

    private static void addHostedImplicitHttpIfNotPresent(DeployState deployState, ApplicationContainerCluster cluster) {
        if (cluster.getHttp() == null) {
            cluster.setHttp(new Http(new FilterChains(cluster)));
        }
        JettyHttpServer httpServer = cluster.getHttp().getHttpServer().orElse(null);
        if (httpServer == null) {
            httpServer = new JettyHttpServer("DefaultHttpServer", cluster, deployState);
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
                .clientAuthentication(AccessControl.ClientAuthentication.need)
                .build()
                .configureHttpFilterChains(http);
    }

    private Http buildHttp(DeployState deployState, ApplicationContainerCluster cluster, Element httpElement, ConfigModelContext context) {
        Http http = new HttpBuilder(portBindingOverride(deployState, context, cluster)).build(deployState, cluster, httpElement);

        if (networking == Networking.disable)
            http.removeAllServers();

        return http;
    }

    private void addDocumentApi(DeployState deployState, Element spec, ApplicationContainerCluster cluster, ConfigModelContext context) {
        cluster.setDocumentApi(buildDocumentApi(deployState, cluster, spec, context));
    }

    private void addDocproc(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        ContainerDocproc containerDocproc = buildDocproc(deployState, cluster, spec);
        if (containerDocproc == null) return;
        cluster.setDocproc(containerDocproc);

        ContainerDocproc.Options docprocOptions = containerDocproc.options;
        cluster.setMbusParams(new ApplicationContainerCluster.MbusParams(
                docprocOptions.maxConcurrentFactor, docprocOptions.documentExpansionFactor, docprocOptions.containerCoreMemory));
    }

    private void addSearch(DeployState deployState, Element spec, ApplicationContainerCluster cluster, ConfigModelContext context) {
        Element searchElement = XML.getChild(spec, "search");
        if (searchElement == null) return;

        addIncludes(searchElement);
        cluster.setSearch(buildSearch(deployState, cluster, searchElement));

        addSearchHandler(deployState, cluster, searchElement, context);

        validateAndAddConfiguredComponents(deployState, cluster, searchElement, "renderer", ContainerModelBuilder::validateRendererElement);

        addSignificance(deployState, searchElement, cluster);
    }

    private void addDefaultThreadpool(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        Element threadpoolElement = XML.getChild(spec, "threadpool");
        if (threadpoolElement == null) {
            cluster.setDefaultThreadpoolProvider(new DefaultThreadpoolProvider(cluster));
        } else {
            var options = ContainerThreadpoolOptionsBuilder.build(deployState, spec);
            cluster.setDefaultThreadpoolProvider(new DefaultThreadpoolProvider(deployState, cluster, options));
        }
    }

    private void addSignificance(DeployState deployState, Element spec, ApplicationContainerCluster cluster) {
        Element significanceElement = XML.getChild(spec, "significance");

        SignificanceModelRegistry significanceModelRegistry = new SignificanceModelRegistry(deployState, significanceElement);
        cluster.addComponent(significanceModelRegistry);

    }

    private void addModelEvaluation(Element spec, ApplicationContainerCluster cluster, ConfigModelContext context) {
        Element modelEvaluationElement = XML.getChild(spec, "model-evaluation");
        if (modelEvaluationElement == null) return;

        RankProfileList profiles =
                context.vespaModel() != null ? context.vespaModel().rankProfileList() : RankProfileList.empty;

        // Create a copy of models so each cluster can have its own specific settings
        FileDistributedOnnxModels models = profiles.getOnnxModels().clone();

        Element onnxElement = XML.getChild(modelEvaluationElement, "onnx");
        Element modelsElement = XML.getChild(onnxElement, "models");
        for (Element modelElement : XML.getChildren(modelsElement, "model") ) {
            OnnxModel onnxModel = models.asMap().get(modelElement.getAttribute("name"));
            if (onnxModel == null) {
                String availableModels = String.join(", ", profiles.getOnnxModels().asMap().keySet());
                context.getDeployState().getDeployLogger().logApplicationPackage(WARNING,
                        "Model '" + modelElement.getAttribute("name") + "' not found. Available ONNX " +
                        "models are: " + availableModels + ". Skipping this configuration.");
                continue;
            }
            onnxModel.setStatelessExecutionMode(getStringValue(modelElement, "execution-mode", null));
            onnxModel.setStatelessInterOpThreads(getIntValue(modelElement, "interop-threads", -1));
            onnxModel.setStatelessIntraOpThreads(getIntValue(modelElement, "intraop-threads", -1));
            Element gpuDeviceElement = XML.getChild(modelElement, "gpu-device");
            if (gpuDeviceElement != null) {
                int gpuDevice = Integer.parseInt(gpuDeviceElement.getTextContent());
                boolean hasGpu = cluster.getContainers().stream().anyMatch(container -> container.getHostResource() != null &&
                                                                                        !container.getHostResource().realResources().gpuResources().isZero());
                onnxModel.setGpuDevice(gpuDevice, hasGpu);
            }
        }
        for (OnnxModel onnxModel : models.asMap().values())
            cluster.onnxModelCostCalculator().registerModel(context.getApplicationPackage().getFile(onnxModel.getFilePath()), onnxModel.onnxModelOptions());

        cluster.setModelEvaluation(new ContainerModelEvaluation(cluster, profiles, models));
    }

    private String getStringValue(Element element, String name, String defaultValue) {
        Element child = XML.getChild(element, name);
        return (child != null) ? child.getTextContent() : defaultValue;
    }

    private int getIntValue(Element element, String name, int defaultValue) {
        Element child = XML.getChild(element, name);
        return (child != null) ? Integer.parseInt(child.getTextContent()) : defaultValue;
    }

    private void addInferenceMemory(Element spec, ApplicationContainerCluster cluster) {
        var inferenceElement = XML.getChild(spec, "inference");
        if (inferenceElement == null) return;

        var memoryElement = XML.getChild(inferenceElement, "memory");

        if (memoryElement != null) {
            var inferenceMemoryString = memoryElement.getTextContent().trim();
            long inferenceMemoryBytes;

            try {
                inferenceMemoryBytes = (long) BinaryUnit.valueOf(inferenceMemoryString);
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException(
                        "Invalid inference memory value, got: " + inferenceMemoryString, exception);
            }

            // Validate that inference memory does not exceed node memory
            if (!cluster.getContainers().isEmpty()) {
                var nodeMemoryGiB = cluster.getContainers().stream()
                        .mapToDouble(container -> container.getHostResource().realResources().memoryGiB())
                        .min()
                        .orElse(Double.MAX_VALUE);

                if (nodeMemoryGiB > 0) {
                    long containerMemoryBytes = (long) (nodeMemoryGiB * 1024 * 1024 * 1024);

                    if (inferenceMemoryBytes > containerMemoryBytes) {
                        throw new IllegalArgumentException(
                                Text.format(
                                        "Inference memory cannot exceed available node memory (%.2f GiB), got: %s",
                                        nodeMemoryGiB, inferenceMemoryString
                                ));
                    }
                }
            }

            cluster.setInferenceMemory(inferenceMemoryBytes);
        }
    }

    protected void addModelEvaluationRuntime(DeployState deployState, ApplicationContainerCluster cluster) {
        /* These bundles are added to all application container clusters, even if they haven't
         * declared 'model-evaluation' in services.xml, because there are many public API packages
         * in the model-evaluation bundle that could be used by customer code. */
        cluster.addPlatformBundle(ContainerModelEvaluation.MODEL_EVALUATION_BUNDLE_FILE);
        cluster.addPlatformBundle(ContainerModelEvaluation.MODEL_INTEGRATION_BUNDLE_FILE);
        cluster.addPlatformBundle(ContainerModelEvaluation.ONNXRUNTIME_BUNDLE_FILE);
        /* The ONNX runtime is always available for injection to any component */
        if (shouldUseTriton(cluster, deployState)) {
            cluster.addSimpleComponent(
                    ContainerModelEvaluation.TRITON_ONNX_RUNTIME_CLASS, null, ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME);
        } else {
            cluster.addSimpleComponent(
                    ContainerModelEvaluation.EMBEDDED_ONNX_RUNTIME_CLASS, null, ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME);
        }
        /* Add runtime providing utilities such as metrics to embedder implementations */
        cluster.addSimpleComponent(
                "ai.vespa.embedding.EmbedderRuntime", null, ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME);

        cluster.addSimpleComponent(
                "ai.vespa.modelintegration.utils.ModelPathHelperImpl", null, ContainerModelEvaluation.INTEGRATION_BUNDLE_NAME);
    }

    private void addProcessing(DeployState deployState, Element spec, ApplicationContainerCluster cluster, ConfigModelContext context) {
        Element processingElement = XML.getChild(spec, "processing");
        if (processingElement == null) return;

        cluster.addSearchAndDocprocBundles();
        addIncludes(processingElement);
        cluster.setProcessingChains(new DomProcessingBuilder(null).build(deployState, cluster, processingElement),
                                    serverBindings(deployState, context, cluster, processingElement, ProcessingChains.defaultBindings).toArray(BindingPattern[]::new));
        validateAndAddConfiguredComponents(deployState, cluster, processingElement, "renderer", ContainerModelBuilder::validateRendererElement);
    }

    private ContainerSearch buildSearch(DeployState deployState, ApplicationContainerCluster containerCluster, Element producerSpec) {
        SearchChains searchChains = new DomSearchChainsBuilder()
                                            .build(deployState, containerCluster, producerSpec);

        ContainerSearch containerSearch = new ContainerSearch(deployState, containerCluster, searchChains);

        applyApplicationPackageDirectoryConfigs(deployState.getApplicationPackage(), containerSearch);
        containerSearch.setQueryProfiles(deployState.getQueryProfiles());
        containerSearch.setSemanticRules(deployState.getSemanticRules());

        return containerSearch;
    }

    private void applyApplicationPackageDirectoryConfigs(ApplicationPackage applicationPackage,ContainerSearch containerSearch) {
        PageTemplates.validate(applicationPackage);
        containerSearch.setPageTemplates(PageTemplates.create(applicationPackage));
    }

    private void addUserHandlers(DeployState deployState, ApplicationContainerCluster cluster, Element spec, ConfigModelContext context) {
        for (Element component: XML.getChildren(spec, "handler")) {
            cluster.addComponent(
                    new DomHandlerBuilder(cluster, portBindingOverride(deployState, context, cluster)).build(deployState, cluster, component));
        }
    }

    private void checkVersion(Element spec) {
        String version = spec.getAttribute("version");

        if ( ! Version.fromString(version).equals(new Version(1)))
            throw new IllegalArgumentException("Expected container version to be 1.0, but got " + version);
    }

    private void addNodes(ApplicationContainerCluster cluster, Element spec, ConfigModelContext context) {
        if (standaloneBuilder)
            addStandaloneNode(cluster, context.getDeployState());
        else
            addNodesFromXml(cluster, spec, context);
    }

    private void addStandaloneNode(ApplicationContainerCluster cluster, DeployState deployState) {
        ApplicationContainer container = new ApplicationContainer(cluster, "standalone", cluster.getContainers().size(), deployState);
        cluster.addContainers(List.of(container));
    }

    private static String buildJvmGCOptions(ConfigModelContext context, String jvmGCOptions) {
        return new JvmGcOptions(context.getDeployState(), jvmGCOptions).build();
    }

    private static String getJvmOptions(Element nodesElement,
                                        DeployState deployState,
                                        boolean legacyOptions) {
        return new JvmOptions(nodesElement, deployState, legacyOptions).build();
    }

    private static String extractAttribute(Element element, String attrName) {
        return element.hasAttribute(attrName) ? element.getAttribute(attrName) : null;
    }

    private void extractJvmOptions(List<ApplicationContainer> nodes,
                                   ApplicationContainerCluster cluster,
                                   Element nodesElement,
                                   ConfigModelContext context) {
        Element jvmElement = XML.getChild(nodesElement, "jvm");
        if (jvmElement == null) {
            extractJvmFromLegacyNodesTag(nodes, cluster, nodesElement, context);
        } else {
            extractJvmTag(nodes, cluster, nodesElement, jvmElement, context);
        }
    }

    private void extractJvmFromLegacyNodesTag(List<ApplicationContainer> nodes, ApplicationContainerCluster cluster,
                                              Element nodesElement, ConfigModelContext context) {
        applyNodesTagJvmArgs(nodes, getJvmOptions(nodesElement, context.getDeployState(), true));

        if (cluster.getJvmGCOptions().isEmpty()) {
            String jvmGCOptions = extractAttribute(nodesElement, VespaDomBuilder.JVM_GC_OPTIONS);

            if (jvmGCOptions != null && !jvmGCOptions.isEmpty()) {
                DeployLogger logger = context.getDeployState().getDeployLogger();
                logger.logApplicationPackage(WARNING, "'jvm-gc-options' is deprecated and will be removed in Vespa 9." +
                        " Please merge into 'gc-options' in 'jvm' element." +
                        " See https://docs.vespa.ai/en/reference/services/container.html#jvm");
            }

            cluster.setJvmGCOptions(buildJvmGCOptions(context, jvmGCOptions));
        }

        if (applyMemoryPercentage(cluster, nodesElement.getAttribute(VespaDomBuilder.Allocated_MEMORY_ATTRIB_NAME)))
            context.getDeployState().getDeployLogger()
                   .logApplicationPackage(WARNING, "'allocated-memory' is deprecated and will be removed in Vespa 9." +
                           " Please merge into 'allocated-memory' in 'jvm' element." +
                           " See https://docs.vespa.ai/en/reference/services/container.html#jvm");
    }

    private void extractJvmTag(List<ApplicationContainer> nodes, ApplicationContainerCluster cluster,
                               Element nodesElement, Element jvmElement, ConfigModelContext context) {
        applyNodesTagJvmArgs(nodes, getJvmOptions(nodesElement, context.getDeployState(), false));
        applyMemoryPercentage(cluster, jvmElement.getAttribute(VespaDomBuilder.Allocated_MEMORY_ATTRIB_NAME));
        String jvmGCOptions = extractAttribute(jvmElement, VespaDomBuilder.GC_OPTIONS);
        cluster.setJvmGCOptions(buildJvmGCOptions(context, jvmGCOptions));
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
            cluster.setJvmGCOptions(buildJvmGCOptions(context, null));
        } else {
            List<ApplicationContainer> nodes = createNodes(cluster, containerElement, nodesElement, context);

            extractJvmOptions(nodes, cluster, nodesElement, context);
            applyDefaultPreload(nodes, nodesElement);
            var envVars = getEnvironmentVariables(XML.getChild(nodesElement, ENVIRONMENT_VARIABLES_ELEMENT)).entrySet();
            for (var container : nodes) {
                for (var entry : envVars) {
                    container.addEnvironmentVariable(entry.getKey(), entry.getValue());
                }
            }
            if (useCpuSocketAffinity(nodesElement))
                AbstractService.distributeCpuSocketAffinity(nodes);
            cluster.addContainers(nodes);
        }
    }

    private ZoneEndpoint zoneEndpoint(ConfigModelContext context, ClusterSpec.Id cluster) {
        return context
                .getApplicationPackage()
                .getDeploymentSpec()
                .zoneEndpoint(context.properties().applicationId().instance(),
                              context.getDeployState().zone(),
                              cluster,
                              context.featureFlags().useNonPublicEndpointForTest());
    }

    private static Map<String, String> getEnvironmentVariables(Element environmentVariables) {
        var map = new LinkedHashMap<String, String>();
        if (environmentVariables != null) {
            for (Element var: XML.getChildren(environmentVariables)) {
                var name = new com.yahoo.text.Identifier(var.getNodeName());
                map.put(name.toString(), var.getTextContent());
            }
        }
        return map;
    }

    private List<ApplicationContainer> createNodes(ApplicationContainerCluster cluster, Element containerElement,
                                                   Element nodesElement, ConfigModelContext context) {
        if (nodesElement.hasAttribute("type")) // internal use for hosted system infrastructure nodes
            return createNodesFromNodeType(cluster, nodesElement, context);
        else if (nodesElement.hasAttribute("count")) // regular, hosted node spec
            return createNodesFromNodeCount(cluster, containerElement, nodesElement, context);
        else if (cluster.isHostedVespa()) // default to 1 if node count is not specified
            return createNodesFromNodeCount(cluster, containerElement, nodesElement, context);
        else // the non-hosted option
            return createNodesFromNodeList(context.getDeployState(), cluster, nodesElement);
    }

    private static boolean applyMemoryPercentage(ApplicationContainerCluster cluster, String memoryPercentage) {
        try {
            if (memoryPercentage == null || memoryPercentage.isEmpty()) return false;
            memoryPercentage = memoryPercentage.trim();
            if ( ! memoryPercentage.endsWith("%"))
                throw new IllegalArgumentException("Missing % sign");
            memoryPercentage = memoryPercentage.substring(0, memoryPercentage.length()-1).trim();
            cluster.setMemoryPercentage(Integer.parseInt(memoryPercentage));
            return true;
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("The memory percentage given for nodes in " + cluster +
                                               " must be given as an integer followe by '%'", e);
        }
    }

    /** Allocate a container cluster without a nodes tag */
    private List<ApplicationContainer> allocateWithoutNodesTag(ApplicationContainerCluster cluster, ConfigModelContext context) {
        DeployState deployState = context.getDeployState();
        HostSystem hostSystem = cluster.hostSystem();
        if (deployState.isHosted()) {
            // request just enough nodes to satisfy environment capacity requirement
            int nodeCount = deployState.zone().environment().isProduction() ? 2 : 1;
            deployState.getDeployLogger().logApplicationPackage(Level.INFO, "Using " + nodeCount + " nodes in " + cluster);
            var nodesSpec = NodesSpecification.dedicated(nodeCount, context);
            ClusterSpec.Id clusterId = ClusterSpec.Id.from(cluster.getName());
            var sidecars = getSidecars(cluster, deployState, nodesSpec);
            var hosts = nodesSpec.provision(hostSystem,
                                            ClusterSpec.Type.container,
                                            clusterId,
                                            zoneEndpoint(context, clusterId),
                                            deployState.getDeployLogger(),
                                            false,
                                            context.clusterInfo().build(),
                                            sidecars);
            return createNodesFromHosts(hosts, cluster, context.getDeployState());
        }
        else {
            return singleHostContainerCluster(cluster, hostSystem.getHost(Container.SINGLENODE_CONTAINER_SERVICESPEC), context);
        }
    }

    private List<ApplicationContainer> singleHostContainerCluster(ApplicationContainerCluster cluster, HostResource host, ConfigModelContext context) {
        ApplicationContainer node = new ApplicationContainer(cluster, "container.0", 0, context.getDeployState());
        node.setHostResource(host);
        node.initService(context.getDeployState());
        return List.of(node);
    }

    private static void requireFixedSizeSingularNodeIfTester(ConfigModelContext context, NodesSpecification nodes) {
        if ( ! context.properties().hostedVespa() || ! context.properties().applicationId().instance().isTester())
            return;

        if ( ! nodes.maxResources().equals(nodes.minResources()))
            throw new IllegalArgumentException("tester resources must be absolute, but min and max resources differ: " + nodes);

        if (nodes.maxResources().nodes() > 1)
            throw new IllegalArgumentException("tester cannot run on more than 1 node, but " + nodes.maxResources().nodes() + " nodes were specified");
    }

    private List<ApplicationContainer> createNodesFromNodeCount(ApplicationContainerCluster cluster, Element containerElement, Element nodesElement, ConfigModelContext context) {
        try {
            var nodesSpecification = NodesSpecification.from(new ModelElement(nodesElement), context);
            requireFixedSizeSingularNodeIfTester(context, nodesSpecification);
            var clusterId = ClusterSpec.Id.from(cluster.name());
            var sidecars = getSidecars(cluster, context.getDeployState(), nodesSpecification);
            Map<HostResource, ClusterMembership> hosts = nodesSpecification.provision(cluster.getRoot().hostSystem(),
                                                                                      ClusterSpec.Type.container,
                                                                                      clusterId,
                                                                                      zoneEndpoint(context, clusterId),
                                                                                      deployLogger,
                                                                                      getZooKeeper(containerElement) != null,
                                                                                      context.clusterInfo().build(),
                                                                                      sidecars);
            return createNodesFromHosts(hosts, cluster, context.getDeployState());
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("In " + cluster, e);
        }
    }

    private List<ApplicationContainer> createNodesFromNodeType(ApplicationContainerCluster cluster, Element nodesElement, ConfigModelContext context) {
        NodeType type = NodeType.valueOf(nodesElement.getAttribute("type"));
        ClusterSpec clusterSpec = ClusterSpec.request(ClusterSpec.Type.container, ClusterSpec.Id.from(cluster.getName()))
                .vespaVersion(context.getDeployState().getWantedNodeVespaVersion())
                .dockerImageRepository(context.getDeployState().getWantedDockerImageRepo())
                .build();
        Map<HostResource, ClusterMembership> hosts =
                cluster.getRoot().hostSystem().allocateHosts(clusterSpec, Capacity.fromRequiredNodeType(type), deployLogger);
        return createNodesFromHosts(hosts, cluster, context.getDeployState());
    }

    private List<ApplicationContainer> createNodesFromHosts(Map<HostResource, ClusterMembership> hosts,
                                                            ApplicationContainerCluster cluster,
                                                            DeployState deployState) {
        List<ApplicationContainer> nodes = new ArrayList<>();
        for (Map.Entry<HostResource, ClusterMembership> entry : hosts.entrySet()) {
            String id = "container." + entry.getValue().index();
            ApplicationContainer container = new ApplicationContainer(cluster, id, entry.getValue().retired(), entry.getValue().index(), deployState);
            container.setHostResource(entry.getKey());
            container.initService(deployState);
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

    private void addSearchHandler(DeployState deployState, ApplicationContainerCluster cluster, Element searchElement, ConfigModelContext context) {
        var bindingPatterns = SearchHandler.defaultBindings();
        if (isHostedTenantApplication(context)) {
            bindingPatterns = SearchHandler.bindingPattern(getDataplanePorts(deployState, cluster));
        }
        SearchHandler searchHandler = new SearchHandler(deployState, cluster,
                                                        serverBindings(deployState, context, cluster, searchElement, bindingPatterns),
                                                        searchElement);
        cluster.addComponent(searchHandler);

        // Add as child to SearchHandler to get the correct chains config.
        searchHandler.addComponent(Component.fromClassAndBundle(SearchHandler.EXECUTION_FACTORY, PlatformBundles.SEARCH_AND_DOCPROC_BUNDLE));
    }

    private List<BindingPattern> serverBindings(DeployState deployState, ConfigModelContext context, ApplicationContainerCluster cluster, Element searchElement, Collection<BindingPattern> defaultBindings) {
        List<Element> bindings = XML.getChildren(searchElement, "binding");
        if (bindings.isEmpty())
            return List.copyOf(defaultBindings);

        return toBindingList(deployState, context, cluster, bindings);
    }

    private List<BindingPattern> toBindingList(DeployState deployState, ConfigModelContext context, ApplicationContainerCluster cluster, List<Element> bindingElements) {
        List<BindingPattern> result = new ArrayList<>();
        var portOverride = isHostedTenantApplication(context) ? getDataplanePorts(deployState, cluster) : Set.<Integer>of();
        for (Element element: bindingElements) {
            String text = element.getTextContent().trim();
            if (!text.isEmpty())
                result.addAll(userBindingPattern(text, portOverride));
        }

        return result;
    }
    private static Collection<UserBindingPattern> userBindingPattern(String path, Set<Integer> portBindingOverride) {
        UserBindingPattern bindingPattern = UserBindingPattern.fromPattern(path);
        if (portBindingOverride.isEmpty()) return Set.of(bindingPattern);
        return portBindingOverride.stream()
                .map(bindingPattern::withOverriddenPort)
                .toList();
    }


    private ContainerDocumentApi buildDocumentApi(DeployState deployState, ApplicationContainerCluster cluster, Element spec, ConfigModelContext context) {
        Element documentApiElement = XML.getChild(spec, "document-api");
        if (documentApiElement == null) return ContainerDocumentApi.createDummyApi(cluster);

        ContainerDocumentApi.HandlerOptions documentApiOptions = DocumentApiOptionsBuilder.build(documentApiElement);
        Element ignoreUndefinedFields = XML.getChild(documentApiElement, "ignore-undefined-fields");
        return new ContainerDocumentApi(deployState, cluster, documentApiOptions,
                                        "true".equals(XML.getValue(ignoreUndefinedFields)), portBindingOverride(deployState, context, cluster));
    }

    private Set<Integer> portBindingOverride(DeployState deployState, ConfigModelContext context, ApplicationContainerCluster cluster) {
        return isHostedTenantApplication(context)
                ? getDataplanePorts(deployState, cluster)
                : Set.of();
    }

    private ContainerDocproc buildDocproc(DeployState deployState, ApplicationContainerCluster cluster, Element spec) {
        Element docprocElement = XML.getChild(spec, "document-processing");
        if (docprocElement == null)
            return null;

        ContainerThreadpool docprocHandlerThreadpool = new ContainerDocproc.Threadpool(deployState, docprocElement);
        addIncludes(docprocElement);
        DocprocChains chains = new DomDocprocChainsBuilder(docprocHandlerThreadpool).build(deployState, cluster, docprocElement);

        ContainerDocproc.Options docprocOptions = DocprocOptionsBuilder.build(docprocElement, deployState.getDeployLogger());
        return new ContainerDocproc(cluster, chains, docprocOptions, deployState, !standaloneBuilder);
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
                                                Element parent, String componentName) {
        for (Element component : XML.getChildren(parent, componentName)) {
            ModelIdResolver.resolveModelIds(component, deployState.isHosted());
            cluster.addComponent(new DomComponentBuilder().build(deployState, cluster, component));
        }
    }

    private static void validateAndAddConfiguredComponents(DeployState deployState,
                                                           ContainerCluster<? extends Container> cluster,
                                                           Element spec,
                                                           String componentName,
                                                           Consumer<Element> elementValidator) {
        for (Element node : XML.getChildren(spec, componentName)) {
            elementValidator.accept(node); // throws exception here if something is wrong
            cluster.addComponent(new DomComponentBuilder().build(deployState, cluster, node));
        }
    }

    private void addIdentityProvider(ApplicationContainerCluster cluster, List<ConfigServerSpec> configServerSpecs, HostName loadBalancerName,
                                     URI ztsUrl, String athenzDnsSuffix, Zone zone, DeploymentSpec spec) {
        spec.athenzDomain().ifPresent(domain -> {
            AthenzService service = spec.athenzService(app.getApplicationId().instance(), zone.environment(), zone.region())
                                        .orElseThrow(() -> new IllegalArgumentException("Missing Athenz service configuration in instance '" +
                                                                                        app.getApplicationId().instance() + "'"));
            addIdentityProvider(cluster, configServerSpecs, loadBalancerName, ztsUrl, athenzDnsSuffix, zone, domain, service);
        });
    }

    private void addIdentityProvider(ApplicationContainerCluster cluster, List<ConfigServerSpec> configServerSpecs, HostName loadBalancerName,
                                     URI ztsUrl, String athenzDnsSuffix, Zone zone, AthenzDomain domain, AthenzService service) {
        String zoneDnsSuffix = zone.environment().value() + "-" + zone.region().value() + "." + athenzDnsSuffix;
        IdentityProvider identityProvider = new IdentityProvider(domain,
                                                                 service,
                                                                 getLoadBalancerName(loadBalancerName, configServerSpecs),
                                                                 ztsUrl,
                                                                 zoneDnsSuffix,
                                                                 zone);

        // Replace AthenzIdentityProviderProvider
        cluster.removeComponent(ComponentId.fromString("com.yahoo.container.jdisc.AthenzIdentityProviderProvider"));
        cluster.addComponent(identityProvider);

        var serviceIdentityProviderProvider = "com.yahoo.vespa.athenz.identityprovider.client.ServiceIdentityProviderProvider";
        cluster.addComponent(new SimpleComponent(new ComponentModel(serviceIdentityProviderProvider, serviceIdentityProviderProvider, "vespa-athenz")));

        cluster.getContainers().forEach(container -> {
            container.setProp("identity.domain", domain.value());
            container.setProp("identity.service", service.value());
        });
    }

    private HostName getLoadBalancerName(HostName loadbalancerName, List<ConfigServerSpec> configServerSpecs) {
        // Set lbaddress, or use first hostname if not specified.
        // TODO: Remove this method and use the loadbalancerName directly
        return Optional.ofNullable(loadbalancerName)
                .orElseGet(
                        () -> HostName.of(configServerSpecs.stream()
                                                           .findFirst()
                                                           .map(ConfigServerSpec::getHostName)
                                                           .orElse("unknown") // Currently unable to test this, hence the unknown
                        ));
    }

    private static Element getZooKeeper(Element spec) {
        return XML.getChild(spec, "zookeeper");
    }

    /** Disallow renderers named "XmlRenderer" or "JsonRenderer" */
    private static void validateRendererElement(Element element) {
        String idAttr = element.getAttribute("id");

        if (idAttr.equals(xmlRendererId) || idAttr.equals(jsonRendererId)) {
            throw new IllegalArgumentException(Text.format("Renderer id %s is reserved for internal use", idAttr));
        }
    }

    /**
     * Validates JVM options and logs a warning or fails deployment (depending on feature flag)
     * if anyone of them has invalid syntax or is an option that is unsupported for the running system.
     */
     private static class JvmOptions {

        private static final Pattern validPattern = Pattern.compile("-[a-zA-z0-9=:./,+*-]+");
        // debug port will not be available in hosted, don't allow
        private static final Pattern invalidInHostedPattern = Pattern.compile("-Xrunjdwp:transport=.*");

        private final Element nodesElement;
        private final DeployLogger logger;
        private final boolean legacyOptions;
        private final boolean isHosted;

        public JvmOptions(Element nodesElement, DeployState deployState, boolean legacyOptions) {
            this.nodesElement = nodesElement;
            this.logger = deployState.getDeployLogger();
            this.legacyOptions = legacyOptions;
            this.isHosted = deployState.isHosted();
        }

        String build() {
            if (legacyOptions)
                return buildLegacyOptions();

            Element jvmElement = XML.getChild(nodesElement, "jvm");
            if (jvmElement == null) return "";
            String jvmOptions = jvmElement.getAttribute(VespaDomBuilder.OPTIONS);
            if (jvmOptions.isEmpty()) return "";
            validateJvmOptions(jvmOptions);
            return jvmOptions;
        }

        String buildLegacyOptions() {
            String jvmOptions = null;
            if (nodesElement.hasAttribute(VespaDomBuilder.JVM_OPTIONS)) {
                jvmOptions = nodesElement.getAttribute(VespaDomBuilder.JVM_OPTIONS);
                if (! jvmOptions.isEmpty())
                    logger.logApplicationPackage(WARNING, "'jvm-options' is deprecated and will be removed in Vespa 9." +
                            " Please merge 'jvm-options' into 'options' or 'gc-options' in 'jvm' element." +
                            " See https://docs.vespa.ai/en/reference/services/container.html#jvm");
            }

            validateJvmOptions(jvmOptions);

            return jvmOptions;
        }

        private void validateJvmOptions(String jvmOptions) {
            if (jvmOptions == null || jvmOptions.isEmpty()) return;

            String[] optionList = jvmOptions.split(" ");
            List<String> invalidOptions = Arrays.stream(optionList)
                                                .filter(option -> !option.isEmpty())
                                                .filter(option -> !Pattern.matches(validPattern.pattern(), option))
                                                .sorted()
                                                .collect(Collectors.toCollection(ArrayList::new));
            if (isHosted)
                invalidOptions.addAll(Arrays.stream(optionList)
                        .filter(option -> !option.isEmpty())
                        .filter(option -> Pattern.matches(invalidInHostedPattern.pattern(), option))
                        .sorted().toList());

            if (invalidOptions.isEmpty()) return;

            String message = "Invalid or misplaced JVM options in services.xml: " +
                    String.join(",", invalidOptions) + "." +
                    " See https://docs.vespa.ai/en/reference/services/container.html#jvm";
            if (isHosted)
                throw new IllegalArgumentException(message);
            else
                logger.logApplicationPackage(WARNING, message);
        }
    }

    /**
     * Validates JVM GC options and logs a warning or fails deployment (depending on feature flag)
     * if anyone of them has invalid syntax or is an option that is unsupported for the running system
     * (e.g. uses CMS options for hosted Vespa, which uses JDK 17).
     */
    private static class JvmGcOptions {

        private static final Pattern validPattern = Pattern.compile("-XX:[+-]*[a-zA-z0-9=]+");
        private static final Pattern invalidCMSPattern = Pattern.compile("-XX:[+-]\\w*CMS[a-zA-z0-9=]+");

        private final DeployState deployState;
        private final String jvmGcOptions;
        private final DeployLogger logger;
        private final boolean isHosted;

        public JvmGcOptions(DeployState deployState, String jvmGcOptions) {
            this.deployState = deployState;
            this.jvmGcOptions = jvmGcOptions;
            this.logger = deployState.getDeployLogger();
            this.isHosted = deployState.isHosted();
        }

        private String build() {
            String options = deployState.getProperties().jvmGCOptions();
            if (jvmGcOptions != null) {
                options = jvmGcOptions;
                String[] optionList = options.split(" ");
                List<String> invalidOptions = Arrays.stream(optionList)
                        .filter(option -> !option.isEmpty())
                        .filter(option -> !Pattern.matches(validPattern.pattern(), option)
                                || Pattern.matches(invalidCMSPattern.pattern(), option)
                                || option.equals("-XX:+UseConcMarkSweepGC"))
                        .sorted()
                        .toList();

                logOrFailInvalidOptions(invalidOptions);
            }

            if (options == null || options.isEmpty())
                options = deployState.isHosted() ? ContainerCluster.PARALLEL_GC : ContainerCluster.G1GC;

            return options;
        }

        private void logOrFailInvalidOptions(List<String> options) {
            if (options.isEmpty()) return;

            String message = "Invalid or misplaced JVM GC options in services.xml: " +
                    String.join(",", options) + "." +
                    " See https://docs.vespa.ai/en/reference/services/container.html#jvm";
            if (isHosted)
                throw new IllegalArgumentException(message);
            else
                logger.logApplicationPackage(WARNING, message);
        }

    }

    private static Set<Integer> getDataplanePorts(DeployState ds, ApplicationContainerCluster cluster) {
        var tokenPort = getTokenDataplanePort(ds, cluster);
        var mtlsPort = getMtlsDataplanePort(ds, cluster);
        return tokenPort.isPresent() ? Set.of(mtlsPort, tokenPort.getAsInt()) : Set.of(mtlsPort);
    }

    private static int getMtlsDataplanePort(DeployState ds, ApplicationContainerCluster cluster) {
        return enableTokenSupport(ds, cluster) ? 8443 : 4443;
    }

    private static OptionalInt getTokenDataplanePort(DeployState ds, ApplicationContainerCluster cluster) {
        return enableTokenSupport(ds, cluster) ? OptionalInt.of(8444) : OptionalInt.empty();
    }

    private static Set<ContainerEndpoint> tokenEndpoints(DeployState deployState, ApplicationContainerCluster cluster) {
        return deployState.getEndpoints().stream()
                .filter(endpoint ->
                        endpoint.authMethod() == ApplicationClusterEndpoint.AuthMethod.token &&
                        endpoint.clusterId().equals(cluster.getName()))
                .collect(Collectors.toSet());
    }

    private static boolean enableTokenSupport(DeployState state, ApplicationContainerCluster cluster) {
        Set<ContainerEndpoint> tokenEndpoints = tokenEndpoints(state, cluster);
        return state.isHosted() && state.zone().system().isPublicCloudLike() && ! tokenEndpoints.isEmpty();
    }
}
