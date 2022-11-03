// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.integration;

import ai.vespa.http.DomainName;
import ai.vespa.http.HttpURL.Path;
import ai.vespa.http.HttpURL.Query;
import com.yahoo.component.AbstractComponent;
import com.yahoo.component.Version;
import com.yahoo.component.annotation.Inject;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.zone.ZoneId;
import com.yahoo.vespa.flags.json.FlagData;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ClusterMetrics;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.DeploymentData;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.EndpointStatus;
import com.yahoo.vespa.hosted.controller.api.application.v4.model.ProtonMetrics;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ApplicationReindexing.Status;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Cluster;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ConfigServer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ContainerEndpoint;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.DeploymentResult;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.LoadBalancer;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.NodeFilter;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ProxyResponse;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.QuotaUsage;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TestReport;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.TesterCloud;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.RestartFilter;
import com.yahoo.vespa.hosted.controller.api.integration.secrets.TenantSecretStore;
import com.yahoo.vespa.hosted.controller.application.SystemApplication;
import com.yahoo.vespa.hosted.controller.application.pkg.ApplicationPackage;
import wiremock.org.checkerframework.checker.units.qual.A;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.slow;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author mortent
 * @author jonmv
 */
public class ConfigServerMock extends AbstractComponent implements ConfigServer {

    private final Map<DeploymentId, Application> applications = new LinkedHashMap<>();
    private final Set<ZoneId> inactiveZones = new HashSet<>();
    private final Map<DeploymentId, EndpointStatus> endpoints = new HashMap<>();
    private final NodeRepositoryMock nodeRepository = new NodeRepositoryMock();
    private final Map<DeploymentId, ServiceConvergence> serviceStatus = new HashMap<>();
    private final Set<ApplicationId> disallowConvergenceCheckApplications = new HashSet<>();
    private final Version initialVersion = new Version(6, 1, 0);
    private final DockerImage initialDockerImage = DockerImage.fromString("registry.example.com/vespa/vespa:6.1.0");
    private final Set<DeploymentId> suspendedApplications = new HashSet<>();
    private final Map<ZoneId, Set<LoadBalancer>> loadBalancers = new HashMap<>();
    private final Set<Environment> deferLoadBalancerProvisioning = new HashSet<>();
    private final Map<DeploymentId, List<DeploymentResult.LogEntry>> warnings = new HashMap<>();
    private final Map<DeploymentId, Set<ContainerEndpoint>> containerEndpoints = new HashMap<>();
    private final Map<DeploymentId, List<ClusterMetrics>> clusterMetrics = new HashMap<>();
    private final Map<DeploymentId, TestReport> testReport = new HashMap<>();
    private final Map<DeploymentId, CloudAccount> cloudAccounts = new HashMap<>();
    private List<ProtonMetrics> protonMetrics;

    private Version lastPrepareVersion = null;
    private Consumer<ApplicationId> prepareException = null;
    private Supplier<String> log = () -> "INFO - All good";

    @Inject
    public ConfigServerMock(ZoneRegistryMock zoneRegistry) {
        bootstrap(zoneRegistry.zones().all().ids(), SystemApplication.notController());
    }

    /** Assigns a reserved tenant node to the given deployment, with initial versions. */
    public void provision(ZoneId zone, ApplicationId application, ClusterSpec.Id clusterId) {
        var current = new ClusterResources(2, 1, new NodeResources(2,  8, 50, 1, slow, remote));
        Cluster cluster = new Cluster(clusterId,
                                      ClusterSpec.Type.container,
                                      new ClusterResources(2, 1, new NodeResources(1,  4, 20, 1, slow, remote)),
                                      new ClusterResources(2, 1, new NodeResources(4, 16, 90, 1, slow, remote)),
                                      current,
                                      Optional.of(new ClusterResources(2, 1, new NodeResources(3, 8, 50, 1, slow, remote))),
                                      Optional.empty(),
                                      new Cluster.Utilization(0.1, 0.2, 0.3, 0.35,
                                                              0.4, 0.5, 0.6, 0.65,
                                                              0.7, 0.8, 0.9, 1.0),
                                      List.of(new Cluster.ScalingEvent(new ClusterResources(0, 0, NodeResources.unspecified()),
                                                                       current,
                                                                       Instant.ofEpochMilli(1234),
                                                                       Optional.of(Instant.ofEpochMilli(2234)))),
                                      "ideal",
                                      "Cluster is ideally scaled",
                                      Duration.ofMinutes(6),
                                      0.7,
                                      0.3);
        nodeRepository.putApplication(zone,
                                      new com.yahoo.vespa.hosted.controller.api.integration.configserver.Application(application,
                                                                                                                     List.of(cluster)));

        Node parent = nodeRepository().list(zone, NodeFilter.all().applications(SystemApplication.tenantHost.id())).stream().findAny()
                                      .orElseThrow(() -> new IllegalStateException("No parent hosts in " + zone));
        nodeRepository().putNodes(zone, Node.builder().hostname(hostFor(application, zone))
                                            .state(Node.State.reserved)
                                            .type(NodeType.tenant)
                                            .owner(application)
                                            .parentHostname(parent.hostname())
                                            .currentVersion(initialVersion)
                                            .wantedVersion(initialVersion)
                                            .currentDockerImage(initialDockerImage)
                                            .wantedDockerImage(initialDockerImage)
                                            .currentOsVersion(Version.emptyVersion)
                                            .wantedOsVersion(Version.emptyVersion)
                                            .resources(new NodeResources(2, 8, 50, 1, slow, remote))
                                            .serviceState(Node.ServiceState.unorchestrated)
                                            .flavor("d-2-8-50")
                                            .clusterId(clusterId.value())
                                            .clusterType(Node.ClusterType.container)
                                            .build());
    }

    public HostName hostFor(ApplicationId application, ZoneId zone) {
        return HostName.of("host-" + application.toFullString() + "-" + zone.value());
    }

    public void bootstrap(List<ZoneId> zones, SystemApplication... applications) {
        bootstrap(zones, List.of(applications));
    }

    public void bootstrap(List<ZoneId> zones, List<SystemApplication> applications) {
        nodeRepository().clear();
        addNodes(zones, applications);
    }

    public void addNodes(List<ZoneId> zones, List<SystemApplication> applications) {
        for (ZoneId zone : zones) {
            for (SystemApplication application : applications) {
                for (int i = 1; i <= 3; i++) {
                    Node node = Node.builder()
                                    .hostname(HostName.of("node-" + i + "-" + application.id().application()
                                                                                         .value() + "-" + zone.value()))
                                    .state(Node.State.active)
                                    .type(application.nodeType())
                                    .owner(application.id())
                                    .currentVersion(initialVersion).wantedVersion(initialVersion)
                                    .currentOsVersion(Version.emptyVersion).wantedOsVersion(Version.emptyVersion)
                                    .build();
                    nodeRepository().putNodes(zone, node);
                }
                convergeServices(application.id(), zone);
            }
        }
    }

    /** Converge all services belonging to the given application */
    public void convergeServices(ApplicationId application, ZoneId zone) {
        List<Node> nodes = nodeRepository.list(zone, NodeFilter.all().applications(application));
        serviceStatus.put(new DeploymentId(application, zone), new ServiceConvergence(application,
                                                                                      zone,
                                                                                      true,
                                                                                      2,
                                                                                      nodes.stream()
                                                                                           .map(node -> new ServiceConvergence.Status(node.hostname(),
                                                                                                                                      43,
                                                                                                                                      "container",
                                                                                                                                      2))
                                                                                           .collect(Collectors.toList())));
    }

    /** The version given in the previous prepare call, or empty if no call has been made */
    public Optional<Version> lastPrepareVersion() {
        return Optional.ofNullable(lastPrepareVersion);
    }

    /** Sets a function that may throw, determined by app id. */
    public void throwOnPrepare(Consumer<ApplicationId> prepareThrower) {
        this.prepareException = prepareThrower;
    }

    /** The exception to throw on the next prepare run, or null to continue normally */
    public void throwOnNextPrepare(RuntimeException prepareException) {
        this.prepareException = prepareException == null ? null : id -> { this.prepareException = null; throw prepareException; };
    }

    /** Set version for an application in a given zone */
    public void setVersion(Version version, ApplicationId application, ZoneId zone) {
        setVersion(zone, nodeRepository.list(zone, NodeFilter.all().applications(application)), version, false);
    }

    /** Set version for nodeCount number of nodes in application in a given zone */
    public void setVersion(Version version, List<Node> nodes, ZoneId zone) {
        setVersion(zone, nodes, version, false);
    }

    /** Set OS version for an application in a given zone */
    public void setOsVersion(Version version, ApplicationId application, ZoneId zone) {
        setVersion(zone, nodeRepository.list(zone, NodeFilter.all().applications(application)), version, true);
    }

    /** Set OS version for an application in a given zone */
    public void setOsVersion(Version version, List<Node> nodes, ZoneId zone) {
        setVersion(zone, nodes, version, true);
    }

    private void setVersion(ZoneId zone, List<Node> nodes, Version version, boolean osVersion) {
        for (var node : nodes) {
            Node newNode;
            if (osVersion) {
                newNode = Node.builder(node).currentOsVersion(version).wantedOsVersion(version).build();
            } else {
                newNode = Node.builder(node).currentVersion(version).wantedVersion(version).build();
            }
            nodeRepository().putNodes(zone, newNode);
        }
    }

    /** The initial version of this config server */
    public Version initialVersion() {
        return initialVersion;
    }

    /** Get deployed application by ID */
    public Optional<Application> application(ApplicationId id, ZoneId zone) {
        return Optional.ofNullable(applications.get(new DeploymentId(id, zone)));
    }

    @Override
    public void setSuspension(DeploymentId deployment, boolean suspend) {
        if (suspend)
            suspendedApplications.add(deployment);
        else
            suspendedApplications.remove(deployment);
    }

    public void generateWarnings(DeploymentId deployment, int count) {
        warnings.put(deployment,
                     IntStream.rangeClosed(1, count)
                              .mapToObj(i -> new DeploymentResult.LogEntry(Instant.now().toEpochMilli(),
                                                                           "log message " + i + " generated by unit test",
                                                                           Level.WARNING,
                                                                           false))
                              .toList());
    }

    public Map<DeploymentId, Set<ContainerEndpoint>> containerEndpoints() {
        return Collections.unmodifiableMap(containerEndpoints);
    }

    public Optional<CloudAccount> cloudAccount(DeploymentId deployment) {
        return Optional.ofNullable(cloudAccounts.get(deployment));
    }

    public Set<String> containerEndpointNames(DeploymentId deployment) {
        return containerEndpoints.getOrDefault(deployment, Set.of()).stream()
                                 .map(ContainerEndpoint::names)
                                 .flatMap(Collection::stream)
                                 .collect(Collectors.toUnmodifiableSet());
    }

    public void setMetrics(DeploymentId deployment, ClusterMetrics clusterMetrics) {
        setMetrics(deployment, List.of(clusterMetrics));
    }

    public void setMetrics(DeploymentId deployment, List<ClusterMetrics> clusterMetrics) {
        this.clusterMetrics.put(deployment, clusterMetrics);
    }

    public void setProtonMetrics(List<ProtonMetrics> protonMetrics) {
        this.protonMetrics = protonMetrics;
    }

    public void deferLoadBalancerProvisioningIn(Set<Environment> environments) {
        deferLoadBalancerProvisioning.addAll(environments);
    }

    @Override
    public NodeRepositoryMock nodeRepository() {
        return nodeRepository;
    }

    @Override
    public Optional<ServiceConvergence> serviceConvergence(DeploymentId deployment, Optional<Version> version) {
        if (disallowConvergenceCheckApplications.contains(deployment.applicationId()))
            throw new IllegalStateException(deployment.applicationId() + " should not ask for service convergence");

        return Optional.ofNullable(serviceStatus.get(deployment));
    }

    public void disallowConvergenceCheck(ApplicationId applicationId) {
        disallowConvergenceCheckApplications.add(applicationId);
    }

    private Set<LoadBalancer> getLoadBalancers(ZoneId zone) {
        return loadBalancers.getOrDefault(zone, new LinkedHashSet<>());
    }

    @Override
    public List<LoadBalancer> getLoadBalancers(ApplicationId application, ZoneId zone) {
        return getLoadBalancers(zone).stream()
                                     .filter(lb -> lb.application().equals(application))
                                     .collect(Collectors.toUnmodifiableList());
    }

    @Override
    public List<FlagData> listFlagData(ZoneId zone) {
        return List.of();
    }

    @Override
    public TesterCloud.Status getTesterStatus(DeploymentId deployment) {
        return TesterCloud.Status.SUCCESS;
    }

    @Override
    public String startTests(DeploymentId deployment, TesterCloud.Suite suite, byte[] config) {
        return "Tests started";
    }

    @Override
    public List<LogEntry> getTesterLog(DeploymentId deployment, long after) {
        return List.of();
    }

    @Override
    public boolean isTesterReady(DeploymentId deployment) {
        return false;
    }

    @Override
    public Optional<TestReport> getTestReport(DeploymentId deployment) {
        return Optional.ofNullable(testReport.get(deployment));
    }
    public void setTestReport(DeploymentId deploymentId, TestReport report) {
        testReport.put(deploymentId, report);
    }

    /** Add any of given loadBalancers that do not already exist to the load balancers in zone */
    public void putLoadBalancers(ZoneId zone, List<LoadBalancer> loadBalancers) {
        this.loadBalancers.putIfAbsent(zone, new LinkedHashSet<>());
        this.loadBalancers.get(zone).addAll(loadBalancers);
    }

    public void removeLoadBalancers(ApplicationId application, ZoneId zone) {
        getLoadBalancers(zone).removeIf(lb -> lb.application().equals(application));
    }

    @Override
    public PreparedApplication deploy(DeploymentData deployment) {
        ApplicationPackage appPackage;
        try (InputStream in = deployment.applicationPackage()) {
            appPackage = new ApplicationPackage(in.readAllBytes());
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        lastPrepareVersion = deployment.platform();
        if (prepareException != null)
            prepareException.accept(ApplicationId.from(deployment.instance().tenant(),
                                                       deployment.instance().application(),
                                                       deployment.instance().instance()));
        DeploymentId id = new DeploymentId(deployment.instance(), deployment.zone());

        applications.put(id, new Application(id.applicationId(), lastPrepareVersion, appPackage));
        ClusterSpec.Id cluster = ClusterSpec.Id.from("default");
        deployment.endpointCertificateMetadata(); // Supplier with side effects >_<

        if (nodeRepository().list(id.zoneId(), NodeFilter.all().applications(id.applicationId())).isEmpty())
            provision(id.zoneId(), id.applicationId(), cluster);

        this.containerEndpoints.put(id, deployment.containerEndpoints());
        deployment.cloudAccount().ifPresent(account -> this.cloudAccounts.put(id, account));

        if (!deferLoadBalancerProvisioning.contains(id.zoneId().environment())) {
            putLoadBalancers(id.zoneId(), List.of(new LoadBalancer(UUID.randomUUID().toString(),
                                                                   id.applicationId(),
                                                                   cluster,
                                                                   Optional.of(HostName.of("lb-0--" + id.applicationId().toFullString() + "--" + id.zoneId().toString())),
                                                                   Optional.empty(),
                                                                   LoadBalancer.State.active,
                                                                   Optional.of("dns-zone-1"))));
        }

        Application application = applications.get(id);
        application.activate();
        List<Node> nodes = nodeRepository.list(id.zoneId(), NodeFilter.all().applications(id.applicationId()));
        for (Node node : nodes) {
            nodeRepository.putNodes(id.zoneId(), Node.builder(node)
                                                     .state(Node.State.active)
                                                     .wantedVersion(application.version().get())
                                                     .build());
        }
        serviceStatus.put(id, new ServiceConvergence(id.applicationId(),
                                                     id.zoneId(),
                                                     false,
                                                     2,
                                                     nodes.stream()
                                                          .map(node -> new ServiceConvergence.Status(node.hostname(),
                                                                                                     43,
                                                                                                     "container",
                                                                                                     1))
                                                          .collect(Collectors.toList())));

        DeploymentResult result = new DeploymentResult("foo", warnings.getOrDefault(id, List.of()));
        return () -> result;
    }

    @Override
    public void reindex(DeploymentId deployment, List<String> clusterNames, List<String> documentTypes, boolean indexedOnly, Double speed) { }

    @Override
    public ApplicationReindexing getReindexing(DeploymentId deployment) {
        return new ApplicationReindexing(true,
                                         Map.of("cluster",
                                                new ApplicationReindexing.Cluster(Map.of("type", 100L),
                                                                                  Map.of("type", new Status(Instant.ofEpochMilli(345),
                                                                                                            Instant.ofEpochMilli(456),
                                                                                                            Instant.ofEpochMilli(567),
                                                                                                            ApplicationReindexing.State.FAILED,
                                                                                                            "(＃｀д´)ﾉ",
                                                                                                            0.1,
                                                                                                            1.0)))));
    }

    @Override
    public void disableReindexing(DeploymentId deployment) { }

    @Override
    public void enableReindexing(DeploymentId deployment) { }

    @Override
    public boolean isSuspended(DeploymentId deployment) {
        return suspendedApplications.contains(deployment);
    }

    @Override
    public void restart(DeploymentId deployment, RestartFilter restartFilter) {
        nodeRepository().requestRestart(deployment, restartFilter.getHostName());
    }

    @Override
    public void deactivate(DeploymentId deployment) {
        ApplicationId applicationId = deployment.applicationId();
        nodeRepository().removeNodes(deployment.zoneId(),
                                     nodeRepository().list(deployment.zoneId(), NodeFilter.all().applications(applicationId)));
        if ( ! applications.containsKey(deployment))
            return;

        applications.remove(deployment);
        serviceStatus.remove(deployment);
        removeLoadBalancers(deployment.applicationId(), deployment.zoneId());
    }

    @Override
    public List<ClusterMetrics> getDeploymentMetrics(DeploymentId deployment) {
        return Collections.unmodifiableList(clusterMetrics.getOrDefault(deployment, List.of()));
    }

    @Override
    public List<ProtonMetrics> getProtonMetrics(DeploymentId deployment) {
        return this.protonMetrics;
    }

    @Override
    public ProxyResponse getServiceNodePage(DeploymentId deployment, String serviceName, DomainName node, Path subPath, Query query) {
        return new ProxyResponse((subPath + " and " + query).getBytes(UTF_8), "text/html", 200);
    }

    @Override
    public ProxyResponse getServiceNodes(DeploymentId deployment) {
        return new ProxyResponse("{\"json\":\"thank you very much\"}".getBytes(UTF_8), "application.json", 200);
    }

    @Override
    public void setGlobalRotationStatus(DeploymentId deployment, List<String> upstreamNames, EndpointStatus status) {
        endpoints.put(deployment, status);
    }

    @Override
    public void setGlobalRotationStatus(ZoneId zone, boolean in) {
        if (in) {
            inactiveZones.remove(zone);
        } else {
            inactiveZones.add(zone);
        }
    }

    @Override
    public EndpointStatus getGlobalRotationStatus(DeploymentId deployment, String upstreamName) {
        EndpointStatus status = new EndpointStatus(EndpointStatus.Status.in, "", Instant.ofEpochSecond(1497618757L));
        return endpoints.getOrDefault(deployment, status);
    }

    @Override
    public boolean getGlobalRotationStatus(ZoneId zone) {
        return !inactiveZones.contains(zone);
    }

    @Override
    public InputStream getLogs(DeploymentId deployment, Map<String, String> queryParameters) {
        return new ByteArrayInputStream(log.get().getBytes(UTF_8));
    }

    @Override
    public ProxyResponse getApplicationPackageContent(DeploymentId deployment, Path path, URI requestUri) {
        return new ProxyResponse(("{\"path\":\"/" + String.join("/", path.segments()) + "\"}").getBytes(UTF_8), "application/json", 200);
    }

    public void setLogStream(Supplier<String> log) {
        this.log = log;
    }

    @Override
    public List<String> getContentClusters(DeploymentId deployment) {
        return Collections.singletonList("music");
    }

    @Override
    public QuotaUsage getQuotaUsage(DeploymentId deploymentId) {
        return new QuotaUsage(42);
    }

    @Override
    public String validateSecretStore(DeploymentId deployment, TenantSecretStore tenantSecretStore, String region, String parameterName) {
        return "{\"settings\":{\"name\":\"foo\",\"role\":\"vespa-secretstore-access\",\"awsId\":\"892075328880\",\"externalId\":\"*****\",\"region\":\"us-east-1\"},\"status\":\"ok\"}";
    }

    public static class Application {

        private final ApplicationId id;
        private final Version version;
        private boolean activated;
        private final ApplicationPackage applicationPackage;

        private Application(ApplicationId id, Version version, ApplicationPackage applicationPackage) {
            this.id = id;
            this.version = version;
            this.applicationPackage = applicationPackage;
        }

        public ApplicationId id() {
            return id;
        }

        public Optional<Version> version() {
            return Optional.ofNullable(version);
        }

        public boolean activated() {
            return activated;
        }

        public ApplicationPackage applicationPackage() {
            return applicationPackage;
        }

        private void activate() {
            this.activated = true;
        }

    }

}
