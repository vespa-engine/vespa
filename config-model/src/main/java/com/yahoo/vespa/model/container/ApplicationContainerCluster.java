// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.container;

import ai.vespa.metricsproxy.http.application.ApplicationMetricsHandler;
import com.yahoo.cloud.config.CuratorConfig;
import com.yahoo.cloud.config.ZookeeperServerConfig;
import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.config.FileReference;
import com.yahoo.config.application.api.ComponentInfo;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.api.ApplicationClusterEndpoint;
import com.yahoo.config.model.api.ApplicationClusterInfo;
import com.yahoo.config.model.api.Model;
import com.yahoo.config.model.api.OnnxModelCost;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.TenantName;
import com.yahoo.container.bundle.BundleInstantiationSpecification;
import com.yahoo.container.di.config.ApplicationBundlesConfig;
import com.yahoo.container.handler.metrics.MetricsProxyApiConfig;
import com.yahoo.container.handler.metrics.MetricsV2Handler;
import com.yahoo.container.handler.metrics.PrometheusV1Handler;
import com.yahoo.container.jdisc.ContainerMbusConfig;
import com.yahoo.container.jdisc.messagebus.MbusServerProvider;
import com.yahoo.osgi.provider.model.ComponentModel;
import com.yahoo.search.config.QrStartConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.Host;
import com.yahoo.vespa.model.admin.metricsproxy.MetricsProxyContainer;
import com.yahoo.vespa.model.container.component.BindingPattern;
import com.yahoo.vespa.model.container.component.Component;
import com.yahoo.vespa.model.container.component.Handler;
import com.yahoo.vespa.model.container.component.SystemBindingPattern;
import com.yahoo.vespa.model.container.configserver.ConfigserverCluster;
import com.yahoo.vespa.model.container.xml.CloudSecrets;
import com.yahoo.vespa.model.filedistribution.UserConfiguredFiles;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.yahoo.vespa.model.container.docproc.DocprocChains.DOCUMENT_TYPE_MANAGER_CLASS;
import static java.util.logging.Level.FINE;

/**
 * A container cluster that is typically set up from the user application.
 *
 * @author gjoranv
 */
public final class ApplicationContainerCluster extends ContainerCluster<ApplicationContainer> implements
        ApplicationBundlesConfig.Producer,
        QrStartConfig.Producer,
        RankProfilesConfig.Producer,
        RankingConstantsConfig.Producer,
        OnnxModelsConfig.Producer,
        RankingExpressionsConfig.Producer,
        ContainerMbusConfig.Producer,
        MetricsProxyApiConfig.Producer,
        ZookeeperServerConfig.Producer,
        ApplicationClusterInfo {

    public static final String METRICS_V2_HANDLER_CLASS = MetricsV2Handler.class.getName();
    public static final BindingPattern METRICS_V2_HANDLER_BINDING_1 = SystemBindingPattern.fromHttpPath(MetricsV2Handler.V2_PATH);
    public static final BindingPattern METRICS_V2_HANDLER_BINDING_2 = SystemBindingPattern.fromHttpPath(MetricsV2Handler.V2_PATH + "/*");

    public static final String PROMETHEUS_V1_HANDLER_CLASS = PrometheusV1Handler.class.getName();
    private static final BindingPattern PROMETHEUS_V1_HANDLER_BINDING_1 = SystemBindingPattern.fromHttpPath(PrometheusV1Handler.V1_PATH);
    private static final BindingPattern PROMETHEUS_V1_HANDLER_BINDING_2 = SystemBindingPattern.fromHttpPath(PrometheusV1Handler.V1_PATH + "/*");

    private static final TenantName HOSTED_VESPA = TenantName.from("hosted-vespa");

    public static final int defaultHeapSizePercentageOfAvailableMemory = 85;
    public static final int heapSizePercentageOfTotalAvailableMemoryWhenCombinedCluster = 24;

    private final Set<FileReference> applicationBundles = new LinkedHashSet<>();

    private final Set<String> previousHosts;
    private final OnnxModelCost onnxModelCost;
    private final OnnxModelCost.Calculator onnxModelCostCalculator;
    private final DeployLogger logger;

    private ContainerModelEvaluation modelEvaluation;

    private final Optional<String> tlsClientAuthority;

    private MbusParams mbusParams;
    private boolean messageBusEnabled = true;
    private int zookeeperSessionTimeoutSeconds = 30;
    private final int transport_events_before_wakeup;
    private final int transport_connections_per_target;

    /** The heap size % of total memory available to the JVM process. */
    private final int heapSizePercentageOfAvailableMemory;

    private Integer memoryPercentage = null;

    private List<ApplicationClusterEndpoint> endpoints = List.of();

    private final UserConfiguredUrls userConfiguredUrls = new UserConfiguredUrls();

    private Optional<CloudSecrets> tenantSecrets = Optional.empty();


    public ApplicationContainerCluster(TreeConfigProducer<?> parent, String configSubId, String clusterId, DeployState deployState) {
        super(parent, configSubId, clusterId, deployState, true, 10);
        this.tlsClientAuthority = deployState.tlsClientAuthority();
        previousHosts = Collections.unmodifiableSet(deployState.getPreviousModel().stream()
                                                               .map(Model::allocatedHosts)
                                                               .map(AllocatedHosts::getHosts)
                                                               .flatMap(Collection::stream)
                                                               .map(HostSpec::hostname)
                                                               .collect(Collectors.toCollection(() -> new LinkedHashSet<>())));

        addSimpleComponent("com.yahoo.language.provider.DefaultLinguisticsProvider");
        addSimpleComponent("com.yahoo.language.provider.DefaultEmbedderProvider");
        addSimpleComponent("com.yahoo.language.provider.DefaultGeneratorProvider");
        addSimpleComponent("com.yahoo.container.jdisc.SecretStoreProvider");
        addSimpleComponent("com.yahoo.container.jdisc.CertificateStoreProvider");
        addSimpleComponent("com.yahoo.container.jdisc.AthenzIdentityProviderProvider");
        addSimpleComponent("com.yahoo.container.core.documentapi.DocumentAccessProvider");
        addSimpleComponent("com.yahoo.container.jdisc.SecretsProvider");
        addSimpleComponent(DOCUMENT_TYPE_MANAGER_CLASS);

        addMetricsHandlers();
        addTestrunnerComponentsIfTester(deployState);
        transport_connections_per_target = deployState.featureFlags().mbusJavaRpcNumTargets();
        transport_events_before_wakeup = deployState.featureFlags().mbusJavaEventsBeforeWakeup();
        heapSizePercentageOfAvailableMemory = deployState.featureFlags().heapSizePercentage() > 0
                ? Math.min(99, deployState.featureFlags().heapSizePercentage())
                : defaultHeapSizePercentageOfAvailableMemory;
        onnxModelCost = deployState.onnxModelCost();
        onnxModelCostCalculator = deployState.onnxModelCost().newCalculator(
                deployState.getApplicationPackage(), deployState.getProperties().applicationId(), ClusterSpec.Id.from(clusterId));
        logger = deployState.getDeployLogger();
    }

    public UserConfiguredUrls userConfiguredUrls() { return userConfiguredUrls; }

    @Override
    protected void doPrepare(DeployState deployState) {
        super.doPrepare(deployState);
        // Register bundles and files for file distribution
        registerApplicationBundles(deployState);
        registerUserConfiguredFiles(deployState);
        createEndpoints(deployState);
        if (onnxModelCostCalculator.restartOnDeploy())
            setDeferChangesUntilRestart(true);
    }

    private void registerApplicationBundles(DeployState deployState) {
        for (ComponentInfo component : deployState.getApplicationPackage().getComponentsInfo(deployState.getVespaVersion())) {
            FileReference reference = deployState.getFileRegistry().addFile(component.getPathRelativeToAppDir());
            applicationBundles.add(reference);
        }
    }

    private void registerUserConfiguredFiles(DeployState deployState) {
        if (containers.isEmpty()) return;

        // Files referenced from user configs to all components.
        UserConfiguredFiles files = new UserConfiguredFiles(deployState.getFileRegistry(),
                                                            deployState.getDeployLogger(),
                                                            deployState.featureFlags(),
                                                            userConfiguredUrls,
                                                            deployState.getApplicationPackage());
        for (Component<?, ?> component : getAllComponents()) {
            files.register(component);
        }
    }

    private void addMetricsHandlers() {
        addMetricsHandler(METRICS_V2_HANDLER_CLASS, METRICS_V2_HANDLER_BINDING_1, METRICS_V2_HANDLER_BINDING_2);
        addMetricsHandler(PROMETHEUS_V1_HANDLER_CLASS, PROMETHEUS_V1_HANDLER_BINDING_1, PROMETHEUS_V1_HANDLER_BINDING_2);
   }

    private void addMetricsHandler(String handlerClass, BindingPattern rootBinding, BindingPattern innerBinding) {
        Handler handler = new Handler(new ComponentModel(handlerClass, null, null, null));
        handler.addServerBindings(rootBinding, innerBinding);
        addComponent(handler);
    }

    private void addTestrunnerComponentsIfTester(DeployState deployState) {
        if (deployState.isHosted() && deployState.getProperties().applicationId().instance().isTester()) {
            addPlatformBundle(PlatformBundles.absoluteBundlePath("vespa-testrunner-components"));
            addPlatformBundle(PlatformBundles.absoluteBundlePath("vespa-osgi-testrunner"));
            addPlatformBundle(PlatformBundles.absoluteBundlePath("tenant-cd-api"));
            if(deployState.zone().system().isPublic()) {
                addPlatformBundle(PlatformBundles.absoluteBundlePath("cloud-tenant-cd"));
            }
        }
    }

    public void setModelEvaluation(ContainerModelEvaluation modelEvaluation) {
        this.modelEvaluation = modelEvaluation;
    }

    public void setMemoryPercentage(Integer memoryPercentage) { this.memoryPercentage = memoryPercentage; }

    @Override
    public Optional<JvmMemoryPercentage> getMemoryPercentage() {
        if (memoryPercentage != null) return Optional.of(JvmMemoryPercentage.of(memoryPercentage));

        if (isHostedVespa()) {
            int heapSizePercentageOfAvailable = heapSizePercentageOfAvailable();
            if (getContainers().isEmpty()) return Optional.of(JvmMemoryPercentage.of(heapSizePercentageOfAvailable)); // Node memory is not known

            // Node memory is known, so compute heap size as a percentage of available memory (excluding overhead, which the startup scripts also account for)
            double totalMemoryGb = getContainers().stream().mapToDouble(c -> c.getHostResource().realResources().memoryGiB()).min().orElseThrow();
            double totalMemoryMinusOverhead = Math.max(0, totalMemoryGb - Host.memoryOverheadGb);
            double onnxModelCostGb = onnxModelCostCalculator.aggregatedModelCostInBytes() / (1024D * 1024 * 1024);
            double availableMemoryGb = Math.max(0, totalMemoryMinusOverhead - onnxModelCostGb);
            int memoryPercentageOfAvailable = (int) (heapSizePercentageOfAvailable * availableMemoryGb / totalMemoryMinusOverhead);
            int memoryPercentageOfTotal = (int) (heapSizePercentageOfAvailable * availableMemoryGb / totalMemoryGb);
            logger.log(FINE, () -> ("cluster id '%s': memoryPercentageOfAvailable=%d, memoryPercentageOfTotal=%d, " +
                                    "availableMemoryGb=%f, totalMemoryGb=%f, heapSizePercentageOfAvailable=%d, onnxModelCostGb=%f")
                    .formatted(id(), memoryPercentageOfAvailable, memoryPercentageOfTotal,
                               availableMemoryGb, totalMemoryGb, heapSizePercentageOfAvailable, onnxModelCostGb));
            return Optional.of(JvmMemoryPercentage.of(memoryPercentageOfAvailable, memoryPercentageOfTotal,
                                                      availableMemoryGb * heapSizePercentageOfAvailable * 1e-2));
        }
        return Optional.empty();
    }

    public int heapSizePercentageOfAvailable() {
        return getHostClusterId().isPresent() ?
                heapSizePercentageOfTotalAvailableMemoryWhenCombinedCluster :
                heapSizePercentageOfAvailableMemory;
    }

    /** Create list of endpoints, these will be consumed later by LbServicesProducer */
    private void createEndpoints(DeployState deployState) {
        if (!configureEndpoints(deployState)) return;
        // Add endpoints provided by the controller
        List<String> hosts = getContainers().stream().map(AbstractService::getHostName).sorted().toList();
        List<ApplicationClusterEndpoint> endpoints = new ArrayList<>();
        deployState.getEndpoints().stream()
                   .filter(ce -> ce.clusterId().equals(getName()))
                   .forEach(ce -> ce.names().forEach(
                           name -> endpoints.add(ApplicationClusterEndpoint.builder()
                                                                           .scope(ce.scope())
                                                                           .weight(ce.weight().orElse(1))
                                                                           .routingMethod(ce.routingMethod())
                                                                           .dnsName(ApplicationClusterEndpoint.DnsName.from(name))
                                                                           .hosts(hosts)
                                                                           .clusterId(getName())
                                                                           .authMethod(ce.authMethod())
                                                                           .build())
                   ));
        if (endpoints.stream().noneMatch(endpoint -> endpoint.scope() == ApplicationClusterEndpoint.Scope.zone)) {
            throw new IllegalArgumentException("Expected at least one " + ApplicationClusterEndpoint.Scope.zone +
                                               " endpoint for cluster '" + name() + "' in application '" +
                                               deployState.getProperties().applicationId() +
                                               "', got " + deployState.getEndpoints());
        }
        this.endpoints = Collections.unmodifiableList(endpoints);
    }

    @Override
    public void getConfig(ApplicationBundlesConfig.Builder builder) {
        applicationBundles.stream().map(FileReference::value)
                .forEach(builder::bundles);
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        if (modelEvaluation != null) modelEvaluation.getConfig(builder);
    }

    @Override
    public void getConfig(RankingConstantsConfig.Builder builder) {
        if (modelEvaluation != null) modelEvaluation.getConfig(builder);
    }

    @Override
    public void getConfig(OnnxModelsConfig.Builder builder) {
        if (modelEvaluation != null) modelEvaluation.getConfig(builder);
    }

    public void getConfig(RankingExpressionsConfig.Builder builder) {
        if (modelEvaluation != null) modelEvaluation.getConfig(builder);
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
        if (getDocproc() != null)
            getDocproc().getConfig(builder);
        builder.transport_events_before_wakeup(transport_events_before_wakeup);
        builder.numconnectionspertarget(transport_connections_per_target);
    }

    @Override
    public void getConfig(MetricsProxyApiConfig.Builder builder) {
        builder.metricsPort(MetricsProxyContainer.BASEPORT)
                .metricsApiPath(ApplicationMetricsHandler.METRICS_VALUES_PATH)
                .prometheusApiPath(ApplicationMetricsHandler.PROMETHEUS_VALUES_PATH);
    }

    @Override
    public void getConfig(QrStartConfig.Builder builder) {
        super.getConfig(builder);
        var memoryPct = getMemoryPercentage().orElse(null);
        int heapsize = truncateTo4SignificantBits(memoryPct != null && memoryPct.asAbsoluteGb().isPresent()
                                                  ? (int) (memoryPct.asAbsoluteGb().getAsDouble() * 1024) : 1536);
        builder.jvm.verbosegc(true)
                .availableProcessors(0)
                .compressedClassSpaceSize(0)
                .minHeapsize(heapsize) // These cause restarts when changed, so we try to keep them stable.
                .heapsize(heapsize);
        if (memoryPct != null) builder.jvm.heapSizeAsPercentageOfPhysicalMemory(memoryPct.ofContainerAvailable());
    }

    static int truncateTo4SignificantBits(int i) {
        if (i == Integer.MIN_VALUE) return i;
        if (i < 0) return -truncateTo4SignificantBits(-i);
        if (i <= 16) return i;
        int mask = Integer.highestOneBit(i);
        mask += mask - (mask >> 3);
        return i & mask;
    }

    @Override
    public void getConfig(ZookeeperServerConfig.Builder builder) {
        if (getParent() instanceof ConfigserverCluster) return; // Produces its own config

        // Note: Default client and server ports are used, so not set here
        for (Container container : getContainers()) {
            ZookeeperServerConfig.Server.Builder serverBuilder = new ZookeeperServerConfig.Server.Builder();
            serverBuilder.hostname(container.getHostName())
                         .id(container.index())
                         .joining( ! previousHosts.isEmpty() &&
                                   ! previousHosts.contains(container.getHostName()))
                         .retired(container.isRetired());
            builder.server(serverBuilder);
        }
        builder.dynamicReconfiguration(true);
    }

    @Override
    public void getConfig(CuratorConfig.Builder builder) {
        super.getConfig(builder);
        if (getParent() instanceof ConfigserverCluster) return; // Produces its own config

        // Will be bounded by 2x and 20x ZookeeperServerConfig.tickTime(), which is currently 6s.
        builder.zookeeperSessionTimeoutSeconds(zookeeperSessionTimeoutSeconds);
    }

    public Optional<String> getTlsClientAuthority() {
        return tlsClientAuthority;
    }

    public void setMbusParams(MbusParams mbusParams) {
        this.mbusParams = mbusParams;
    }

    public void setMessageBusEnabled(boolean messageBusEnabled) { this.messageBusEnabled = messageBusEnabled; }

    public void setZookeeperSessionTimeoutSeconds(int timeoutSeconds) {
        this.zookeeperSessionTimeoutSeconds = timeoutSeconds;
    }

    protected boolean messageBusEnabled() { return messageBusEnabled; }

    public void addAccessLog() {
        // In hosted there is one application container per node, so we do not use the container name to distinguish log files
        Optional<String> clusterName = isHostedVespa ? Optional.empty() : Optional.of(getName());
        addAccessLog(clusterName);
    }

    public void addMbusServer(ComponentId chainId) {
        ComponentId serviceId = chainId.nestInNamespace(ComponentId.fromString("MbusServer"));

        addComponent(
                new Component<>(new ComponentModel(new BundleInstantiationSpecification(
                        serviceId,
                        ComponentSpecification.fromString(MbusServerProvider.class.getName()),
                        null))));
    }

    public void setTenantSecretsConfig(CloudSecrets secretsConfig) {
        tenantSecrets = Optional.of(secretsConfig);
        addComponent(secretsConfig);
    }

    public Optional<CloudSecrets> getTenantSecrets() {
        return tenantSecrets;
    }

    @Override
    public List<ApplicationClusterEndpoint> endpoints() {
        return endpoints;
    }

    @Override
    public String name() { return getName(); }

    public OnnxModelCost onnxModelCost() { return onnxModelCost; }

    public OnnxModelCost.Calculator onnxModelCostCalculator() { return onnxModelCostCalculator; }

    /** Returns whether the deployment in given deploy state should have endpoints */
    private static boolean configureEndpoints(DeployState deployState) {
        if (!deployState.isHosted()) return false;
        if (deployState.getProperties().applicationId().instance().isTester()) return false;
        if (deployState.getProperties().applicationId().tenant().equals(HOSTED_VESPA)) return false;
        return true;
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

    public static class UserConfiguredUrls {

        private final Set<String> urls = new HashSet<>();

        public void add(String url) { urls.add(url); }

        public Set<String> all() { return urls; }

    }

}
